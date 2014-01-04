package app.openconnect.core;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashMap;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;

import org.infradead.libopenconnect.LibOpenConnect;

import app.openconnect.AuthFormHandler;
import app.openconnect.VpnProfile;

public class OpenConnectManagementThread implements Runnable, OpenVPNManagement {

	public static final String TAG = "OpenConnect";

	// Keep these in sync with the "connection_states" string array
	public static final int STATE_AUTHENTICATING = 1;
	public static final int STATE_USER_PROMPT = 2;
	public static final int STATE_AUTHENTICATED = 3;
	public static final int STATE_CONNECTING = 4;
	public static final int STATE_CONNECTED = 5;
	public static final int STATE_DISCONNECTED = 6;

	private Context mContext;
	private VpnProfile mProfile;
	private OpenVpnService mOpenVPNService;
	private SharedPreferences mPrefs;
	private SharedPreferences mAppPrefs;
	private String mFilesDir;
	private String mCacheDir;
	private String mServerAddr;

	private LibOpenConnect mOC;
	private boolean mAuthgroupSet = false;
	private String mLastFormDigest;
	private HashMap<String,Boolean> mAcceptedCerts = new HashMap<String,Boolean>();
	private boolean mAuthDone = false;

	private boolean mRequestPause;
	private boolean mRequestDisconnect;
	private Object mMainloopLock = new Object();

    public OpenConnectManagementThread(Context context, VpnProfile profile, OpenVpnService openVpnService) {
    	mContext = context;
		mProfile = profile;
		mOpenVPNService = openVpnService;
		mPrefs = mProfile.mPrefs;
		mAppPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
	}

    private String getStringPref(final String key) {
    	return mPrefs.getString(key, "");
    }

    private boolean getBoolPref(final String key) {
    	return mPrefs.getBoolean(key, false);
    }

    private void log(String msg) {
    	mOpenVPNService.log(VPNLog.LEVEL_INFO, msg);
    }

	private class AndroidOC extends LibOpenConnect {
		public int onValidatePeerCert(String reason) {
			log("CALLBACK: onValidatePeerCert");

			// This can be called repeatedly on the same (re)connection attempt
			String hash = getCertSHA1();
			if (mAcceptedCerts.containsKey(hash)) {
				return 0;
			}
			if (mAuthDone) {
				log("AUTH: certificate mismatch on existing connection");
				return -1;
			}

			Boolean response = (Boolean)mOpenVPNService.promptUser(
					new CertWarningDialog(mPrefs, getHostname(), hash, reason));

			if (response) {
				mAcceptedCerts.put(hash, true);
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

			AuthFormHandler h = new AuthFormHandler(mPrefs, authForm, mAuthgroupSet, mLastFormDigest);

			Integer response = (Integer)mOpenVPNService.promptUser(h);
			if (response == OC_FORM_RESULT_OK) {
				setState(STATE_AUTHENTICATING);
				mLastFormDigest = h.getFormDigest();
			} else if (response == OC_FORM_RESULT_NEWGROUP) {
				log("AUTH: requesting authgroup change " +
						(mAuthgroupSet ? "(interactive)" : "(non-interactive)"));
				mAuthgroupSet = true;
			} else {
				log("AUTH: form result is " + response);
			}
			return response;
		}

		public void onProgress(int level, String msg) {
			mOpenVPNService.log(level, "LIB: " + msg.trim());
		}

		public void onProtectSocket(int fd) {
			if (mOpenVPNService.protect(fd) != true) {
				log("Error protecting fd " + fd);
			}
		}

		public void onStatsUpdate(LibOpenConnect.VPNStats stats) {
			mOpenVPNService.setStats(stats);
		}
	}

	@Override
	public void run() {
		if (!runVPN()) {
			log("VPN terminated with errors");
		}
		setState(STATE_DISCONNECTED);

		synchronized (mMainloopLock) {
			mOC.destroy();
			mOC = null;
		}
		UserDialog.clearDeferredPrefs();

		mOpenVPNService.threadDone();
	}

	private synchronized void setState(int state) {
		mOpenVPNService.setConnectionState(state);
	}

