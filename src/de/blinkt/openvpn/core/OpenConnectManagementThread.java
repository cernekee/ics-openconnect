package de.blinkt.openvpn.core;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;

import org.infradead.libopenconnect.LibOpenConnect;
import org.jetbrains.annotations.NotNull;

import de.blinkt.openvpn.AuthFormHandler;
import de.blinkt.openvpn.R;
import de.blinkt.openvpn.UiTask;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.OpenVPN.ConnectionStatus;

public class OpenConnectManagementThread implements Runnable, OpenVPNManagement {

	public static final String TAG = "OpenConnect";

	public static Context mContext;
	private VpnProfile mProfile;
	private OpenVpnService mOpenVPNService;
	private SharedPreferences mPrefs;

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
		UiTask r = new UiTask(mContext, mPrefs) {
			public Object fn(Object arg) {
				return getStringPref(key);
			}
		};
		return (String)r.go(null);
    }

    @SuppressWarnings("unused")
	private void setStringPref(final String key, final String value) {
		UiTask r = new UiTask(mContext, mPrefs) {
			public Object fn(Object arg) {
				setStringPref(key, value);
				return null;
			}
		};
		r.go(null);
    }

    @SuppressWarnings("unused")
	private boolean getBooleanPref(final String key) {
		UiTask r = new UiTask(mContext, mPrefs) {
			public Object fn(Object arg) {
				return getBooleanPref(key);
			}
		};
		return (Boolean)r.go(null);
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
			new AlertDialog.Builder(mContext)
				.setTitle(R.string.cert_warning_title)
				.setMessage(mContext.getString(R.string.cert_warning_message,
						hostname, reason, certSHA1))
				.setPositiveButton(R.string.cert_warning_always_connect, this)
				.setNeutralButton(R.string.cert_warning_just_once, this)
				.setNegativeButton(R.string.no, this)
				.setOnDismissListener(this)
				.show();
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
			OpenVPN.logMessage(0, "", "CALLBACK: onValidatePeerCert");

			CertWarningHandler h = new CertWarningHandler(mContext, mPrefs);
			h.reason = reason;
			h.hostname = getHostname();
			h.certSHA1 = getCertSHA1();

			if ((Boolean)h.go(null)) {
				return 0;
			} else {
				OpenVPN.logMessage(0, "", "AUTH: user rejected bad certificate");
				return -1;
			}
		}

		public int onWriteNewConfig(byte[] buf) {
			OpenVPN.logMessage(0, "", "CALLBACK: onWriteNewConfig");
			return 0;
		}

		public int onProcessAuthForm(LibOpenConnect.AuthForm authForm) {
			OpenVPN.logMessage(0, "", "CALLBACK: onProcessAuthForm");
			if ((Boolean)new AuthFormHandler(mContext, mPrefs).go(authForm)) {
				return AUTH_FORM_PARSED;
			} else {
				OpenVPN.logMessage(0, "", "AUTH: user aborted");
				return AUTH_FORM_CANCELLED;
			}
		}

		public void onProgress(int level, String msg) {
			OpenVPN.logMessage(0, "", "PROGRESS: " + msg.trim());
		}

		public void onProtectSocket(int fd) {
			if (mOpenVPNService.protect(fd) != true) {
				OpenVPN.logMessage(0, "", "Error protecting fd " + fd);
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
		initNative();

		mOC = new AndroidOC();

		mOpenVPNService.updateState("USER_VPN_PASSWORD", "", R.string.state_user_vpn_password,
				ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT);

		if (mOC.parseURL(getStringPref("server_address")) != 0 ||
			mOC.obtainCookie() != 0 ||
			mOC.makeCSTPConnection() != 0) {

			mOpenVPNService.updateState("AUTH_FAILED", "",
					R.string.state_auth_failed, ConnectionStatus.LEVEL_AUTH_FAILED);
			return;
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
			mOpenVPNService.updateState("NOPROCESS", "",
					R.string.state_noprocess, ConnectionStatus.LEVEL_NOTCONNECTED);
			return;
		}

		mOpenVPNService.updateState("CONNECTED", "",
				R.string.state_connected, ConnectionStatus.LEVEL_CONNECTED);

		mOC.setupDTLS(60);
		mOC.mainloop(300, LibOpenConnect.RECONNECT_INTERVAL_MIN);

		mOpenVPNService.updateState("NOPROCESS", "",
				R.string.state_noprocess, ConnectionStatus.LEVEL_NOTCONNECTED);
	}

	public void reconnect() {
		OpenVPN.logMessage(0, "", "RECONNECT");
	}

	@Override
	public void pause (pauseReason reason) {
		OpenVPN.logMessage(0, "", "PAUSE");
	}

	@Override
	public void resume() {
		OpenVPN.logMessage(0, "", "RESUME");
	}

	@Override
	public boolean stopVPN() {
		OpenVPN.logMessage(0, "", "STOP");
		mOC.cancel();
		return true;
	}
}
