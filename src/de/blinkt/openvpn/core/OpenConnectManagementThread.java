package de.blinkt.openvpn.core;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;

import org.infradead.libopenconnect.LibOpenConnect;
import org.jetbrains.annotations.NotNull;

import com.stericson.RootTools.RootTools;

import de.blinkt.openvpn.AuthFormHandler;
import de.blinkt.openvpn.R;
import de.blinkt.openvpn.VpnProfile;

public class OpenConnectManagementThread implements Runnable, OpenVPNManagement {

	public static final String TAG = "OpenConnect";

	public static final int STATE_AUTHENTICATING = 1;
	public static final int STATE_USER_PROMPT = 2;
	public static final int STATE_AUTHENTICATED = 3;
	public static final int STATE_CONNECTING = 4;
	public static final int STATE_CONNECTED = 5;
	public static final int STATE_DISCONNECTED = 6;

	public static Context mContext;
	private VpnProfile mProfile;
	private OpenVpnService mOpenVPNService;
	private SharedPreferences mPrefs;
	private String mFilesDir;
	private String mCacheDir;
	private String mServerAddr;

	LibOpenConnect mOC;
	private boolean mInitDone = false;

    public OpenConnectManagementThread(Context context, VpnProfile profile, OpenVpnService openVpnService) {
    	mContext = context;
		mProfile = profile;
		mOpenVPNService = openVpnService;
		mPrefs = mContext.getSharedPreferences(mProfile.getUUID().toString(),
				Context.MODE_PRIVATE);
	}

    public boolean openManagementInterface(@NotNull Context c) {
    	return true;
    }

    private String getStringPref(final String key) {
    	return mPrefs.getString(key, "");
    }

    private boolean getBoolPref(final String key) {
    	return mPrefs.getBoolean(key, false);
    }

    private void log(String msg) {
    	OpenVPN.logMessage(0, "", msg);
    }

	private class AndroidOC extends LibOpenConnect {
		public int onValidatePeerCert(String reason) {
			log("CALLBACK: onValidatePeerCert");

			Boolean response = (Boolean)mOpenVPNService.promptUser(
					new CertWarningDialog(mPrefs, reason, getHostname(), getCertSHA1()));

			if (response) {
				return 0;
			} else {
				log("AUTH: user rejected bad certificate");
				return -1;
			}
		}

		public int onWriteNewConfig(byte[] buf) {
			log("CALLBACK: onWriteNewConfig");
			return 0;
		}

		public int onProcessAuthForm(LibOpenConnect.AuthForm authForm) {
			log("CALLBACK: onProcessAuthForm");
			setState(STATE_USER_PROMPT);

			Boolean response = (Boolean)mOpenVPNService.promptUser(
					new AuthFormHandler(mPrefs, authForm));

			if (response) {
				setState(STATE_AUTHENTICATING);
				return AUTH_FORM_PARSED;
			} else {
				log("AUTH: user aborted");
				return AUTH_FORM_CANCELLED;
			}
		}

		public void onProgress(int level, String msg) {
			log("PROGRESS: " + msg.trim());
		}

		public void onProtectSocket(int fd) {
			if (mOpenVPNService.protect(fd) != true) {
				log("Error protecting fd " + fd);
			}
		}
	}

	private synchronized void initNative() {
		if (!mInitDone) {
			System.loadLibrary("openconnect");
		}
	}

	@Override
	public void run() {
		if (!runVPN()) {
			log("VPN terminated with errors");
		}
		setState(STATE_DISCONNECTED);
	}

	private void setState(int state) {
		log("New state: " + state);
	}

