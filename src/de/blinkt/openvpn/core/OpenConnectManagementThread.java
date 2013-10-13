package de.blinkt.openvpn.core;

import java.io.File;

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

	private boolean runVPN() {
		initNative();

		RootTools.installBinary(mContext, R.raw.android_csd, "android_csd.sh", "0755");
		RootTools.installBinary(mContext, R.raw.curl, "curl", "0755");

		setState(STATE_CONNECTING);
		mOC = new AndroidOC();

		mFilesDir = mContext.getFilesDir().getPath();
		mOC.setCSDWrapper(mFilesDir + File.separator + "android_csd.sh", mFilesDir);
		if (mOC.parseURL(getStringPref("server_address")) != 0) {
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
