package app.openconnect;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import app.openconnect.api.GrantPermissionsActivity;
import app.openconnect.core.ProfileManager;


public class OnBootReceiver extends BroadcastReceiver {

	public static final String TAG = "OpenConnect";

	// Debug: am broadcast -a android.intent.action.BOOT_COMPLETED
	@Override
	public void onReceive(Context context, Intent intent) {

		final String action = intent.getAction();

		if(Intent.ACTION_BOOT_COMPLETED.equals(action)) {
			VpnProfile bootProfile = ProfileManager.getOnBootProfile();
			if(bootProfile != null) {
				Log.i(TAG, "starting profile '" + bootProfile.getName() + "' on boot");
				launchVPN(bootProfile, context);
			} else {
				Log.d(TAG, "no boot profile configured");
			}
		}
	}

	void launchVPN(VpnProfile profile, Context context) {
		Intent startVpnIntent = new Intent(context, GrantPermissionsActivity.class);
		startVpnIntent.putExtra(context.getPackageName() + GrantPermissionsActivity.EXTRA_UUID,
				profile.getUUIDString());
		startVpnIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		//startVpnIntent.putExtra(LogWindow.EXTRA_HIDELOG, true);

		context.startActivity(startVpnIntent);
	}
}
