/*
 * Copyright (c) 2014, Kevin Cernekee
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
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.Button;
import app.openconnect.R;

public class FeedbackFragment extends Fragment {

	public static final String TAG = "OpenConnect";
	public static final String marketURI = "market://details?id=app.openconnect";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
    		Bundle savedInstanceState) {

    	View v = inflater.inflate(R.layout.feedback, container, false);

    	final Activity act = getActivity();
    	Button b;

    	/*
    	 * Adapted from:
    	 * http://www.techrepublic.com/blog/software-engineer/get-more-positive-ratings-for-your-app-in-google-play/1111/
    	 */
    	b = (Button)v.findViewById(R.id.i_love_it);
    	b.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(marketURI));
				startActivity(i);
				act.finish();
			}
    	});

    	b = (Button)v.findViewById(R.id.needs_work);
    	b.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				String ver = "???";
				try {
					PackageInfo packageinfo = act.getPackageManager().getPackageInfo(act.getPackageName(), 0);
					ver = packageinfo.versionName;
				} catch (NameNotFoundException e) {
				}
				Intent i = new Intent(android.content.Intent.ACTION_SEND);

				i.putExtra(android.content.Intent.EXTRA_EMAIL, new String[] {"cernekee+oc@gmail.com"});
				i.putExtra(android.content.Intent.EXTRA_SUBJECT, "ics-openconnect v" +
						ver + " - Needs Improvement!");
				i.setType("plain/text");

				startActivity(i);
				act.finish();
			}
    	});

    	b = (Button)v.findViewById(R.id.maybe_later);
    	b.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				act.finish();
			}
    	});

    	return v;
    }

}
