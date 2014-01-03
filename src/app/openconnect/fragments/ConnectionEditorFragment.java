package app.openconnect.fragments;

import java.util.Map;

import app.openconnect.FileSelect;
import app.openconnect.ConnectionEditorActivity;
import app.openconnect.R;
import app.openconnect.ShowTextPreference;
import app.openconnect.VpnProfile;
import app.openconnect.core.ProfileManager;
import android.os.Bundle;
import android.os.Environment;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;

public class ConnectionEditorFragment extends PreferenceFragment
		implements OnSharedPreferenceChangeListener, OnPreferenceClickListener {

	PreferenceManager mPrefs;
	VpnProfile mProfile;

    String fileSelectKeys[] = { "ca_certificate", "user_certificate", "private_key", "custom_csd_wrapper" };

	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mProfile = ProfileManager.get(getArguments().getString("profileUUID"));

        mPrefs = getPreferenceManager();
        mPrefs.setSharedPreferencesName(ProfileManager.getPrefsName(mProfile.getUUIDString()));
        mPrefs.setSharedPreferencesMode(Context.MODE_PRIVATE);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.pref_openconnect);

        SharedPreferences sp = mPrefs.getSharedPreferences();
        for (Map.Entry<String,?> entry : sp.getAll().entrySet()) {
            updatePref(sp, entry.getKey());
        }

        setClickListeners();
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

    private void updatePref(SharedPreferences sp, String key) {
        String value;
        try {
            value = sp.getString(key, "");
        } catch (ClassCastException e) {
            /* wasn't a string preference */
            return;
        }

        Preference pref = findPreference(key);
        if (pref != null) {
			if (pref instanceof ListPreference) {
				ListPreference lpref = (ListPreference)pref;
				pref.setSummary(lpref.getEntry());
			} else {
				/* for ShowTextPreference entries, hide the raw base64 cert data */
				if (value.startsWith("[[INLINE]]")) {
					pref.setSummary("[STORED]");
				} else {
					pref.setSummary(value);
				}
			}
        }

        /* disable token_string item if the profile isn't using a software token */ 
        if (key.equals("software_token")) {
            pref = findPreference("token_string");
            if (pref != null) {
                pref.setEnabled(!value.equals("disabled"));
            }
        }

        if (key.equals("profile_name")) {
        	((ConnectionEditorActivity)getActivity()).setProfileName(value);
        }
    }

	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		updatePref(sharedPreferences, key);
	}

	/* These functions start up a FileSelect activity to import data from the filesystem */

	private void setClickListeners() {
		for (String key : fileSelectKeys) {
			Preference p = findPreference(key);
			p.setOnPreferenceClickListener(this);
		}
	}

	@Override
	public boolean onPreferenceClick(Preference preference) {
		int idx;

		String key = preference.getKey();
		for (idx = 0; ; idx++) {
			if (idx == fileSelectKeys.length) {
				return false;
			}
			if (fileSelectKeys[idx].equals(key)) {
				break;
			}
		}

		Intent startFC = new Intent(getActivity(), FileSelect.class);
		startFC.putExtra(FileSelect.START_DATA, Environment.getExternalStorageDirectory().getPath());
		startFC.putExtra(FileSelect.SHOW_CLEAR_BUTTON, true);

		startActivityForResult(startFC, idx);
		return false;
	}

	@Override
	public void onActivityResult(int idx, int resultCode, Intent data) {
		super.onActivityResult(idx, resultCode, data);
		if (resultCode == Activity.RESULT_OK) {
			String key = fileSelectKeys[idx];
			ShowTextPreference p = (ShowTextPreference)findPreference(key);
			p.setText(data.getStringExtra(FileSelect.RESULT_DATA));
			updatePref(mPrefs.getSharedPreferences(), key);
		}
	}

}
