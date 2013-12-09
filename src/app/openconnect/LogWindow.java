package app.openconnect;

import android.app.ListActivity;
import android.content.*;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.widget.*;
import android.widget.AdapterView.OnItemLongClickListener;
import app.openconnect.core.OpenConnectManagementThread;
import app.openconnect.core.OpenVpnService;
import app.openconnect.core.VPNConnector;
import app.openconnect.core.VPNLog;
import app.openconnect.core.VPNLog.LogArrayAdapter;

public class LogWindow extends ListActivity {
	public static final String TAG = "OpenConnect";

	private static final String LOGTIMEFORMAT = "logtimeformat";

	private VPNConnector mConn;

    private MenuItem mCancelButton;
    private boolean mDisconnected;

	private LogArrayAdapter mLogAdapter;
	private ListView mLogView;

	private TextView mSpeedView;

	private void sendReport() {
		String ver, dataText;

		dataText = mConn.service.dumpLog();

		try {
			ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
		} catch (NameNotFoundException e) {
			ver = "???";
		}

		// "ENTER PROBLEM DESCRIPTION" on the top (K9) and bottom (Email.apk) to cover both
		// types of client behavior
		String body = getString(R.string.enter_problem) + "\n\n\n\n" +
				dataText + "\n\n" +
				getString(R.string.enter_problem) + "\n\n";

		String uriText = "mailto:cernekee@gmail.com?subject=" +
				Uri.encode("ics-openconnect problem report - v" + ver) + "&body=" +
				Uri.encode(body);
		Intent email = new Intent(Intent.ACTION_SENDTO);
		email.setData(Uri.parse(uriText));

		// this shouldn't be necessary, but the default Android email client overrides
		// "body=" from the URI.  See MessageCompose.initFromIntent()
		email.putExtra(Intent.EXTRA_TEXT, body);

		startActivity(Intent.createChooser(email, getString(R.string.send_logfile)));
	}

    @Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (mConn.service == null) {
			return true;
		}
		if(item.getItemId()==R.id.clearlog) {
			mConn.service.clearLog();
			return true;
		} else if(item.getItemId()==R.id.cancel) {
			if (mDisconnected) {
				mConn.service.startReconnectActivity(this);
			} else {
				stopVPN();
			}
            return true;
        } else if(item.getItemId()==R.id.send) {
        	sendReport();
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
		updateUI(mConn.service);
		return true;
	}

    private synchronized void updateUI(OpenVpnService service) {
    	if (service != null) {
    		service.startActiveDialog(this);

    		int state = service.getConnectionState();
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
    			mLogAdapter = service.getArrayAdapter(this);
    			mLogAdapter.setTimeFormat(getPreferences(0).getInt(LOGTIMEFORMAT, VPNLog.TIME_FORMAT_LONG));
    			mLogView.setAdapter(mLogAdapter);
    			mLogView.setSelection(mLogAdapter.getCount());
    		}
    	}
    }

	@Override
	protected void onResume() {
		super.onResume();

		mConn = new VPNConnector(this) {
			@Override
			public void onUpdate(OpenVpnService service) {
				updateUI(service);
			}
		};
	}

	@Override
	protected void onPause() {
		mConn.stop();
		if (mConn.service != null) {
    		mConn.service.putArrayAdapter(mLogAdapter);
    		mLogAdapter = null;
		}
		mConn.unbind();
		super.onPause();
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
    	if (mConn.service != null) {
    		Log.d(TAG, "connection terminated via UI");
    		mConn.service.stopVPN();
    	}
    }

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

}
