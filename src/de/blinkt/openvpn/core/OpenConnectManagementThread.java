package de.blinkt.openvpn.core;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;

import org.infradead.libopenconnect.LibOpenConnect;
import org.jetbrains.annotations.NotNull;

import com.stericson.RootTools.RootTools;

import de.blinkt.openvpn.AuthFormHandler;
import de.blinkt.openvpn.R;
import de.blinkt.openvpn.UiTask;
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

    public OpenConnectManagementThread(VpnProfile profile, OpenVpnService openVpnService) {
		mProfile = profile;
		mOpenVPNService = openVpnService;
		mPrefs = mContext.getApplicationContext().getSharedPreferences(mProfile.getUUID().toString(),
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

    private class CertWarningHandler extends UiTask
		implements DialogInterface.OnClickListener, DialogInterface.OnDismissListener {

    	public String hostname;
    	public String certSHA1;
    	public String reason;

    	private boolean isOK = false;

    	public CertWarningHandler(Context context, SharedPreferences prefs) {
    		super(context, prefs);
    	}

		@Override
		public Object fn(Object arg) {
			String goodSHA1 = getStringPref("accepted_cert_sha1");
			if (certSHA1.equals(goodSHA1)) {
				return true;
			}

			holdoff();
			AlertDialog alert = new AlertDialog.Builder(mContext)
				.setTitle(R.string.cert_warning_title)
				.setMessage(mContext.getString(R.string.cert_warning_message,
						hostname, reason, certSHA1))
				.setPositiveButton(R.string.cert_warning_always_connect, this)
				.setNeutralButton(R.string.cert_warning_just_once, this)
				.setNegativeButton(R.string.no, this)
				.create();
			alert.setOnDismissListener(this);
			alert.show();
			return null;
		}

		@Override
		public void onDismiss(DialogInterface dialog) {
			// catches Pos/Neg/Neutral and Back button presses
			complete(isOK);
		}

		@Override
		public void onClick(DialogInterface dialog, int which) {
			if (which == DialogInterface.BUTTON_POSITIVE) {
				isOK = true;
				setStringPref("accepted_cert_sha1", certSHA1);
			} else if (which == DialogInterface.BUTTON_NEUTRAL) {
				isOK = true;
			}
		}
    }

	private class AndroidOC extends LibOpenConnect {
		public int onValidatePeerCert(String reason) {
			log("CALLBACK: onValidatePeerCert");

			CertWarningHandler h = new CertWarningHandler(mContext, mPrefs);
			h.reason = reason;
			h.hostname = getHostname();
			h.certSHA1 = getCertSHA1();

			if ((Boolean)h.go(null)) {
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
			if ((Boolean)new AuthFormHandler(mContext, mPrefs).go(authForm)) {
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
