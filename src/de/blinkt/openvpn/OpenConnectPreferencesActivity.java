package de.blinkt.openvpn;

import de.blinkt.openvpn.fragments.OpenConnectPreferencesFragment;
import android.app.Activity;
import android.os.Bundle;

public class OpenConnectPreferencesActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        OpenConnectPreferencesFragment frag = new OpenConnectPreferencesFragment();
        String profileUUID = getIntent().getStringExtra(getPackageName() + ".profileUUID");
        Bundle args = new Bundle();
        args.putString("profileUUID", profileUUID);
        frag.setArguments(args);

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, frag)
                .commit();
    }
}
