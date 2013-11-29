package de.blinkt.openvpn;

import java.util.Map;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.DialogPreference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;

public class ClearPasswordPreference extends DialogPreference {
    public ClearPasswordPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            PreferenceManager mPrefs = getPreferenceManager();
            SharedPreferences sp = mPrefs.getSharedPreferences();
            for (Map.Entry<String,?> entry : sp.getAll().entrySet()) {
            	String key = entry.getKey();
            	if (key.startsWith("FORMDATA-")) {
            		sp.edit().putString(key, "").commit();
            	}
            }
        }
    }
}
