package de.blinkt.openvpn;

import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;

public class ShowTextPreference extends DialogPreference {

    public ShowTextPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public ShowTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ShowTextPreference(Context context) {
        this(context, null);
    }

    @Override
    protected void onClick() {
        /* don't invoke the superclass; we do not want a dialog to pop up */
    }

    public void setText(String text) {
        if (text == null) {
            text = "";
        }
        persistString(text);
        notifyDependencyChange(shouldDisableDependents());
    }
}
