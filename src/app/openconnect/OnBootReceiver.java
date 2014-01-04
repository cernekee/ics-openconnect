/*
 * Adapted from OpenVPN for Android
 * Copyright (c) 2012-2013, Arne Schwabe
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

package app.openconnect;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import app.openconnect.api.GrantPermissionsActivity;
import app.openconnect.core.ProfileManager;


public class OnBootReceiver extends BroadcastReceiver {

	public static final String TAG = "OpenConnect";

	// Debug: am broadcast -a android.intent.action.BOOT_COMPLETED
	@Override
	public void onReceive(Context context, Intent intent) {

		final String action = intent.getAction();

		if(Intent.ACTION_BOOT_COMPLETED.equals(action)) {
			VpnProfile bootProfile = ProfileManager.getOnBootProfile();
			if(bootProfile != null) {
				Log.i(TAG, "starting profile '" + bootProfile.getName() + "' on boot");
				launchVPN(bootProfile, context);
			} else {
				Log.d(TAG, "no boot profile configured");
			}
		}
	}

	void launchVPN(VpnProfile profile, Context context) {
		Intent startVpnIntent = new Intent(context, GrantPermissionsActivity.class);
		startVpnIntent.putExtra(context.getPackageName() + GrantPermissionsActivity.EXTRA_UUID,
				profile.getUUIDString());
		startVpnIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		//startVpnIntent.putExtra(LogWindow.EXTRA_HIDELOG, true);

		context.startActivity(startVpnIntent);
	}
}
