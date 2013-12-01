package app.openconnect.core;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class KeepAlive extends BroadcastReceiver {

	public static final String TAG = "OpenConnect";

	public static final String ACTION_KEEPALIVE_ALARM = "app.openconnect.KEEPALIVE_ALARM";

	/* sync with pref_openconnect.xml */
	private static int DEFAULT_INTERVAL = 14;

	private static boolean mConnectionActive;

	private static PendingIntent mPendingIntent;
	private static int mBaseDelayMs;

	private void handleKeepAlive(Context context) {
		mPendingIntent = null;
		if (!mConnectionActive) {
			return;
		}

		/* TODO: send a probe here */

		Log.d(TAG, "handleKeepAlive");
		scheduleNext(context, mBaseDelayMs);
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();

		if (action.equals(OpenVpnService.ACTION_VPN_STATUS)) {
			int state = intent.getIntExtra(OpenVpnService.EXTRA_CONNECTION_STATE,
					OpenConnectManagementThread.STATE_DISCONNECTED);
			String UUID = intent.getStringExtra(OpenVpnService.EXTRA_UUID);
			handleVpnStatus(context, state, UUID);
		} else if (action.equals(ACTION_KEEPALIVE_ALARM)) {
			handleKeepAlive(context);
		}
	}

	private static void scheduleNext(Context context, int delayMs) {
		Intent intent = new Intent("app.openconnect.KEEPALIVE_ALARM");
		mPendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

		AlarmManager am = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
		am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMs, mPendingIntent);
	}

	private void handleVpnStatus(Context context, int state, String UUID) {
		if (mConnectionActive && state == OpenConnectManagementThread.STATE_DISCONNECTED) {
			stop(context);
		} else if (!mConnectionActive && state == OpenConnectManagementThread.STATE_CONNECTED) {
			SharedPreferences p = context.getSharedPreferences(UUID, Context.MODE_PRIVATE);
			int minutes = DEFAULT_INTERVAL;
			try {
				minutes = Integer.parseInt(p.getString("keepalive_interval", ""));
			} catch (NumberFormatException e) {
			}
			start(context, minutes * 60);
		}
	}

	private static void start(Context context, int seconds) {
		stop(context);
		mConnectionActive = true;

		if (seconds > 0) {
			mBaseDelayMs = seconds * 1000;
			scheduleNext(context, mBaseDelayMs);
		}
	}

	private static void stop(Context context) {
		mConnectionActive = false;
		if (mPendingIntent != null) {
			AlarmManager am = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
			am.cancel(mPendingIntent);
			mPendingIntent = null;
		}
	}
};
