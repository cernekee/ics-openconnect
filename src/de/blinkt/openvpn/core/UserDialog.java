package de.blinkt.openvpn.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public abstract class UserDialog {
	public static final String TAG = "OpenConnect";

	private Object mResult;
	private boolean mDialogUp;
	protected SharedPreferences mPrefs;

	public UserDialog(SharedPreferences prefs) {
		mPrefs = prefs;
	}

	public Object waitForResponse() {
		while (mResult == null) {
			synchronized (this) {
				try {
					this.wait();
				} catch (InterruptedException e) {
				}
			}
		}
		return mResult;
	}

	protected void finish(Object result) {
		synchronized (this) {
			if (mDialogUp) {
				mResult = result;
				this.notifyAll();
			}
		}
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

	// Render the dialog; called from the UI thread.  May not block. 
	public void onStart(Context context) {
		mDialogUp = true;
		Log.d(TAG, "rendering user dialog");
	}

	// Dismiss a pending dialog, e.g. if the Activity is being torn down.  Called from the UI thread.
	public void onStop(Context context) {
		mDialogUp = false;
		Log.d(TAG, "tearing down user dialog");
	}

	// See if the dialog can be safely skipped based on SharedPreferences.  Called from the background thread.
	public Object earlyReturn() {
		Log.d(TAG, (mResult == null ? "not skipping" : "skipping") + " user dialog");
		return mResult;
	}
}
