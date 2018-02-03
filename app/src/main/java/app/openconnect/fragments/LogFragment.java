/*
 * Adapted from OpenVPN for Android
 * Copyright (c) 2012-2013, Arne Schwabe
 * Copyright (c) 2013, Kevin Cernekee
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
 * USA.
 *
 * In addition, as a special exception, the copyright holders give
 * permission to link the code of portions of this program with the
 * OpenSSL library.
 */

package app.openconnect.fragments;

import android.app.Activity;
import android.app.ListFragment;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemLongClickListener;
import app.openconnect.R;
import app.openconnect.core.OpenConnectManagementThread;
import app.openconnect.core.OpenVpnService;
import app.openconnect.core.VPNConnector;
import app.openconnect.core.VPNLog;
import app.openconnect.core.VPNLog.LogArrayAdapter;

public class LogFragment extends ListFragment {
	public static final String TAG = "OpenConnect";

	private VPNConnector mConn;

	private CommonMenu mDropdown;
    private MenuItem mCancelButton;
    private boolean mDisconnected;

	private LogArrayAdapter mLogAdapter;
	private ListView mLogView;
	private Activity mActivity;

	private TextView mSpeedView;

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
				mConn.service.startReconnectActivity(mActivity);
			} else {
				stopVPN();
			}
            return true;
		} else if(mDropdown.onOptionsItemSelected(item)) {
			return true;
		}
		return super.onOptionsItemSelected(item);

	}

    @Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.logmenu, menu);
		mDropdown = new CommonMenu(getActivity(), menu, true);
		mCancelButton = menu.findItem(R.id.cancel);
		if (mConn != null) {
			updateUI(mConn.service);
		}
	}

    private synchronized void updateUI(OpenVpnService service) {
    	if (service != null) {
    		int state = service.getConnectionState();
    		if (mCancelButton != null) {
    			String title;
    			if (state == OpenConnectManagementThread.STATE_DISCONNECTED) {
    				title = getString(R.string.reconnect);
    				mCancelButton.setIcon(R.drawable.ic_action_refresh);
					mCancelButton.setVisible(service.getReconnectName() != null);
    				mDisconnected = true;
    			} else {
    				title = getString(R.string.disconnect);
    				mCancelButton.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
					mCancelButton.setVisible(true);
    				mDisconnected = false;
    			}
				mCancelButton.setTitle(title);
				mCancelButton.setTitleCondensed(title);
    		}

    		String byteCountSummary = "";
    		if (state == OpenConnectManagementThread.STATE_CONNECTED) {
				byteCountSummary = " - " + mConn.getByteCountSummary();
    		}
    		String states[] = getResources().getStringArray(R.array.connection_states);
    		mSpeedView.setText(states[state] + byteCountSummary);

    		if (mLogAdapter == null) {
    			mLogAdapter = service.getArrayAdapter(mActivity);
    			mLogView.setAdapter(mLogAdapter);
    			mLogView.setSelection(mLogAdapter.getCount());
    		}

    		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
    		mLogAdapter.setTimeFormat(prefs.getString("timestamp_format", VPNLog.DEFAULT_TIME_FORMAT));
    	}
    }

	@Override
	public void onResume() {
		super.onResume();

		mConn = new VPNConnector(mActivity, false) {
			@Override
			public void onUpdate(OpenVpnService service) {
				updateUI(service);
			}
		};
	}

	@Override
	public void onPause() {
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
		setHasOptionsMenu(true);
	}

    @Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.logwindow, container, false);

		mActivity = getActivity();

		mLogView = (ListView)v.findViewById(android.R.id.list);
		mLogView.setOnItemLongClickListener(new OnItemLongClickListener() {

			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view,
					int position, long id) {
				ClipboardManager clipboard = (ClipboardManager)
						mActivity.getSystemService(Context.CLIPBOARD_SERVICE);
				ClipData clip = ClipData.newPlainText("Log Entry",((TextView) view).getText());
				clipboard.setPrimaryClip(clip);
				Toast.makeText(mActivity.getBaseContext(), R.string.copied_entry, Toast.LENGTH_SHORT).show();
				return true;
			}
		});

		mSpeedView = (TextView)v.findViewById(R.id.speed);
		return v;
    }

    private void stopVPN() {
    	if (mConn.service != null) {
    		Log.d(TAG, "connection terminated via UI");
    		mConn.service.stopVPN();
    	}
    }
}
