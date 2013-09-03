package de.blinkt.openvpn;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

public abstract class UiTask implements Runnable {
	public abstract Object fn(Object arg);

	protected Context mContext;
	protected SharedPreferences mPrefs;

	private Object arg;
	private Object result;
	private boolean done = false;
	private boolean completeOnReturn = true;
	private Object lock = new Object();

	public UiTask(Context context, SharedPreferences prefs) {
		mContext = context;
		mPrefs = prefs;
	}

	protected void setStringPref(String key, String value) {
		mPrefs.edit().putString(key, value).commit();
	}

	protected String getStringPref(String key) {
		return mPrefs.getString(key, "");
	}

	protected void setBooleanPref(String key, boolean value) {
		mPrefs.edit().putBoolean(key, value).commit();
	}

	protected boolean getBooleanPref(String key) {
		return mPrefs.getBoolean(key, false);
	}

	@Override
	public void run() {
		synchronized (lock) {
			Object localResult = fn(arg);
			if (completeOnReturn) {
				complete(localResult);
			}
		}
	}

	protected void holdoff() {
		completeOnReturn = false;
	}

	protected void complete(Object result) {
		synchronized (lock) {
			done = true;
			this.result = result;
			lock.notifyAll();
		}
	}

	public Object go(Object arg) {
		Handler h = new Handler(Looper.getMainLooper());

		this.arg = arg;
		h.post(this);
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