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

import java.util.Calendar;

import android.app.Activity;
import android.app.Fragment;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.Button;
import app.openconnect.FragActivity;
import app.openconnect.R;

public class FeedbackFragment extends Fragment {

	public static final String TAG = "OpenConnect";
	public static final String marketURI = "market://details?id=app.openconnect";

	/* ask for feedback exactly once, after NAGDAYS && NAGUSES */
	private static final int nagDays = 14;
	private static final long nagUses = 10;

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
				recordNag(act);

				Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(marketURI));
				try {
					startActivity(i);
				} catch (ActivityNotFoundException e) {
					/* not all devices have a handler for market:// URIs registered */
				}
				act.finish();
			}
    	});

    	b = (Button)v.findViewById(R.id.needs_work);
    	b.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				recordNag(act);

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

				try {
					startActivity(i);
				} catch (ActivityNotFoundException e) {
					/* this probably never happens */
				}
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

    private static void recordNag(Context ctx) {
    	SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
    	sp.edit().putBoolean("feedback_nagged", true).commit();
    }

    private static boolean isNagOK(Context ctx) {
    	SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);

    	if (sp.getBoolean("feedback_nagged", false)) {
    		return false;
    	}

    	long first = sp.getLong("first_use", -1);
    	if (first == -1) {
    		return false;
    	}

    	Calendar now = Calendar.getInstance();
    	Calendar nagDay = Calendar.getInstance();
    	nagDay.setTimeInMillis(first);
    	nagDay.add(Calendar.DATE, nagDays);
    	if (!now.after(nagDay)) {
    		return false;
    	}

    	long numUses = sp.getLong("num_uses", 0);
    	if (numUses < nagUses) {
    		return false;
    	}

    	return true;
    }

    public static void feedbackNag(Context ctx) {
    	if (!isNagOK(ctx)) {
    		return;
    	}
    	recordNag(ctx);

		Intent intent = new Intent(ctx, FragActivity.class);
		intent.putExtra(FragActivity.EXTRA_FRAGMENT_NAME, "FeedbackFragment");
		ctx.startActivity(intent);
    }

    public static void recordUse(Context ctx, boolean success) {
    	SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
    	if (sp.getLong("first_use", -1) == -1) {
    		long now = Calendar.getInstance().getTimeInMillis();
    		sp.edit().putLong("first_use", now).apply();
    	}
    	if (!success) {
    		return;
    	}

    	long numUses = sp.getLong("num_uses", 0);
    	sp.edit().putLong("num_uses", numUses + 1).apply();
    }

    public static void recordProfileAdd(Context ctx) {
    	SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
    	long count = sp.getLong("num_profiles_added", 0) + 1;
    	sp.edit().putLong("num_profiles_added", count).apply();
    }
}
