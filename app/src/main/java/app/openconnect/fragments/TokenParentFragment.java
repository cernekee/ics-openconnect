/*
 * Copyright (c) 2014, Kevin Cernekee
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import app.openconnect.R;
import app.openconnect.VpnProfile;
import app.openconnect.core.ProfileManager;

public class TokenParentFragment extends Fragment {

	public static final String TAG = "OpenConnect";

	public static final String PREF_TOKEN_UUID = "token_uuid";

	private SharedPreferences mPrefs;
	private List<VpnProfile> mVpnProfileList;

	private boolean mFastRedraw = true;
	private int mSelected;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
    		Bundle savedInstanceState) {
    	super.onCreateView(inflater, container, savedInstanceState);

    	mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

    	View v = inflater.inflate(R.layout.token_diag_parent, container, false);
    	return v;
    }

    private void setFrag(VpnProfile v, boolean animate) {
		Fragment frag = new TokenDiagFragment();
		if (v != null) {
			Bundle b = new Bundle();
			b.putString(TokenDiagFragment.EXTRA_UUID, v.getUUIDString());
			frag.setArguments(b);
		}

		FragmentTransaction ft = getFragmentManager().beginTransaction();
		if (animate) {
			ft.setCustomAnimations(R.animator.fade_in, R.animator.fade_out);
		}
		ft.replace(R.id.frag_container, frag).commit();
    }

    private void refreshProfileSelection(int pos) {
    	VpnProfile v = null;

    	if (!mFastRedraw && pos == mSelected) {
    		return;
    	}

    	if (pos < mVpnProfileList.size()) {
    		v = mVpnProfileList.get(pos);
    	}
    	setFrag(v, !mFastRedraw);
		mFastRedraw = false;
		mSelected = pos;

		String UUID = (v == null) ? "" : v.getUUIDString();
		mPrefs.edit().putString(PREF_TOKEN_UUID, UUID).apply();
    }

    private void populateProfileList() {
		// always refresh this on resume, as the list may have changed
		List<VpnProfile> allvpn = new ArrayList<VpnProfile>(ProfileManager.getProfiles());
		Collections.sort(allvpn);

		String lastUUID = mPrefs.getString(PREF_TOKEN_UUID, null);
		int i = 0, lastIdx = 0;

    	List<String> choiceList = new ArrayList<String>();
    	mVpnProfileList = new ArrayList<VpnProfile>();

		for (VpnProfile v : allvpn) {
			if (!v.mPrefs.getString("software_token", "").equals("securid")) {
				continue;
			}
			String t = v.mPrefs.getString("token_string", "").trim();
			if (t.equals("")) {
				continue;
			}

			mVpnProfileList.add(v);
			choiceList.add(v.getName());

			if (v.getUUIDString().equals(lastUUID)) {
				lastIdx = i;
			}
			i++;
		}

		if (choiceList.size() == 0) {
			choiceList.add("-----------");
		}

    	ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(),
    			android.R.layout.simple_spinner_item, choiceList);
    	adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

    	Spinner sp = (Spinner)getActivity().findViewById(R.id.vpn_spinner);
    	sp.setAdapter(adapter);
    	sp.setSelection(lastIdx >= 0 ? lastIdx : 0);
    	sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> parent, View view,
					int position, long id) {
				refreshProfileSelection(position);
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
		});

		refreshProfileSelection(lastIdx);
    }

    @Override
    public void onResume() {
    	super.onResume();
    	mFastRedraw = true;
		populateProfileList();
    }

}