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
import java.io.File;
import java.util.Map;

import android.Manifest.permission;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import app.openconnect.R;
import app.openconnect.api.ExternalAppDatabase;
import app.openconnect.core.DeviceStateReceiver;

public class GeneralSettings extends PreferenceFragment
		implements OnPreferenceClickListener, OnClickListener, OnSharedPreferenceChangeListener {

	private ExternalAppDatabase mExtapp;
	private PreferenceManager mPrefs;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);


		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.general_settings);

		Preference loadtun = findPreference("loadTunModule");
		if(!isTunModuleAvailable())
			loadtun.setEnabled(false);

		mExtapp = new ExternalAppDatabase(getActivity());

		for (String s : new String[] { "netchangereconnect", "screenoff", "trace_log" }) {
			findPreference(s).setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference arg0, Object arg1) {
					Intent intent = new Intent(DeviceStateReceiver.PREF_CHANGED);
					getActivity().sendBroadcast(intent, permission.ACCESS_NETWORK_STATE);
					return true;
				}
			});
		}

		mPrefs = getPreferenceManager();
        SharedPreferences sp = mPrefs.getSharedPreferences();
        for (Map.Entry<String,?> entry : sp.getAll().entrySet()) {
            this.onSharedPreferenceChanged(sp, entry.getKey());
        }

		/*
		Preference clearapi = findPreference("clearapi");
		clearapi.setOnPreferenceClickListener(this);
		setClearApiSummary();
		*/
	}

    @Override
	public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

	public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
    	Preference pref = findPreference(key);
		if (pref instanceof ListPreference) {
			/* update all spinner prefs so the summary shows the current value */
			ListPreference lpref = (ListPreference)pref;
			lpref.setValue(sp.getString(key, ""));
			pref.setSummary(lpref.getEntry());
		}
    }

	private void setClearApiSummary() {
		Preference clearapi = findPreference("clearapi");

		if(mExtapp.getExtAppList().isEmpty()) {
			clearapi.setEnabled(false);
			clearapi.setSummary(R.string.no_external_app_allowed);
		} else { 
			clearapi.setEnabled(true);
			clearapi.setSummary(getString(R.string.allowed_apps,getExtAppList(", ")));
		}
	}

	private String getExtAppList(String delim) {
		ApplicationInfo app;
		PackageManager pm = getActivity().getPackageManager();

		String applist=null;
		for (String packagename : mExtapp.getExtAppList()) {
			try {
				app = pm.getApplicationInfo(packagename, 0);
				if (applist==null)
					applist = "";
				else
					applist += delim;
				applist+=app.loadLabel(pm);

			} catch (NameNotFoundException e) {
				// App not found. Remove it from the list
				mExtapp.removeApp(packagename);
			}
		}

		return applist;
	}

	private boolean isTunModuleAvailable() {
		// Check if the tun module exists on the file system
        return new File("/system/lib/modules/tun.ko").length() > 10;
    }

	@Override
	public boolean onPreferenceClick(Preference preference) { 
		if(preference.getKey().equals("clearapi")){
			Builder builder = new AlertDialog.Builder(getActivity());
			builder.setPositiveButton(R.string.clear, this);
			builder.setNegativeButton(android.R.string.cancel, null);
			builder.setMessage(getString(R.string.clearappsdialog,getExtAppList("\n")));
			builder.show();
		}
			
		return true;
	}

	@Override
	public void onClick(DialogInterface dialog, int which) {
		if( which == Dialog.BUTTON_POSITIVE){
			mExtapp.clearAllApiApps();
			setClearApiSummary();
		}
	}



}
