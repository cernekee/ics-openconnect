package de.blinkt.openvpn;

import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;

public class ClearPasswordPreference extends DialogPreference {
    public ClearPasswordPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            // TODO: clear saved passwords
        }
    }
}
