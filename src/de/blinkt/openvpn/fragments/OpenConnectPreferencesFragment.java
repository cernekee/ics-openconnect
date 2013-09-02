package de.blinkt.openvpn.fragments;

import java.util.Map;

import de.blinkt.openvpn.R;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;

public class OpenConnectPreferencesFragment extends PreferenceFragment
		implements OnSharedPreferenceChangeListener {

	Context mContext;
	PreferenceManager mPrefs;

	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = getActivity();

        mPrefs = getPreferenceManager();
        mPrefs.setSharedPreferencesName(getArguments().getString("profileUUID"));
        mPrefs.setSharedPreferencesMode(Context.MODE_PRIVATE);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.pref_openconnect);

        SharedPreferences sp = mPrefs.getSharedPreferences();
        for (Map.Entry<String,?> entry : sp.getAll().entrySet()) {
            updateStringPref(sp, entry.getKey());
        }
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

    private void updateStringPref(SharedPreferences sp, String key) {
        String value;

        try {
            value = sp.getString(key, "");
        } catch (ClassCastException e) {
            /* wasn't a string preference */
            return;
        }

        Preference pref = findPreference(key);
        if (pref != null) {
            /* FIXME: show the user-friendly text on multiple choice entries */
            pref.setSummary(value);
        }
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        updateStringPref(sharedPreferences, key);
    }
}
