/*
 * Copyright (c) 2013, Kevin Cernekee
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
 * USA.
 *
 * In addition, as a special exception, the copyright holders give
 * permission to link the code of portions of this program with the
 * OpenSSL library.
 */

package app.openconnect.core;

import java.util.HashMap;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public abstract class UserDialog {
	public static final String TAG = "OpenConnect";

	private Object mResult;
	private boolean mDialogUp;
	protected SharedPreferences mPrefs;

	private static HashMap<String,DeferredPref> mDeferredPrefs = new HashMap<String,DeferredPref>();

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

	private abstract class DeferredPref {
		protected SharedPreferences mPrefs;
		protected String mKey;

		public DeferredPref(SharedPreferences prefs, String name) {
			mPrefs = prefs;
			mKey = name;
		}
		public abstract void commit();
	}

	private class DeferredStringPref extends DeferredPref {
		public String value;

		public DeferredStringPref(SharedPreferences prefs, String name, String newValue) {
			super(prefs, name);
			value = newValue;
		}

		public void commit() {
			mPrefs.edit().putString(mKey, value).commit();
		}
	}

	private class DeferredBooleanPref extends DeferredPref {
		public boolean value;

		public DeferredBooleanPref(SharedPreferences prefs, String name, boolean newValue) {
			super(prefs, name);
			value = newValue;
		}

		public void commit() {
			mPrefs.edit().putBoolean(mKey, value).commit();
		}
	}

	public static void clearDeferredPrefs() {
		mDeferredPrefs.clear();
	}

	public static void writeDeferredPrefs() {
		for (DeferredPref p : mDeferredPrefs.values()) {
			p.commit();
		}
		mDeferredPrefs.clear();
	}

	protected void setStringPref(String key, String value) {
		mDeferredPrefs.put(key, new DeferredStringPref(mPrefs, key, value));
	}

	protected String getStringPref(String key) {
		try {
			DeferredStringPref p = (DeferredStringPref)mDeferredPrefs.get(key);
			return p.value;
		} catch (ClassCastException e) {
		} catch (NullPointerException e) {
		}
		return mPrefs.getString(key, "");
	}

	protected void setBooleanPref(String key, boolean value) {
		mDeferredPrefs.put(key, new DeferredBooleanPref(mPrefs, key, value));
	}

	protected boolean getBooleanPref(String key) {
		try {
			DeferredBooleanPref p = (DeferredBooleanPref)mDeferredPrefs.get(key);
			return p.value;
		} catch (ClassCastException e) {
		} catch (NullPointerException e) {
		}
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
