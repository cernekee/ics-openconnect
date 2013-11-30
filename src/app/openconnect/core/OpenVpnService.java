package app.openconnect.core;

import android.Manifest.permission;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.VpnService;
import android.os.*;
import android.preference.PreferenceManager;
import android.util.Log;
import app.openconnect.LogWindow;
import app.openconnect.VpnProfile;
import app.openconnect.api.GrantPermissionsActivity;
import app.openconnect.core.VPNLog.LogArrayAdapter;

import java.util.Locale;

public class OpenVpnService extends VpnService {

	public static final String TAG = "OpenConnect";

	public static final String START_SERVICE = "app.openconnect.START_SERVICE";
	public static final String START_SERVICE_STICKY = "app.openconnect.START_SERVICE_STICKY";
	public static final String ALWAYS_SHOW_NOTIFICATION = "app.openconnect.NOTIFICATION_ALWAYS_VISIBLE";

	public static final String EXTRA_UUID = ".UUID";

	private VpnProfile mProfile;

	private DeviceStateReceiver mDeviceStateReceiver;

	private final IBinder mBinder = new LocalBinder();

	private String mUUID;
	private int mStartId;

	private Thread mVPNThread;
	private OpenConnectManagementThread mVPN;

	private UserDialog mDialog;
	private Context mDialogContext;

	private int mConnectionState = OpenConnectManagementThread.STATE_DISCONNECTED;

	private VPNLog mVPNLog = new VPNLog();
	private Handler mHandler = new Handler();

	public class LocalBinder extends Binder {
		public OpenVpnService getService() {
			// Return this instance of LocalService so clients can call public methods
			return OpenVpnService.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		String action = intent.getAction();
		if( action !=null && action.equals(START_SERVICE))
			return mBinder;
		else
			return super.onBind(intent);
	}

	@Override
	public void onRevoke() {
		Log.i(TAG, "VPN access has been revoked");
		stopVPN();
	}

	@Override
	public void onCreate() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

		// Restore service state from disk if available
		// This gets overwritten if somebody calls startService()
		// NOTE: We aren't saving the log buffer
		mUUID = prefs.getString("service_mUUID", "");
	}

	@Override
	public void onDestroy() {
		killVPNThread(true);
		if (mDeviceStateReceiver != null) {
			this.unregisterReceiver(mDeviceStateReceiver);
		}

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		prefs.edit().putString("service_mUUID", mUUID).apply();
	}
	
	private synchronized boolean doStopVPN() {
		if (mVPN != null) {
			mVPN.stopVPN();
			return true;
		}
		return false;
	}

	private void killVPNThread(boolean joinThread) {
		if (doStopVPN() && joinThread) {
			try {
				mVPNThread.join(1000);
			} catch (InterruptedException e) {
				Log.e(TAG, "OpenConnect thread did not exit");
			}
		}
	}

