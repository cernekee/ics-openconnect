/*
 * Adapted from OpenVPN for Android
 * Copyright (c) 2012-2013, Arne Schwabe
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

package app.openconnect.api;

import app.openconnect.core.OpenVpnService;
import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;

public class GrantPermissionsActivity extends Activity {
	public static final String EXTRA_START_ACTIVITY = ".start_activity";
	public static final String EXTRA_UUID = ".UUID";

	private String mUUID;
	private String mStartActivity;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Intent myIntent = getIntent();
		mUUID = myIntent.getStringExtra(getPackageName() + EXTRA_UUID);
		if (mUUID == null) {
			finish();
			return;
		}
		mStartActivity = myIntent.getStringExtra(getPackageName() + EXTRA_START_ACTIVITY);

		Intent prepIntent = VpnService.prepare(this);
		if (prepIntent != null) {
			startActivityForResult(prepIntent, 0);
		} else {
			onActivityResult(0, RESULT_OK, null);
		}
	}

	/* Called by Android OS after user clicks "OK" on VpnService.prepare() dialog */ 
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		setResult(resultCode);

		if (resultCode == RESULT_OK) {
	    	Intent intent = new Intent(getBaseContext(), OpenVpnService.class);
	    	intent.putExtra(OpenVpnService.EXTRA_UUID, mUUID);
	    	startService(intent);

	    	if (mStartActivity != null) {
	    		intent = new Intent();
	    		intent.setClassName(this, mStartActivity);
	    		startActivity(intent);
	    	}
		}
		finish();
	}
}
