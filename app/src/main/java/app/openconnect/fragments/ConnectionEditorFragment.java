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

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import java.io.FileNotFoundException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import app.openconnect.ConnectionEditorActivity;
import app.openconnect.R;
import app.openconnect.ShowTextPreference;
import app.openconnect.TokenImportActivity;
import app.openconnect.VpnProfile;
import app.openconnect.core.ProfileManager;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import org.apache.commons.io.IOUtils;

public class ConnectionEditorFragment extends PreferenceFragment
		implements OnSharedPreferenceChangeListener {

	public static final String TAG = "OpenConnect";

	PreferenceManager mPrefs;
	VpnProfile mProfile;
	String mUUID;

    HashMap<String,Integer> fileSelectMap = new HashMap<String,Integer>();

    private final int IDX_TOKEN_STRING = 65536;

	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mProfile = ProfileManager.get(getArguments().getString("profileUUID"));
        mUUID = mProfile.getUUIDString();

        mPrefs = getPreferenceManager();
        mPrefs.setSharedPreferencesName(ProfileManager.getPrefsName(mUUID));
        mPrefs.setSharedPreferencesMode(Context.MODE_PRIVATE);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.pref_openconnect);
        setClickListeners();

        SharedPreferences sp = mPrefs.getSharedPreferences();
        for (Map.Entry<String,?> entry : sp.getAll().entrySet()) {
            updatePref(sp, entry.getKey());
        }
    }

    @Override
	public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    // FIXME: File access on the UI thread.
    private String getCertDN(String key, String filename) {
		// If this isn't a cert field, don't try to parse the file as a cert.
		if (ProfileManager.getCertMIMETypes(key).length == 0) {
			return null;
		}

		String pathname = ProfileManager.getCertPath() + filename;
		byte[] certBytes;
		try {
			InputStream is = new FileInputStream(pathname);
			certBytes = IOUtils.toByteArray(is);
			is.close();
		} catch (FileNotFoundException e) {
			// We shouldn't reach this method at all if the file is absent
			Log.e(TAG, "Error opening cert " + pathname, e);
			return null;
		} catch (IOException e) {
			Log.e(TAG, "Error reading cert " + pathname, e);
			return null;
		}

		// This tries PEM + DER first.
		try {
			CertificateFactory cf = CertificateFactory.getInstance("X.509");
			X509Certificate cert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certBytes));
			return cert.getSubjectDN().getName();
		} catch (ClassCastException | NullPointerException | CertificateException e) {
			// Ignore decoding errors
		}

		// Fall back to PKCS12.
		try {
			KeyStore p12 = KeyStore.getInstance("pkcs12");
			p12.load(new ByteArrayInputStream(certBytes), new char[0]);

			Enumeration e = p12.aliases();
			while (e.hasMoreElements()) {
				X509Certificate cert = (X509Certificate) p12.getCertificate((String) e.nextElement());
				return cert.getSubjectDN().getName();
			}
		} catch (CertificateException | NoSuchAlgorithmException | KeyStoreException | IOException e) {
			// Return null if cert can't be parsed as PKCS#12
		}

		return null;
	}

    private void updatePref(SharedPreferences sp, String key) {
        String value;
        try {
            value = sp.getString(key, "");
        } catch (ClassCastException e) {
            /* wasn't a string preference */
            return;
        }

        Preference pref = findPreference(key);
        if (pref != null) {
			if (pref instanceof ListPreference) {
				/* update all spinner prefs so the summary shows the current value */
				ListPreference lpref = (ListPreference)pref;
				lpref.setValue(value);
				pref.setSummary(lpref.getEntry());
			} else {
				/* for ShowTextPreference entries, hide the filename */
				if (fileSelectMap.containsKey(key) && !value.equals("")) {
					String s = getCertDN(key, value);
					pref.setSummary(s != null ? s : getString(R.string.stored));
				} else {
					pref.setSummary(value);
				}
			}
			if (pref instanceof EditTextPreference) {
				final EditTextPreference etpref = (EditTextPreference)pref;
				etpref.getEditText().setOnEditorActionListener(new TextView.OnEditorActionListener() {
					@Override
					public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
						if (actionId == EditorInfo.IME_ACTION_DONE ||
								(keyEvent != null &&
										keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER &&
										keyEvent.getAction() == KeyEvent.ACTION_DOWN)) {
							etpref.onClick(etpref.getDialog(), Dialog.BUTTON_POSITIVE);
							etpref.getDialog().dismiss();
							return true;
						} else {
							return false;
						}
					}
				});
			}
        }

        /* disable token_string item if the profile isn't using a software token */ 
        if (key.equals("software_token")) {
            pref = findPreference("token_string");
            if (pref != null) {
                pref.setEnabled(!value.equals("disabled"));
            }
        }

        /* similarly, if split tunnel is "auto", ignore manually entered subnets */
        if (key.equals("split_tunnel_mode")) {
            pref = findPreference("split_tunnel_networks");
            if (pref != null) {
                pref.setEnabled(!value.equals("auto"));
            }
        }

        if (key.equals("profile_name")) {
        	((ConnectionEditorActivity)getActivity()).setProfileName(value);
        }
    }

	public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
		updatePref(sp, key);
	}

	private void openFileChooser(final int idx) {
		// This could use ACTION_GET_CONTENT, but:
		// 1) It doesn't support filtering on multiple MIME types (AFAICT).
		// 2) ACTION_GET_CONTENT doesn't just show Downloads and storage; it also
		//    allows requesting files from other apps.  This makes the UI unnecessarily
		//    complicated.
		Intent startFC = new Intent();
		startFC.setAction(Intent.ACTION_OPEN_DOCUMENT);
		startFC.addCategory(Intent.CATEGORY_OPENABLE);

		String[] mimeTypes = ProfileManager.getCertMIMETypes(ProfileManager.fileSelectKeys[idx]);
		startFC.setType("*/*");
		if (mimeTypes.length > 0) {
			startFC.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
		}

		startActivityForResult(startFC, idx);
	}

	private void changeCertFile(final int idx) {
		final String key = ProfileManager.fileSelectKeys[idx];
		final SharedPreferences sp = mPrefs.getSharedPreferences();

		String currentFilename = sp.getString(key, "");
		if (currentFilename.isEmpty()) {
			openFileChooser(idx);
			return;
		}

		// Confirm overwrite (or clear)
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

		String certName = getCertDN(ProfileManager.fileSelectKeys[idx], currentFilename);
		if (certName == null) {
			builder.setMessage(R.string.replace_cert_message_generic);
		} else {
			builder.setMessage(getString(R.string.replace_cert_message, certName));
		}

		builder.setTitle(R.string.replace_cert_title)
				.setPositiveButton(R.string.replace, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						openFileChooser(idx);
					}
				})
				.setNegativeButton(R.string.clear, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						ProfileManager.deleteFilePref(mProfile, key);
						ShowTextPreference p = (ShowTextPreference)findPreference(key);
						p.setText("");
						updatePref(sp, key);
					}
				})
				.setNeutralButton(R.string.cancel, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						// no-op
					}
				})
				.show();
	}

	private void setClickListeners() {
		for (int idx = 0; idx < ProfileManager.fileSelectKeys.length; idx++) {
			String key = ProfileManager.fileSelectKeys[idx];
			Preference p = findPreference(key);
			fileSelectMap.put(key, idx);

			/* Start up a FileSelect activity to import data from the filesystem */
			p.setOnPreferenceClickListener(new OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					Integer idx = fileSelectMap.get(preference.getKey());
					if (idx != null) {
						changeCertFile(idx);
					}
					return false;
				}
			});
		}

		Preference p = findPreference("token_string");
		/* The TokenImport activity will set the token_string preference for us */
		p.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				Intent intent = new Intent(getActivity(), TokenImportActivity.class);
				intent.putExtra(TokenImportActivity.EXTRA_UUID, mUUID);
				startActivityForResult(intent, IDX_TOKEN_STRING);
				return false;
			}
		});
	}

	@Override
	public void onActivityResult(int idx, int resultCode, Intent data) {
		super.onActivityResult(idx, resultCode, data);

		if (resultCode != Activity.RESULT_OK || data == null) {
			return;
		}

		SharedPreferences prefs = mPrefs.getSharedPreferences();
		if (idx >= IDX_TOKEN_STRING) {
			updatePref(prefs, "token_string");
			updatePref(prefs, "software_token");
		} else {
			String key = ProfileManager.fileSelectKeys[idx];
			ShowTextPreference p = (ShowTextPreference)findPreference(key);

			String path = ProfileManager.storeFilePref(mProfile, key, data.getData());
			p.setText(path);
			updatePref(prefs, key);
		}
	}
}
