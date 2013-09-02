package de.blinkt.openvpn;

import de.blinkt.openvpn.core.ProfileManager;
import de.blinkt.openvpn.fragments.OpenConnectPreferencesFragment;
import de.blinkt.openvpn.fragments.VPNProfileList;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

public class OpenConnectPreferencesActivity extends Activity {

    private String mName;
    private String mUUID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        OpenConnectPreferencesFragment frag = new OpenConnectPreferencesFragment();
        mUUID = getIntent().getStringExtra(getPackageName() + ".profileUUID");
        Bundle args = new Bundle();
        args.putString("profileUUID", mUUID);
        frag.setArguments(args);

        SharedPreferences mPrefs = getSharedPreferences(mUUID, Context.MODE_PRIVATE);
        mName = getIntent().getStringExtra(getPackageName() + ".profileName");
        mPrefs.edit().putString("profile_name", mName).commit();

        // Hack to make checkProfile leave us alone
        ProfileManager pm = ProfileManager.getInstance(this);
        VpnProfile vp = ProfileManager.get(this, mUUID);
        vp.mAuthenticationType = VpnProfile.TYPE_STATICKEYS;
        pm.saveProfile(this, vp);

        // FIXME: VPNProfileList should use the names stored in SharedPreferences
        //mName = mPrefs.getString("profile_name", "");

        if (!mName.equals("")) {
            setTitle(getString(R.string.edit_profile_title, mName));
        }

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, frag)
                .commit();
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.vpnpreferences_menu, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if(item.getItemId() == R.id.remove_vpn)
			askProfileRemoval();
		return super.onOptionsItemSelected(item);
	}

	private void askProfileRemoval() {
		AlertDialog.Builder dialog = new AlertDialog.Builder(this);
		dialog.setTitle("Confirm deletion");
		dialog.setMessage(getString(R.string.remove_vpn_query, mName));

		dialog.setPositiveButton(android.R.string.yes,
				new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				Context ctx = OpenConnectPreferencesActivity.this;
				VpnProfile profile = ProfileManager.get(ctx, mUUID);
				if (profile != null) {
					/* TODO: delete the xml file from the app's data dir */
					ProfileManager.getInstance(ctx).removeProfile(ctx, profile);
				}
				setResult(VPNProfileList.RESULT_VPN_DELETED);
				finish();
			}
		});
		dialog.setNegativeButton(android.R.string.no,null);
		dialog.create().show();
	}
}
