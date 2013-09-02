package de.blinkt.openvpn.core;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.ViewGroup.LayoutParams;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.infradead.libopenconnect.LibOpenConnect;
import org.jetbrains.annotations.NotNull;

import de.blinkt.openvpn.R;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.OpenVPN.ConnectionStatus;

public class OpenConnectManagementThread implements Runnable, OpenVPNManagement {

	public static Context context;
	private Handler mHandler;
	private VpnProfile mProfile;
	private OpenVpnService mOpenVPNService;
	private SharedPreferences mPrefs;

	LibOpenConnect mOC;
	private boolean mInitDone = false;

    public OpenConnectManagementThread(VpnProfile profile, OpenVpnService openVpnService) {
		mHandler = new Handler();
		mProfile = profile;
		mOpenVPNService = openVpnService;
		mPrefs = context.getSharedPreferences(mProfile.getUUID().toString(), Context.MODE_PRIVATE);
	}

    public boolean openManagementInterface(@NotNull Context c) {
    	return true;
    }

	private abstract class UiTask implements Runnable {
		abstract Object fn();

		private Object result;
		private boolean done = false;
		private Object lock = new Object();

		@Override
		public void run() {
			synchronized (lock) {
				result = fn();
				done = true;
				lock.notifyAll();
			}
		}

		public Object go() {
			mHandler.post(this);
			synchronized (lock) {
				while (!done) {
					try {
						lock.wait();
					} catch (InterruptedException e) {
					}
				}
			}
			return result;
		}
	}

    private String getStringPref(final String key) {
		UiTask r = new UiTask() {
			public Object fn() {
				return mPrefs.getString(key, "");
			}
		};
		return (String)r.go();
    }
    
    private class AuthFormHandler
    	implements DialogInterface.OnClickListener, DialogInterface.OnDismissListener {

    	private Context mContext;
    	private boolean isOK = false;
    	private boolean done = false;
    	private Object lock = new Object();

    	public AuthFormHandler(Context context) {
    		mContext = context;
    	}

		@Override
		public void onDismiss(DialogInterface dialog) {
			// catches OK, Cancel, and Back button presses
			synchronized (lock) {
				done = true;
				lock.notify();
			}
		}

		@Override
		public void onClick(DialogInterface dialog, int which) {
			if (which == DialogInterface.BUTTON_POSITIVE) {
				isOK = true;
			}
		}
		
		private LinearLayout newTextBlank(LibOpenConnect.FormOpt opt, String defval) {
			LinearLayout.LayoutParams fillWidth =
					new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);

			LinearLayout ll = new LinearLayout(mContext);
			ll.setOrientation(LinearLayout.HORIZONTAL);
			ll.setLayoutParams(fillWidth);

			TextView tv = new TextView(mContext);
			tv.setText(opt.label);
			ll.addView(tv);

			tv = new EditText(mContext);
			tv.setLayoutParams(fillWidth);
			if (defval != null) {
				tv.setText(defval);
			}
			if (opt.type == LibOpenConnect.OC_FORM_OPT_PASSWORD) {
				tv.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
				tv.setTransformationMethod(PasswordTransformationMethod.getInstance());
			} else {
				tv.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
			}

			opt.userData = tv;
			ll.addView(tv);
			return ll;
		}

		public boolean executeForm(final LibOpenConnect.AuthForm form) {
			final AuthFormHandler h = this;
			
			UiTask r = new UiTask() {
				public Object fn() {
					LinearLayout v = new LinearLayout(mContext);
					v.setOrientation(LinearLayout.VERTICAL);

					for (LibOpenConnect.FormOpt opt : form.opts) {
						if (opt.type == LibOpenConnect.OC_FORM_OPT_TEXT ||
								opt.type == LibOpenConnect.OC_FORM_OPT_PASSWORD) {
							v.addView(newTextBlank(opt, null));
						}
					}

					new AlertDialog.Builder(mContext)
							.setView(v)
							.setTitle("ocserv login")
							.setPositiveButton("OK", h)
							.setNegativeButton("Cancel", h)
							.setOnDismissListener(h)
							.show();
					return true;
				}
			};
			r.go();
			synchronized (lock) {
				while (!done) {
					try {
						lock.wait();
					} catch (InterruptedException e) {
					}
				}
			}
			
			for (LibOpenConnect.FormOpt opt : form.opts) {
				if (opt.type == LibOpenConnect.OC_FORM_OPT_TEXT ||
						opt.type == LibOpenConnect.OC_FORM_OPT_PASSWORD) {
					TextView tv = (TextView)opt.userData;
					opt.setValue(tv.getText().toString());
				}
			}

			return isOK;
		}
    }

	private class AndroidOC extends LibOpenConnect {
		public int onValidatePeerCert(String msg) {
			OpenVPN.logMessage(0, "", "CALLBACK: onValidatePeerCert");
			return 0;
		}

		public int onWriteNewConfig(byte[] buf) {
			OpenVPN.logMessage(0, "", "CALLBACK: onWriteNewConfig");
			return 0;
		}

		public int onProcessAuthForm(LibOpenConnect.AuthForm authForm) {
			OpenVPN.logMessage(0, "", "CALLBACK: onProcessAuthForm");
			if (new AuthFormHandler(context).executeForm(authForm)) {
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
