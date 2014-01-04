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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.util.Log;

public class DeviceStateReceiver extends BroadcastReceiver {

	public static final String TAG = "OpenConnect";

    private OpenVPNManagement mManagement;
    private boolean mPauseOnScreenOff;
    private boolean mNetchangeReconnect;

    private boolean mScreenOff;
    private boolean mNetworkOff;
    private int mNetworkType = -1;
    private boolean mKeepaliveActive;
    private boolean mPaused;

    public DeviceStateReceiver(OpenVPNManagement management, SharedPreferences prefs) {
        super();
        mManagement = management;
        mPauseOnScreenOff = prefs.getBoolean("screenoff", false);
        mNetchangeReconnect = prefs.getBoolean("netchangereconnect", false);
    }

    private void updatePauseState() {
    	boolean pause = false;
    	if (mPauseOnScreenOff && mScreenOff && !mKeepaliveActive) {
    		pause = true;
    	}
    	if (mNetworkOff) {
    		pause = true;
    	}
    	if (pause && !mPaused) {
    		Log.i(TAG, "pausing: mScreenOff=" + mScreenOff + " mNetworkOff=" + mNetworkOff);
    		mManagement.pause();
    	} else if (!pause && mPaused) {
    		Log.i(TAG, "resuming: mScreenOff=" + mScreenOff + " mNetworkOff=" + mNetworkOff);
    		mManagement.resume();
    	}
    	mPaused = pause;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
            networkStateChange(context);
        } else if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
        	mScreenOff = true;
        } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
        	mScreenOff = false;
        }
        updatePauseState();
    }

    public void networkStateChange(Context context) {
        ConnectivityManager conn = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = conn.getActiveNetworkInfo();

        if (networkInfo == null || networkInfo.getState() != State.CONNECTED) {
        	mNetworkOff = true;
        } else {
        	int networkType = networkInfo.getType();
        	if (mNetworkType != -1 && mNetworkType != networkType) {
        		if (!mPaused && mNetchangeReconnect) {
        			Log.i(TAG, "reconnecting due to network type change");
        			mManagement.reconnect();
        		}
        	}
        	mNetworkType = networkType;
        	mNetworkOff = false;
        }
    }

    public void setKeepalive(boolean active) {
    	mKeepaliveActive = active;
    	updatePauseState();
    }
}