	private String prefToTempFile(String prefName, boolean isExecutable) throws IOException {
		String prefData = getStringPref(prefName);
		String path;

		if (prefData.equals("")) {
			return null;
		}
		if (prefData.startsWith("[[INLINE]]")) {
			prefData = prefData.substring(10);

			path = mCacheDir + File.separator + prefName + ".tmp";
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
			String PATH = System.getenv("PATH");
			if (!PATH.startsWith(mFilesDir)) {
				PATH = mFilesDir + ":" + PATH;
			}
			s = prefToTempFile("custom_csd_wrapper", true);
			mOC.setCSDWrapper(s != null ? s : (mFilesDir + File.separator + "android_csd.sh"), mCacheDir, PATH);

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

		if (mAppPrefs.getBoolean("trace_log", false)) {
			mOC.setLogLevel(LibOpenConnect.PRG_TRACE);
		}

		return true;
	}

	private void setIPInfo(VpnService.Builder b) {
		LibOpenConnect.IPInfo ip = mOC.getIPInfo();
		CIDRIP cdr;

		/* IP + MTU */

		if (ip.addr != null && ip.netmask != null) {
			cdr = new CIDRIP(ip.addr, ip.netmask);
			b.addAddress(cdr.mIp, cdr.len);
			log("IPv4: " + cdr.mIp + "/" + cdr.len);
		}
		if (ip.addr6 != null && ip.netmask6 != null) {
			int netmask = Integer.parseInt(ip.netmask6);
			b.addAddress(ip.addr6, netmask);
			log("IPv6: " + ip.addr6 + "/" + netmask);
		}
		b.setMtu(ip.MTU);
		log("MTU: " + ip.MTU);

		/* routing */

		if (ip.splitIncludes.isEmpty()) {
			b.addRoute("0.0.0.0", 0);
			log("ROUTE: 0.0.0.0/0");
		} else {
			for (String s : ip.splitIncludes) {
				String ss[] = s.split("/");
				cdr = new CIDRIP(ss[0], ss[1]);
				b.addRoute(cdr.mIp, cdr.len);
				log("ROUTE: " + cdr.mIp + "/" + cdr.len);
			}
			for (String s : ip.DNS) {
				b.addRoute(s, 32);
			}
		}

		/* DNS */

		for (String s : ip.DNS) {
			b.addDnsServer(s);
			log("DNS: " + s);
		}
		if (ip.domain != null) {
			b.addSearchDomain(ip.domain);
			log("DOMAIN: " + ip.domain);
		}

		mOpenVPNService.setIPInfo(ip);
	}

	private boolean runVPN() {
		AssetExtractor.extractAll(mContext);

		setState(STATE_CONNECTING);
		synchronized (mMainloopLock) {
			mOC = new AndroidOC();
		}

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

		mAuthDone = true;
		UserDialog.writeDeferredPrefs();
		setState(STATE_AUTHENTICATED);
		if (mOC.makeCSTPConnection() != 0) {
			log("Error establishing CSTP connection");
			return false;
		}

		VpnService.Builder b = mOpenVPNService.getVpnServiceBuilder();
		setIPInfo(b);

		ParcelFileDescriptor pfd = b.establish();
		if (pfd == null || mOC.setupTunFD(pfd.getFd()) != 0) {
			log("Error setting up tunnel fd");
			return false;
		}
		setState(STATE_CONNECTED);

		mOC.setupDTLS(60);

		while (true) {
			int ret = mOC.mainloop(300, LibOpenConnect.RECONNECT_INTERVAL_MIN);
			if (ret == LibOpenConnect.MAINLOOP_DONE) {
				break;
			}
			synchronized (mMainloopLock) {
				if (mRequestDisconnect) {
					// let the library send the BYE packet and wrap up
					continue;
				}
				while (mRequestPause) {
					try {
						mMainloopLock.wait();
					} catch (InterruptedException e) {
					}
				}
			}
		}

		try {
			pfd.close();
		} catch (IOException e) {
		}
		return true;
	}

	public void reconnect() {
		log("RECONNECT");
		// if mRequestPause is false, this will drop the connection and immediately
		// restart the mainloop
		synchronized (mMainloopLock) {
			if (mOC != null) {
				mOC.pause();
			}
		}
	}

	@Override
	public void pause () {
		log("PAUSE");
		synchronized (mMainloopLock) {
			if (!mRequestPause && !mRequestDisconnect && mOC != null) {
				mRequestPause = true;
				mOC.pause();
			}
		}
	}

	@Override
	public void resume() {
		log("RESUME");
		synchronized (mMainloopLock) {
			if (mRequestPause) {
				mRequestPause = false;
				mMainloopLock.notify();
			}
		}
	}

	@Override
	public boolean stopVPN() {
		log("STOP");
		synchronized (mMainloopLock) {
			if (mRequestDisconnect || mOC == null) {
				return true;
			}
			mRequestDisconnect = true;
			mRequestPause = false;
			mOC.cancel();
			mMainloopLock.notify();
		}
		return true;
	}

	public void requestStats() {
		if (mOC != null) {
			mOC.requestStats();
		}
	}
}
