package app.openconnect.core;

import android.Manifest.permission;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.VpnService;
import android.os.*;
import android.os.Handler.Callback;
import android.preference.PreferenceManager;
import app.openconnect.LogWindow;
import app.openconnect.R;
import app.openconnect.VpnProfile;
import app.openconnect.api.GrantPermissionsActivity;
import app.openconnect.core.OpenVPN.ByteCountListener;
import app.openconnect.core.OpenVPN.ConnectionStatus;
import app.openconnect.core.OpenVPN.StateListener;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;

import static app.openconnect.core.OpenVPN.ConnectionStatus.*;

public class OpenVpnService extends VpnService implements StateListener, Callback, ByteCountListener {
	public static final String START_SERVICE = "app.openconnect.START_SERVICE";
	public static final String START_SERVICE_STICKY = "app.openconnect.START_SERVICE_STICKY";
	public static final String ALWAYS_SHOW_NOTIFICATION = "app.openconnect.NOTIFICATION_ALWAYS_VISIBLE";

	public static final String EXTRA_UUID = ".UUID";

    public static final String DISCONNECT_VPN = "app.openconnect.DISCONNECT_VPN";
    private static final String PAUSE_VPN = "app.openconnect.PAUSE_VPN";
    private static final String RESUME_VPN = "app.openconnect.RESUME_VPN";


    private Thread mProcessThread=null;

	private VpnProfile mProfile;

	private DeviceStateReceiver mDeviceStateReceiver;

	private boolean mDisplayBytecount=false;

	private boolean mStarting=false;

	private long mConnecttime;


	private static final int OPENVPN_STATUS = 1;

	private static boolean mNotificationAlwaysVisible =false;

	private final IBinder mBinder = new LocalBinder();
	private boolean mOvpn3 = false;

	private String mUUID;
	private OpenVPNManagement mManagement;

	private UserDialog mDialog;
	private Context mDialogContext;

