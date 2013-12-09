package app.openconnect.core;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;
import app.openconnect.core.OpenVpnService.LocalBinder;

public abstract class VPNConnector {

	public static final String TAG = "OpenConnect";

	public OpenVpnService service;

	private Context mContext;
	private BroadcastReceiver mReceiver;
	private String mOwnerName;

	public abstract void onUpdate(OpenVpnService service);

	public VPNConnector(Context ctx) {
		mContext = ctx;
		Intent intent = new Intent(mContext, OpenVpnService.class);
		intent.setAction(OpenVpnService.START_SERVICE);
		mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

		mReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (service != null) {
					onUpdate(service);
				}
			}
		};
		mContext.registerReceiver(mReceiver, new IntentFilter(
				OpenVpnService.ACTION_VPN_STATUS));
		mOwnerName = mContext.getClass().getSimpleName();
	}

	public void stop() {
		if (mReceiver != null) {
			mContext.unregisterReceiver(mReceiver);
			mReceiver = null;
		}
		if (service != null) {
			service.stopActiveDialog(mContext);
		}
	}

	public void unbind() {
		stop();
		mContext.unbindService(mConnection);
	}

	private ServiceConnection mConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName className, IBinder serviceBinder) {
			// We've bound to LocalService, cast the IBinder and get
			// LocalService instance
			LocalBinder binder = (LocalBinder) serviceBinder;
			service = binder.getService();
			onUpdate(service);
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			service = null;
			Log.w(TAG, mOwnerName + " was forcibly unbound from OpenVpnService");
		}
	};
}
