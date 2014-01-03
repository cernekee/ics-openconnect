package app.openconnect;

import app.openconnect.core.ProfileManager;
import app.openconnect.fragments.ConnectionEditorFragment;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

public class ConnectionEditorActivity extends Activity {

    private String mName = "";
    private String mUUID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ConnectionEditorFragment frag = new ConnectionEditorFragment();
        mUUID = getIntent().getStringExtra(getPackageName() + ".profileUUID");
        Bundle args = new Bundle();
        args.putString("profileUUID", mUUID);
        frag.setArguments(args);

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

	public void setProfileName(String name) {
		mName = name;
    	setTitle(getString(R.string.edit_profile_title, mName));
	}

	private void askProfileRemoval() {
		AlertDialog.Builder dialog = new AlertDialog.Builder(this);
		dialog.setTitle("Confirm deletion");
		dialog.setMessage(getString(R.string.remove_vpn_query, mName));

		dialog.setPositiveButton(android.R.string.yes,
				new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				ProfileManager.delete(mUUID);
				finish();
			}
		});
		dialog.setNegativeButton(android.R.string.no,null);
		dialog.create().show();
	}
}
