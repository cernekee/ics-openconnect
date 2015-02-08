/*
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

import android.app.Fragment;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import app.openconnect.R;
import app.openconnect.core.OpenConnectManagementThread;
import app.openconnect.core.OpenVpnService;
import app.openconnect.core.VPNConnector;

import org.infradead.libopenconnect.LibOpenConnect;

public class StatusFragment extends Fragment {

	private View mView;
	private VPNConnector mConn;

	private CommonMenu mDropdown;
	private Button mDisconnectButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
    		Bundle savedInstanceState) {
    	super.onCreateView(inflater, container, savedInstanceState);

    	mView = inflater.inflate(R.layout.status, container, false);
    	mDisconnectButton = (Button)mView.findViewById(R.id.disconnect_button);

    	mDisconnectButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				if (mConn.service.getConnectionState() ==
						OpenConnectManagementThread.STATE_DISCONNECTED) {
					mConn.service.startReconnectActivity(getActivity());
				} else {
					mConn.service.stopVPN();
				}
			}
    	});

    	mConn = new VPNConnector(getActivity(), false) {
			@Override
			public void onUpdate(OpenVpnService service) {
				updateUI(service);
			}
    	};

    	return mView;
    }

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		mDropdown = new CommonMenu(getActivity(), menu, true);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (mDropdown.onOptionsItemSelected(item)) {
			return true;
		} else {
			return super.onOptionsItemSelected(item);
		}
	}

    @Override
    public void onDestroyView() {
    	mConn.unbind();
    	super.onDestroyView();
    }

    private void writeStatusField(int id, int header_res, String value) {
    	String html = "<b>" + getString(header_res) + "</b><br>" + value;
    	TextView tv = (TextView)mView.findViewById(id);
    	tv.setText(Html.fromHtml(html));
    }

    private void updateUI(OpenVpnService service) {
		int state = service.getConnectionState();
		int visibility = View.INVISIBLE;

		if (state == OpenConnectManagementThread.STATE_CONNECTED) {

			visibility = View.VISIBLE;

			String s = getString(R.string.state_connected_to, service.profile.getName());
			if (s.length() < 25) {
				writeStatusField(R.id.connection_state, R.string.netstatus, s);
			} else {
				writeStatusField(R.id.connection_state, R.string.netstatus,
					service.getConnectionStateName());
			}
			writeStatusField(R.id.connection_time, R.string.uptime,
					OpenVpnService.formatElapsedTime(service.startTime.getTime()));

			int statsVisibility = mConn.statsValid ? View.VISIBLE : View.INVISIBLE;
			mView.findViewById(R.id.tx).setVisibility(statsVisibility);
			mView.findViewById(R.id.rx).setVisibility(statsVisibility);

			writeStatusField(R.id.tx, R.string.tx, getString(R.string.oneway_bytecount,
					OpenVpnService.humanReadableByteCount(mConn.deltaStats.txBytes, true),
					OpenVpnService.humanReadableByteCount(mConn.newStats.txBytes, false)));

			writeStatusField(R.id.rx, R.string.rx, getString(R.string.oneway_bytecount,
					OpenVpnService.humanReadableByteCount(mConn.deltaStats.rxBytes, true),
					OpenVpnService.humanReadableByteCount(mConn.newStats.rxBytes, false)));
			writeStatusField(R.id.server_name, R.string.server_name, service.serverName);

			LibOpenConnect.IPInfo ip = service.ipInfo;
			String dis = getString(R.string.disabled);

			if (ip.addr != null && ip.netmask != null) {
				writeStatusField(R.id.local_ip4, R.string.local_ip4, ip.addr);
				writeStatusField(R.id.local_ip4_netmask, R.string.subnet_mask, ip.netmask);
			} else {
				writeStatusField(R.id.local_ip4, R.string.local_ip4, dis);
			}

			writeStatusField(R.id.local_ip6, R.string.local_ip6, ip.netmask6 != null ? ip.netmask6 : dis);
		} else {
			writeStatusField(R.id.connection_state, R.string.netstatus,
					service.getConnectionStateName());
		}

		mView.findViewById(R.id.connection_rows).setVisibility(visibility);
		mView.findViewById(R.id.connection_time).setVisibility(visibility);

		// Check explicitly for "disconnected" so the user can cancel connections-in-progress
		if (state == OpenConnectManagementThread.STATE_DISCONNECTED) {
			String profileName = service.getReconnectName();
			if (profileName != null) {
				mDisconnectButton.setVisibility(View.VISIBLE);
				mDisconnectButton.setText(getString(R.string.reconnect_to, profileName));
			} else {
				mDisconnectButton.setVisibility(View.INVISIBLE);
			}
		} else {
			mDisconnectButton.setVisibility(View.VISIBLE);
			mDisconnectButton.setText(R.string.disconnect);
		}
    }
}