	private String prefToTempFile(String prefName, boolean isExecutable) throws IOException {
		String prefData = getStringPref(prefName);
		String path;

		if (prefData.equals("")) {
			return null;
		}
		if (prefData.startsWith("[[INLINE]]")) {
			prefData = prefData.substring(10);

			// It would be nice to use mCacheDir here, but putting curl and the CSD script in the same
			// directory simplifies things.
			path = mFilesDir + File.separator + prefName + ".tmp";
			Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path), "utf-8"));
			writer.write(prefData);
			writer.close();

			log("PREF: wrote out " + path);
		} else {
			path = prefData;
			log("PREF: using existing file " + path);
		}

		if (isExecutable) {
			File f = new File(path);
			if (!f.exists()) {
				log("PREF: file does not exist");
				return null;
			}
			if (!f.setExecutable(true)) {
				throw new IOException();
			}
		}
		return path;
	}

	private boolean setPreferences() {
		String s;

		try {
			s = prefToTempFile("custom_csd_wrapper", true);
			mOC.setCSDWrapper(s != null ? s : (mFilesDir + File.separator + "android_csd.sh"), mCacheDir);

			s = prefToTempFile("ca_certificate", false);
			if (s != null) {
				mOC.setCAFile(s);
			}

			s = prefToTempFile("user_certificate", false);
			String key = prefToTempFile("private_key", false);
			if (s != null) {
				if (key == null) {
					// assume the file contains the cert + key
					mOC.setClientCert(s, s);
				} else {
					mOC.setClientCert(s, key);
				}
			}
		} catch (IOException e) {
			log("Error writing temporary file");
			return false;
		}

		mServerAddr = getStringPref("server_address");
		mOC.setXMLPost(!getBoolPref("disable_xml_post"));
		mOC.setReportedOS(getStringPref("reported_os"));

		s = getStringPref("software_token");
		String token = getStringPref("token_string");
		int ret = 0;

		if (s.equals("securid")) {
			ret = mOC.setTokenMode(LibOpenConnect.OC_TOKEN_MODE_STOKEN, token);
		} else if (s.equals("totp")) {
			ret = mOC.setTokenMode(LibOpenConnect.OC_TOKEN_MODE_TOTP, token);
		}
		if (ret < 0) {
			log("Error " + ret + " setting token string");
			return false;
		}

		return true;
	}

	private boolean runVPN() {
		initNative();

		RootTools.installBinary(mContext, R.raw.android_csd, "android_csd.sh", "0755");
		RootTools.installBinary(mContext, R.raw.curl, "curl", "0755");

		setState(STATE_CONNECTING);
		mOC = new AndroidOC();

		mFilesDir = mContext.getFilesDir().getPath();
		mCacheDir = mContext.getCacheDir().getPath();

		if (setPreferences() == false)
			return false;

		if (mOC.parseURL(mServerAddr) != 0) {
			log("Error parsing server address");
			return false;
		}
		if (mOC.obtainCookie() != 0) {
			log("Error obtaining cookie");
			return false;
		}
		setState(STATE_AUTHENTICATED);
		if (mOC.makeCSTPConnection() != 0) {
			log("Error establishing CSTP connection");
			return false;
		}

		LibOpenConnect.IPInfo ip = mOC.getIPInfo();
		mOpenVPNService.setLocalIP(ip.addr, ip.netmask, ip.MTU, "");

		for (String s : ip.DNS) {
			mOpenVPNService.addDNS(s);
		}

		if (ip.splitIncludes.isEmpty()) {
			mOpenVPNService.addRoute("0.0.0.0", "0.0.0.0");
		} else {
			for (String s : ip.splitIncludes) {
				String ss[] = s.split("/");
				mOpenVPNService.addRoute(ss[0], ss[1]);
			}
			for (String s : ip.DNS) {
				mOpenVPNService.addRoute(s, "255.255.255.255");
			}
		}

		ParcelFileDescriptor pfd = mOpenVPNService.openTun();
		if (pfd == null || mOC.setupTunFD(pfd.getFd()) != 0) {
			log("Error setting up tunnel fd");
			return false;
		}
		setState(STATE_CONNECTED);

		mOC.setupDTLS(60);
		mOC.mainloop(300, LibOpenConnect.RECONNECT_INTERVAL_MIN);

		return true;
	}

	public void reconnect() {
		log("RECONNECT");
	}

	@Override
	public void pause (pauseReason reason) {
		log("PAUSE");
	}

	@Override
	public void resume() {
		log("RESUME");
	}

	@Override
	public boolean stopVPN() {
		log("STOP");
		mOC.cancel();
		return true;
	}
}
