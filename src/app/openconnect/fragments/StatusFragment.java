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

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import app.openconnect.LogWindow;
import app.openconnect.R;
import app.openconnect.core.OpenConnectManagementThread;
import app.openconnect.core.OpenVpnService;
import app.openconnect.core.VPNConnector;

public class StatusFragment extends Fragment {

	private View mView;
	private VPNConnector mConn;

	private Button mLogButton;
	private Button mDisconnectButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
    		Bundle savedInstanceState) {
    	super.onCreateView(inflater, container, savedInstanceState);

    	mView = inflater.inflate(R.layout.status, container, false);
    	mLogButton = (Button)mView.findViewById(R.id.log_window_button);
    	mDisconnectButton = (Button)mView.findViewById(R.id.disconnect_button);

    	mLogButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Activity a = getActivity();
				a.startActivity(new Intent(a, LogWindow.class));
			}
    	});

    	mDisconnectButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				mConn.service.stopVPN();
			}
    	});

    	mConn = new VPNConnector(getActivity()) {
			@Override
			public void onUpdate(OpenVpnService service) {
				updateUI(service);
			}
    	};

    	return mView;
    }

    @Override
    public void onDestroyView() {
    	mConn.unbind();
    	super.onDestroyView();
    }

    private void updateUI(OpenVpnService service) {
		TextView tv = (TextView)mView.findViewById(R.id.status);
		int state = service.getConnectionState();

		String line = getString(R.string.netstatus, service.getConnectionStateName());
		if (state == OpenConnectManagementThread.STATE_CONNECTED) {
			line += "\n" + mConn.getByteCountSummary();
		}
		tv.setText(line);

		if (state != OpenConnectManagementThread.STATE_DISCONNECTED) {
			mDisconnectButton.setVisibility(View.VISIBLE);
		} else {
			mDisconnectButton.setVisibility(View.INVISIBLE);
		}
    }
}
