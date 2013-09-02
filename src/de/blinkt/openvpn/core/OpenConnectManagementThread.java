package de.blinkt.openvpn.core;

import java.security.MessageDigest;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.infradead.libopenconnect.LibOpenConnect;
import org.jetbrains.annotations.NotNull;

import de.blinkt.openvpn.R;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.OpenVPN.ConnectionStatus;

public class OpenConnectManagementThread implements Runnable, OpenVPNManagement {

	public static final String TAG = "OpenConnect";

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

    private void setStringPref(final String key, final String value) {
		UiTask r = new UiTask() {
			public Object fn() {
				mPrefs.edit().putString(key, value).commit();
				return null;
			}
		};
		r.go();
    }

    private boolean getBooleanPref(final String key) {
		UiTask r = new UiTask() {
			public Object fn() {
				return mPrefs.getBoolean(key, false);
			}
		};
		return (Boolean)r.go();
    }

    private class AuthFormHandler
    	implements DialogInterface.OnClickListener, DialogInterface.OnDismissListener {

    	private Context mContext;
    	LibOpenConnect.AuthForm mForm;

    	private boolean isOK = false;
    	private boolean done = false;
    	private Object lock = new Object();

    	private CheckBox savePassword = null;
    	private boolean noSave = false;
    	private String formPfx;
    	private int batchMode = BATCH_MODE_DISABLED;

    	private static final int BATCH_MODE_DISABLED = 0;
    	private static final int BATCH_MODE_EMPTY_ONLY = 1;
    	private static final int BATCH_MODE_ENABLED = 2;

    	public AuthFormHandler(Context context) {
    		mContext = context;
    	}

		@Override
		public void onDismiss(DialogInterface dialog) {
			// catches OK, Cancel, and Back button presses
			saveAndStore();
		}

		@Override
		public void onClick(DialogInterface dialog, int which) {
			if (which == DialogInterface.BUTTON_POSITIVE) {
				isOK = true;
			}
		}

		private void setStringPref(String key, String value) {
			mPrefs.edit().putString(key, value).commit();
		}

		private String getStringPref(String key) {
			return mPrefs.getString(key, "");
		}

		private boolean getBooleanPref(String key) {
			return mPrefs.getBoolean(key, false);
		}

		private String digest(String s) {
			String out = "";
			if (s == null) {
				s = "";
			}
			try {
				MessageDigest digest = MessageDigest.getInstance("MD5");
				StringBuilder sb = new StringBuilder();
				byte d[] = digest.digest(s.getBytes("UTF-8"));
				for (byte dd : d) {
					sb.append(String.format("%02x", dd));
				}
				out = sb.toString();
			} catch (Exception e) {
				Log.e(TAG, "MessageDigest failed", e);
			}
			return out;
		}

		private String getOptDigest(LibOpenConnect.FormOpt opt) {
			StringBuilder in = new StringBuilder();

			switch (opt.type) {
			case LibOpenConnect.OC_FORM_OPT_SELECT:
				for (LibOpenConnect.FormChoice ch : opt.choices) {
					in.append(digest(ch.name));
					in.append(digest(ch.label));
				}
				/* falls through */
			case LibOpenConnect.OC_FORM_OPT_TEXT:
			case LibOpenConnect.OC_FORM_OPT_PASSWORD:
				in.append(":" + Integer.toString(opt.type) + ":");
				in.append(digest(opt.name));
				in.append(digest(opt.label));
			}
			return digest(in.toString());
		}

		private String getFormPrefix(LibOpenConnect.AuthForm form) {
			StringBuilder in = new StringBuilder();

			for (LibOpenConnect.FormOpt opt : form.opts) {
				in.append(getOptDigest(opt));
			}
			return "FORMDATA-" + digest(in.toString()) + "-";
		}

		private void fixPadding(View v) {
			v.setPadding(20, 20, 20, 20);
		}

		private LinearLayout newTextBlank(LibOpenConnect.FormOpt opt, String defval) {
			LinearLayout.LayoutParams fillWidth =
					new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);

			LinearLayout ll = new LinearLayout(mContext);
			ll.setOrientation(LinearLayout.HORIZONTAL);
			ll.setLayoutParams(fillWidth);
			fixPadding(ll);

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

		private CheckBox newSavePasswordView(boolean isChecked) {
			CheckBox cb = new CheckBox(mContext);
			cb.setText(R.string.save_password);
			cb.setChecked(isChecked);
			fixPadding(cb);
			return cb;
		}

		private void saveAndStore() {
			for (LibOpenConnect.FormOpt opt : mForm.opts) {
				switch (opt.type) {
				case LibOpenConnect.OC_FORM_OPT_TEXT: {
					TextView tv = (TextView)opt.userData;
					String s = tv.getText().toString();
					if (!noSave) {
						setStringPref(formPfx + getOptDigest(opt), s);
					}
					opt.setValue(s);
					break;
				}
				case LibOpenConnect.OC_FORM_OPT_PASSWORD: {
					TextView tv = (TextView)opt.userData;
					String s = tv.getText().toString();
					if (savePassword != null) {
						boolean checked = savePassword.isChecked();
						setStringPref(formPfx + getOptDigest(opt), checked ? s : "");
						setStringPref(formPfx + "savePass", checked ? "true" : "false");
					}
					opt.setValue(s);
					break;
				}
				case LibOpenConnect.OC_FORM_OPT_SELECT:
					// XXX
				}
			}
			synchronized (lock) {
				done = true;
				lock.notify();
			}
		}

		public boolean executeForm(final LibOpenConnect.AuthForm form) {
			final AuthFormHandler h = this;

			mForm = form;
			formPfx = getFormPrefix(form);
			noSave = getBooleanPref("disable_username_caching");

			String s = getStringPref("batch_mode");
			if (s.equals("empty_only")) {
				batchMode = BATCH_MODE_EMPTY_ONLY;
			} else if (s.equals("enabled")) {
				batchMode = BATCH_MODE_ENABLED;
			}

			UiTask r = new UiTask() {
				public Object fn() {
					LinearLayout v = new LinearLayout(mContext);
					v.setOrientation(LinearLayout.VERTICAL);

					boolean hasPassword = false, allFilled = true;
					for (LibOpenConnect.FormOpt opt : form.opts) {
						switch (opt.type) {
						case LibOpenConnect.OC_FORM_OPT_PASSWORD:
							hasPassword = true;
							/* falls through */
						case LibOpenConnect.OC_FORM_OPT_TEXT:
							String defval = noSave ? "" : getStringPref(formPfx + getOptDigest(opt));
							if (defval.equals("")) {
								allFilled = false;
							}
							v.addView(newTextBlank(opt, defval));
						}
					}
					if (hasPassword && !noSave) {
						boolean savePass = !getStringPref(formPfx + "savePass").equals("false");
						savePassword = newSavePasswordView(savePass);
						v.addView(savePassword);
					}

					android.util.Log.i("OpenConnect", "batchMode " + batchMode + ", allFilled " + allFilled);
					if ((batchMode == BATCH_MODE_EMPTY_ONLY && allFilled) ||
						batchMode == BATCH_MODE_ENABLED) {
						saveAndStore();
						isOK = true;
						return false;
					}

					/* FIXME: this needs to be rerendered on e.g. screen rotation events */
					new AlertDialog.Builder(mContext)
							.setView(v)
							.setTitle(mContext.getString(R.string.login_title, getStringPref("profile_name")))
							.setPositiveButton(R.string.ok, h)
							.setNegativeButton(R.string.cancel, h)
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
