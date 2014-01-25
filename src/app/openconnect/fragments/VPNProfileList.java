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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.app.AlertDialog;
import android.app.ListFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.Html;
import android.text.Html.ImageGetter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import app.openconnect.ConnectionEditorActivity;
import app.openconnect.R;
import app.openconnect.VpnProfile;
import app.openconnect.api.GrantPermissionsActivity;
import app.openconnect.core.ProfileManager;

public class VPNProfileList extends ListFragment {

	private static final int MENU_ADD_PROFILE = Menu.FIRST;

	private ArrayAdapter<VpnProfile> mArrayadapter;

	private AlertDialog mDialog;
	private EditText mDialogEntry;

	private class VPNArrayAdapter extends ArrayAdapter<VpnProfile> {

		public VPNArrayAdapter(Context context, int resource, int textViewResourceId) {
			super(context, resource, textViewResourceId);
		}

		@Override
		public View getView(final int position, View convertView, ViewGroup parent) {
			View v = super.getView(position, convertView, parent);

			View titleview = v.findViewById(R.id.vpn_list_item_left);
			titleview.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					VpnProfile profile =(VpnProfile) getListAdapter().getItem(position);
					startVPN(profile);
				}
			});

			View settingsview = v.findViewById(R.id.quickedit_settings);
			settingsview.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					VpnProfile editProfile = (VpnProfile) getListAdapter().getItem(position);
					editVPN(editProfile);
				}
			});

			return v;
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);

		if (savedInstanceState != null) {
			String s = savedInstanceState.getString("mDialogEntry");
			if (s != null) {
				onAddProfileClicked(s);
			}
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (mDialog != null) {
			mDialog.dismiss();
		}
	}

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		super.onSaveInstanceState(savedInstanceState);

		if (mDialog != null) {
			savedInstanceState.putString("mDialogEntry", mDialogEntry.getText().toString());
		}
	}

	class MiniImageGetter implements ImageGetter {
		@Override
		public Drawable getDrawable(String source) {
			Drawable d = null;
			if ("ic_menu_add".equals(source))
				d = getActivity().getResources().getDrawable(android.R.drawable.ic_menu_add);
			else if("ic_menu_archive".equals(source))
				d = getActivity().getResources().getDrawable(R.drawable.ic_menu_archive);

			if (d != null) {
				d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
				return d;
			} else {
				return null;
			}
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.vpn_profile_list, container,false);

		TextView newvpntext = (TextView) v.findViewById(R.id.add_new_vpn_hint);
		newvpntext.setText(Html.fromHtml(getString(R.string.add_new_vpn_hint),new MiniImageGetter(),null));

		mArrayadapter = new VPNArrayAdapter(getActivity(), R.layout.vpn_list_item, R.id.vpn_item_title);
		setListAdapter(mArrayadapter);

		return v;
	}

	class VpnProfileNameComperator implements Comparator<VpnProfile> {

		@Override
		public int compare(VpnProfile lhs, VpnProfile rhs) {
			return lhs.mName.compareTo(rhs.mName);
		}

	}

	@Override
	public void onResume() {
		super.onResume();

		// always refresh this on resume, as the list may have changed
		List<VpnProfile> allvpn = new ArrayList<VpnProfile>(ProfileManager.getProfiles());
		Collections.sort(allvpn);

		mArrayadapter.clear();
		mArrayadapter.addAll(allvpn);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		menu.add(0, MENU_ADD_PROFILE, 0 , R.string.menu_add_profile)
		.setIcon(android.R.drawable.ic_menu_add)
		.setAlphabeticShortcut('a')
		.setTitleCondensed(getActivity().getString(R.string.add))
		.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS |  MenuItem.SHOW_AS_ACTION_WITH_TEXT);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		final int itemId = item.getItemId();
		if (itemId == MENU_ADD_PROFILE) {
			onAddProfileClicked("");
			return true;
		} else {
			return super.onOptionsItemSelected(item);
		}
	}

	private void onAddProfileClicked(String savedEntry) {
		Context context = getActivity();
		if (context != null) {
			mDialogEntry = new EditText(context);
			mDialogEntry.setSingleLine();
			mDialogEntry.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
			mDialogEntry.setText(savedEntry);

			AlertDialog.Builder builder = new AlertDialog.Builder(context);
			builder.setTitle(R.string.menu_add_profile);
			builder.setMessage(R.string.add_profile_hostname_prompt);
			builder.setView(mDialogEntry);

			builder.setPositiveButton(android.R.string.ok,
					new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					editVPN(ProfileManager.create(mDialogEntry.getText().toString()));
				}
			});
			builder.setNegativeButton(android.R.string.cancel, null);

			mDialog = builder.create();

			mDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
				@Override
				public void onDismiss(DialogInterface dialog) {
					mDialog = null;
				}
			});

			mDialog.show();

			// Block user from entering an empty hostname

			final Button okButton = mDialog.getButton(AlertDialog.BUTTON_POSITIVE);
			okButton.setEnabled(!savedEntry.equals(""));

			mDialogEntry.addTextChangedListener(new TextWatcher() {
				@Override
				public void afterTextChanged(Editable arg0) {
					okButton.setEnabled(mDialogEntry.getText().length() != 0);
				}

				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {
				}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {
				}
			});
		}

	}

	private void editVPN(VpnProfile profile) {
		String pfx = getActivity().getPackageName();
		Intent vprefintent = new Intent(getActivity(), ConnectionEditorActivity.class)
			.putExtra(pfx + ".profileUUID", profile.getUUID().toString())
			.putExtra(pfx + ".profileName", profile.getName());

		startActivity(vprefintent);
	}

	private void startVPN(VpnProfile profile) {
		Intent intent = new Intent(getActivity(), GrantPermissionsActivity.class);
		String pkg = getActivity().getPackageName();

		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
		if (sp.getBoolean("showlogwindow", false)) {
			intent.putExtra(pkg + GrantPermissionsActivity.EXTRA_START_ACTIVITY, pkg + ".LogWindow");
		}

		intent.putExtra(pkg + GrantPermissionsActivity.EXTRA_UUID, profile.getUUID().toString());
		intent.setAction(Intent.ACTION_MAIN);
		startActivity(intent);
	}
}