	PendingIntent getLogPendingIntent() {
		// Let the configure Button show the Log
		Intent intent = new Intent(getBaseContext(),LogWindow.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
		PendingIntent startLW = PendingIntent.getActivity(this, 0, intent, 0);
		intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
		return startLW;
	}

	synchronized void registerDeviceStateReceiver(OpenVPNManagement magnagement) {
		// Registers BroadcastReceiver to track network connection changes.
		IntentFilter filter = new IntentFilter();
		filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		filter.addAction(Intent.ACTION_SCREEN_OFF);
		filter.addAction(Intent.ACTION_SCREEN_ON);
		mDeviceStateReceiver = new DeviceStateReceiver(magnagement);
		registerReceiver(mDeviceStateReceiver, filter);
	}

	synchronized void unregisterDeviceStateReceiver() {
		if(mDeviceStateReceiver!=null)
			try {
				this.unregisterReceiver(mDeviceStateReceiver);
			} catch (IllegalArgumentException iae) {
				// I don't know why  this happens:
				// java.lang.IllegalArgumentException: Receiver not registered: app.openconnect.NetworkSateReceiver@41a61a10
				// Ignore for now ...
				iae.printStackTrace();
			}
		mDeviceStateReceiver=null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		String action = intent.getAction();
		if (START_SERVICE.equals(action)) {
			return START_NOT_STICKY;
		} else if (START_SERVICE_STICKY.equals(action)) {
			return START_REDELIVER_INTENT;
		}

		// Extract information from the intent.
		String prefix = getPackageName();
		mUUID = intent.getStringExtra(prefix + EXTRA_UUID);
		if (mUUID == null) {
			return START_NOT_STICKY;
		}

		mProfile = ProfileManager.get(this, mUUID);
		killVPNThread(true);

		// stopSelfResult(most_recent_startId) will kill the service
		// stopSelfResult(previous_startId) will not
		mStartId = startId;

        mVPN = new OpenConnectManagementThread(getApplicationContext(), mProfile, this);
        mVPNThread = new Thread(mVPN, "OpenVPNManagementThread");
        mVPNThread.start();

		if(mDeviceStateReceiver != null)
			unregisterDeviceStateReceiver();
		registerDeviceStateReceiver(mVPN);

		ProfileManager.setConnectedVpnProfile(this, mProfile);

        return START_NOT_STICKY;
    }

	public Builder getVpnServiceBuilder() {
		VpnService.Builder b = new VpnService.Builder();
		b.setSession(mProfile.mName);
		b.setConfigureIntent(getLogPendingIntent());
		return b;
	}

	// From: http://stackoverflow.com/questions/3758606/how-to-convert-byte-size-into-human-readable-format-in-java
	public static String humanReadableByteCount(long bytes, boolean mbit) {
		if(mbit)
			bytes = bytes *8;
		int unit = mbit ? 1000 : 1024;
		if (bytes < unit)
			return bytes + (mbit ? " bit" : " B");

		int exp = (int) (Math.log(bytes) / Math.log(unit));
		String pre = (mbit ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (mbit ? "" : "");
		if(mbit)
			return String.format(Locale.getDefault(),"%.1f %sbit", bytes / Math.pow(unit, exp), pre);
		else 
			return String.format(Locale.getDefault(),"%.1f %sB", bytes / Math.pow(unit, exp), pre);
	}

	/* called from the activity on broadcast receipt, or startup */
	public synchronized void startActiveDialog(Context context) {
		if (mDialog != null && mDialogContext == null) {
			mDialogContext = context;
			mDialog.onStart(context);
		}
	}

	/* called when the activity shuts down (mDialog will be re-rendered when the activity starts again) */
	public synchronized void stopActiveDialog() {
		if (mDialog != null && mDialogContext != null) {
			mDialog.onStop(mDialogContext);
		}
		mDialogContext = null;
	}

	private synchronized void setDialog(Context context, UserDialog dialog) {
		mDialogContext = context;
		mDialog = dialog;
	}

	private void wakeUpActivity() {
		Intent vpnstatus = new Intent();
		vpnstatus.setAction(getPackageName() + ".VPN_STATUS");
		sendBroadcast(vpnstatus, permission.ACCESS_NETWORK_STATE);
	}

	/* called from the VPN thread; blocks until user responds */
	public Object promptUser(UserDialog dialog) {
		Object ret;

		ret = dialog.earlyReturn();
		if (ret != null) {
			return ret;
		}

		setDialog(null, dialog);
		wakeUpActivity();
		ret = mDialog.waitForResponse();

		setDialog(null, null);
		return ret;
	}

	public synchronized void threadDone() {
		final int startId = mStartId;

		Log.i(TAG, "VPN thread has terminated");
		mVPN = null;
		mHandler.post(new Runnable() {

			@Override
			public void run() {
				if (stopSelfResult(startId) == false) {
					Log.w(TAG, "not stopping service due to startId mismatch");
				}
			}
		});
	}

	public synchronized void setConnectionState(int state) {
		mConnectionState = state;
		wakeUpActivity();
	}

	public synchronized int getConnectionState() {
		return mConnectionState;
	}

	public LogArrayAdapter getArrayAdapter(Context context) {
		return mVPNLog.getArrayAdapter(context);
	}

	public void putArrayAdapter(LogArrayAdapter adapter) {
		if (adapter != null) {
			mVPNLog.putArrayAdapter(adapter);
		}
	}

	public void log(final int level, final String msg) {
		mHandler.post(new Runnable() {

			@Override
			public void run() {
				mVPNLog.add(level, msg);
			}
		});
	}

	public void clearLog() {
		mVPNLog.clear();
	}

	public String dumpLog() {
		return mVPNLog.dump();
	}

	public void startReconnectActivity(Context context) {
		Intent intent = new Intent(context, GrantPermissionsActivity.class);
		intent.putExtra(getPackageName() + GrantPermissionsActivity.EXTRA_UUID, mUUID);
		context.startActivity(intent);
	}

	public void stopVPN() {
		killVPNThread(false);
		ProfileManager.setConnectedVpnProfileDisconnected(this);
	}
}
