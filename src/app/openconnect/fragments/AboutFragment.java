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

package app.openconnect.fragments;

import android.app.Activity;
import android.app.Fragment;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.TextView;
import app.openconnect.R;

public class AboutFragment extends Fragment  {

	public static final String TAG = "OpenConnect";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
    		Bundle savedInstanceState) {
    	Activity activity = getActivity();

    	View v = inflater.inflate(R.layout.about, container, false);
    	TextView ver = (TextView) v.findViewById(R.id.version);

		try {
			PackageInfo packageinfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
			ver.setText("OpenConnect for Android v" + packageinfo.versionName);
		} catch (NameNotFoundException e) {
			Log.e(TAG, "can't retrieve package version");
		}

    	WebView contents = (WebView)v.findViewById(R.id.about_contents);
    	contents.loadUrl("file:///android_asset/about.html");
    	contents.setBackgroundColor(0);

    	return v;
    }

}
