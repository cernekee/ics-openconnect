package app.openconnect;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.*;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.*;
import android.widget.*;
import android.widget.AdapterView.OnItemLongClickListener;
import app.openconnect.core.OpenConnectManagementThread;
import app.openconnect.core.OpenVpnService;
import app.openconnect.core.OpenVpnService.LocalBinder;
import app.openconnect.core.VPNLog;
import app.openconnect.core.VPNLog.LogArrayAdapter;

public class LogWindow extends ListActivity {
	public static final String TAG = "OpenConnect";

	private static final String LOGTIMEFORMAT = "logtimeformat";

	protected BroadcastReceiver mReceiver; 
	protected OpenVpnService mService;

    private MenuItem mCancelButton;
    private boolean mDisconnected;

	private LogArrayAdapter mLogAdapter;
	private ListView mLogView;

	private TextView mSpeedView;
	private AlertDialog mAlert;

	private ServiceConnection mConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName className,
				IBinder service) {
			// We've bound to LocalService, cast the IBinder and get LocalService instance
			LocalBinder binder = (LocalBinder) service;
			mService = binder.getService();
			updateUI();
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			mService = null;
			Log.w(TAG, "unbound from OpenVpnService");
		}
	};

    @Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if(item.getItemId()==R.id.clearlog) {
			mService.clearLog();
			return true;
		} else if(item.getItemId()==R.id.cancel) {
			if (mDisconnected) {
				if (mService != null) {
					mService.startReconnectActivity(this);
				}
			} else {
				stopVPN();
			}
            return true;
        } else if(item.getItemId()==R.id.send) {
			// XXX mLogAdapter.shareLog();
		} else if(item.getItemId() == R.id.toggle_time) {
			mLogAdapter.setTimeFormat(VPNLog.TIME_FORMAT_TOGGLE);
		} else if(item.getItemId() == android.R.id.home) {
			// This is called when the Home (Up) button is pressed
			// in the Action Bar.
			Intent parentActivityIntent = new Intent(this, MainActivity.class);
			parentActivityIntent.addFlags(
					Intent.FLAG_ACTIVITY_CLEAR_TOP |
					Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(parentActivityIntent);
			finish();
			return true;

		}
		return super.onOptionsItemSelected(item);

	}

    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.logmenu, menu);
		mCancelButton = menu.findItem(R.id.cancel);
		updateUI();
		return true;
	}

    private synchronized void updateUI() {
    	if (mService != null) {
    		mService.startActiveDialog(this);

    		int state = mService.getConnectionState();
    		if (mCancelButton != null) {
    			String title;
    			if (state == OpenConnectManagementThread.STATE_DISCONNECTED) {
    				title = getString(R.string.reconnect);
    				mCancelButton.setIcon(android.R.drawable.ic_menu_rotate);
    				mDisconnected = true;
    			} else {
    				title = getString(R.string.disconnect);
    				mCancelButton.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
    				mDisconnected = false;
    			}
				mCancelButton.setTitle(title);
				mCancelButton.setTitleCondensed(title);
    		}
    		String states[] = getResources().getStringArray(R.array.connection_states);
    		mSpeedView.setText(states[state]);

    		if (mLogAdapter == null) {
    			mLogAdapter = mService.getArrayAdapter(this);
    			mLogAdapter.setTimeFormat(getPreferences(0).getInt(LOGTIMEFORMAT, VPNLog.TIME_FORMAT_LONG));
    			mLogView.setAdapter(mLogAdapter);
    			mLogView.setSelection(mLogAdapter.getCount());
    		}
    	}
    }

	@Override
	protected void onStart() {
		super.onStart();

        Intent intent = new Intent(this, OpenVpnService.class);
        intent.setAction(OpenVpnService.START_SERVICE);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        IntentFilter filter = new IntentFilter();
        filter.addAction(getPackageName() + ".VPN_STATUS");

        mReceiver = new BroadcastReceiver() {
          @Override
          public void onReceive(Context context, Intent intent) {
        	  updateUI();
          }
        };
        registerReceiver(mReceiver, filter);
	}

	@Override
	protected void onStop() {
        unregisterReceiver(mReceiver);
    	if (mService != null) {
    		mService.stopActiveDialog();
    		mService.putArrayAdapter(mLogAdapter);
    		mLogAdapter = null;
    	}
        unbindService(mConnection);
        if (mAlert != null) {
        	mAlert.dismiss();
        }
		super.onStop();
    }

    @Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.logwindow);
		mLogView = getListView();

		mLogView.setOnItemLongClickListener(new OnItemLongClickListener() {

			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view,
					int position, long id) {
				ClipboardManager clipboard = (ClipboardManager)
						getSystemService(Context.CLIPBOARD_SERVICE);
				ClipData clip = ClipData.newPlainText("Log Entry",((TextView) view).getText());
				clipboard.setPrimaryClip(clip);
				Toast.makeText(getBaseContext(), R.string.copied_entry, Toast.LENGTH_SHORT).show();
				return true;
			}
		});

		mSpeedView = (TextView) findViewById(R.id.speed);
		getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    private void stopVPN() {
    	if (mService != null) {
    		mService.stopVPN();
    	}
    }

    private void handleBackButton() {
    	stopVPN();
    	super.onBackPressed();
    }

    @Override
    public void onBackPressed() {
    	if (mDisconnected) {
    		super.onBackPressed();
    		return;
    	}
    	mAlert = new AlertDialog.Builder(this)
    		.setTitle(R.string.cancel_connection_long)
    		.setMessage(R.string.cancel_connection_query)
    		.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					handleBackButton();
				}
    		})
    		.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
				}
    		})
    		.create();
    	mAlert.setOnDismissListener(new DialogInterface.OnDismissListener() {
			@Override
			public void onDismiss(DialogInterface dialog) {
				mAlert = null;
			}
		});
    	mAlert.show();
    }

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

}