	private int mConnectionState = OpenConnectManagementThread.STATE_DISCONNECTED;

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
		mManagement.stopVPN();
		endVpnService();
	}

	// Similar to revoke but do not try to stop process
	public void processDied() {
		endVpnService();
	}

	private void endVpnService() {
		mProcessThread=null;
		OpenVPN.removeByteCountListener(this);
		unregisterDeviceStateReceiver();
		ProfileManager.setConntectedVpnProfileDisconnected(this);
		if(!mStarting) {
			stopForeground(!mNotificationAlwaysVisible);

			if( !mNotificationAlwaysVisible) {
				stopSelf();
				OpenVPN.removeStateListener(this);
			}
		}
	}

	private void showNotification(String msg, String tickerText, boolean lowpriority, long when, ConnectionStatus status) {
		String ns = Context.NOTIFICATION_SERVICE;
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);


		int icon = getIconByConnectionStatus(status);

		android.app.Notification.Builder nbuilder = new Notification.Builder(this);

		if(mProfile!=null)
			nbuilder.setContentTitle(getString(R.string.notifcation_title,mProfile.mName));
		else
			nbuilder.setContentTitle(getString(R.string.notifcation_title_notconnect));

		nbuilder.setContentText(msg);
		nbuilder.setOnlyAlertOnce(true);
		nbuilder.setOngoing(true);
		nbuilder.setContentIntent(getLogPendingIntent());
		nbuilder.setSmallIcon(icon);


		if(when !=0)
			nbuilder.setWhen(when);


		// Try to set the priority available since API 16 (Jellybean)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
		    jbNotificationExtras(lowpriority, nbuilder);

		if(tickerText!=null && !tickerText.equals(""))
			nbuilder.setTicker(tickerText);

		@SuppressWarnings("deprecation")
		Notification notification = nbuilder.getNotification();


		mNotificationManager.notify(OPENVPN_STATUS, notification);
		startForeground(OPENVPN_STATUS, notification);
	}

    private int getIconByConnectionStatus(ConnectionStatus level) {
       switch (level) {
           case LEVEL_CONNECTED:
               return R.drawable.ic_stat_vpn;
           case LEVEL_AUTH_FAILED:
           case LEVEL_NONETWORK:
           case LEVEL_NOTCONNECTED:
               return R.drawable.ic_stat_vpn_offline;
           case LEVEL_CONNECTING_NO_SERVER_REPLY_YET:
           case LEVEL_WAITING_FOR_USER_INPUT:
               return R.drawable.ic_stat_vpn_outline;
           case LEVEL_CONNECTING_SERVER_REPLIED:
               return R.drawable.ic_stat_vpn_empty_halo;
           case LEVEL_VPNPAUSED:
               return android.R.drawable.ic_media_pause;
           case UNKNOWN_LEVEL:
           default:
               return R.drawable.ic_stat_vpn;

       }
    }


    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private void jbNotificationExtras(boolean lowpriority,
			android.app.Notification.Builder nbuilder) {
		try {
			if(lowpriority) {
				Method setpriority = nbuilder.getClass().getMethod("setPriority", int.class);
				// PRIORITY_MIN == -2
				setpriority.invoke(nbuilder, -2 );

				Method setUsesChronometer = nbuilder.getClass().getMethod("setUsesChronometer", boolean.class);
				setUsesChronometer.invoke(nbuilder,true);

			}

            Intent disconnectVPN = new Intent(this,LogWindow.class);
            disconnectVPN.setAction(DISCONNECT_VPN);
            PendingIntent disconnectPendingIntent = PendingIntent.getActivity(this, 0, disconnectVPN, 0);

            nbuilder.addAction(android.R.drawable.ic_menu_close_clear_cancel,
                    getString(R.string.cancel_connection),disconnectPendingIntent);

            Intent pauseVPN = new Intent(this,OpenVpnService.class);
            if (mDeviceStateReceiver == null || !mDeviceStateReceiver.isUserPaused()) {
                pauseVPN.setAction(PAUSE_VPN);
                PendingIntent pauseVPNPending = PendingIntent.getService(this,0,pauseVPN,0);
                nbuilder.addAction(android.R.drawable.ic_media_pause,
                        getString(R.string.pauseVPN), pauseVPNPending);

            } else {
                pauseVPN.setAction(RESUME_VPN);
                PendingIntent resumeVPNPending = PendingIntent.getService(this,0,pauseVPN,0);
                nbuilder.addAction(android.R.drawable.ic_media_play,
                        getString(R.string.resumevpn), resumeVPNPending);
            }


            //ignore exception
		} catch (NoSuchMethodException nsm) {
            nsm.printStackTrace();
		} catch (IllegalArgumentException e) {
            e.printStackTrace();
		} catch (IllegalAccessException e) {
            e.printStackTrace();
		} catch (InvocationTargetException e) {
            e.printStackTrace();
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
		OpenVPN.addByteCountListener(mDeviceStateReceiver);
	}

	synchronized void unregisterDeviceStateReceiver() {
		if(mDeviceStateReceiver!=null)
			try {
				OpenVPN.removeByteCountListener(mDeviceStateReceiver);
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

		if(intent != null && intent.getBooleanExtra(ALWAYS_SHOW_NOTIFICATION, false))
			mNotificationAlwaysVisible =true;

		OpenVPN.addStateListener(this);
		OpenVPN.addByteCountListener(this);

        if(intent != null && PAUSE_VPN.equals(intent.getAction()))
        {
            if(mDeviceStateReceiver!=null)
                mDeviceStateReceiver.userPause(true);
            return START_NOT_STICKY;
        }

        if(intent != null && RESUME_VPN.equals(intent.getAction()))
        {
            if(mDeviceStateReceiver!=null)
                mDeviceStateReceiver.userPause(false);
            return START_NOT_STICKY;
        }


        if(intent != null && START_SERVICE.equals(intent.getAction()))
			return START_NOT_STICKY;
		if(intent != null && START_SERVICE_STICKY.equals(intent.getAction())) {
			return START_REDELIVER_INTENT;
		}

        assert(intent!=null);

		// Extract information from the intent.
		String prefix = getPackageName();
		String[] argv = { }; //intent.getStringArrayExtra(prefix + ".ARGV");
		String nativelibdir = ""; //intent.getStringExtra(prefix + ".nativelib");
		mUUID = intent.getStringExtra(prefix + EXTRA_UUID);

		mProfile = ProfileManager.get(this, mUUID);

		String startTitle = getString(R.string.start_vpn_title, mProfile.mName);
		String startTicker = getString(R.string.start_vpn_ticker, mProfile.mName);
		showNotification(startTitle, startTicker,
				false,0, LEVEL_CONNECTING_NO_SERVER_REPLY_YET);

		// Set a flag that we are starting a new VPN
		mStarting=true;
		// Stop the previous session by interrupting the thread.
		if(mManagement!=null && mManagement.stopVPN())
			// an old was asked to exit, wait 1s
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
                e.printStackTrace();
			}


		if (mProcessThread!=null) {
			mProcessThread.interrupt();
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
                e.printStackTrace();
			}
		}
		// An old running VPN should now be exited
		mStarting=false;


        // Open the Management Interface
        if (!mOvpn3) {

            // start a Thread that handles incoming messages of the managment socket
            OpenConnectManagementThread ovpnManagementThread =
				new OpenConnectManagementThread(getApplicationContext(), mProfile, this);
            if (ovpnManagementThread.openManagementInterface(this)) {

                Thread mSocketManagerThread = new Thread(ovpnManagementThread, "OpenVPNManagementThread");
                mSocketManagerThread.start();
                mManagement = ovpnManagementThread;
                OpenVPN.logInfo("started Socket Thread");
            }
        }

        // Start a new session by creating a new thread.
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);        

		mOvpn3 = prefs.getBoolean("ovpn3", false);
		mOvpn3 = false;

		Runnable processThread;
		if(mOvpn3) {

			OpenVPNManagement mOpenVPN3 = instantiateOpenVPN3Core();
			processThread = (Runnable) mOpenVPN3;
			mManagement = mOpenVPN3;


        } else {
            HashMap<String, String> env = new HashMap<String, String>();
            processThread = new OpenVPNThread(this, argv, env, nativelibdir);
        }

		mProcessThread = new Thread(processThread, "OpenVPNProcessThread");
		//mProcessThread.start();

		if(mDeviceStateReceiver!=null)
			unregisterDeviceStateReceiver();
		
		registerDeviceStateReceiver(mManagement);


		ProfileManager.setConnectedVpnProfile(this, mProfile);

        return START_NOT_STICKY;
    }

	private OpenVPNManagement instantiateOpenVPN3Core() {
		return null;
	}

	@Override
	public void onDestroy() {
		if (mProcessThread != null) {
			mManagement.stopVPN();

			mProcessThread.interrupt();
		}
		if (mDeviceStateReceiver!= null) {
			this.unregisterReceiver(mDeviceStateReceiver);
		}
		// Just in case unregister for state
		OpenVPN.removeStateListener(this);

	}

	public Builder getVpnServiceBuilder() {
		VpnService.Builder b = new VpnService.Builder();
		b.setSession(mProfile.mName);
		b.setConfigureIntent(getLogPendingIntent());
		return b;
	}

	@Override
	public void updateState(String state,String logmessage, int resid, ConnectionStatus level) {
		// If the process is not running, ignore any state, 
		// Notification should be invisible in this state
		doSendBroadcast(state, level);
		if(mProcessThread==null && !mNotificationAlwaysVisible)
			return;

		// Display byte count only after being connected

		{
			if (level == LEVEL_WAITING_FOR_USER_INPUT) {
				// The user is presented a dialog of some kind, no need to inform the user 
				// with a notifcation
				return;
			} else if(level == LEVEL_CONNECTED) {
				mDisplayBytecount = true;
				mConnecttime = System.currentTimeMillis();
			} else {
				mDisplayBytecount = false;
			}

			// Other notifications are shown,
			// This also mean we are no longer connected, ignore bytecount messages until next
			// CONNECTED
			String ticker = getString(resid);
			showNotification(getString(resid) +" " + logmessage,ticker,false,0, level);

		}
	}

	private void doSendBroadcast(String state, ConnectionStatus level) {
		Intent vpnstatus = new Intent();
		vpnstatus.setAction(getPackageName() + ".VPN_STATUS");
		vpnstatus.putExtra("status", level.toString());
		vpnstatus.putExtra("detailstatus", state);
		sendBroadcast(vpnstatus, permission.ACCESS_NETWORK_STATE);
	}

	@Override
	public void updateByteCount(long in, long out, long diffin, long diffout) {
		if(mDisplayBytecount) {
			String netstat = String.format(getString(R.string.statusline_bytecount),
					humanReadableByteCount(in, false),
					humanReadableByteCount(diffin/ OpenVPNManagement.mBytecountInterval, true),
					humanReadableByteCount(out, false),
					humanReadableByteCount(diffout/ OpenVPNManagement.mBytecountInterval, true));

			boolean lowpriority = !mNotificationAlwaysVisible;
			showNotification(netstat,null,lowpriority,mConnecttime, LEVEL_CONNECTED);
		}

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

	@Override
	public boolean handleMessage(Message msg) {
		Runnable r = msg.getCallback();
		if(r!=null){
			r.run();
			return true;
		} else {
			return false;
		}
	}

	public OpenVPNManagement getManagement() {
		return mManagement;
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

	public synchronized void setConnectionState(int state) {
		mConnectionState = state;
		wakeUpActivity();
	}

	public synchronized int getConnectionState() {
		return mConnectionState;
	}

	public void startReconnectActivity(Context context) {
		Intent intent = new Intent(context, GrantPermissionsActivity.class);
		intent.putExtra(getPackageName() + GrantPermissionsActivity.EXTRA_UUID, mUUID);
		context.startActivity(intent);
	}

	public void stopVPN() {
		if (mManagement != null) {
			mManagement.stopVPN();
		}
	}
}
