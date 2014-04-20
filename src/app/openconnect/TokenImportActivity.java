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

package app.openconnect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import app.openconnect.core.AssetExtractor;
import app.openconnect.core.ProfileManager;
import org.stoken.LibStoken;

public class TokenImportActivity extends Activity {

	public static final String TAG = "OpenConnect";

	public static final String EXTRA_UUID = "app.openconnect.UUID";

	private static final int ALERT_NONE = 0;
	private static final int ALERT_BAD_TOKEN = 1;
	private static final int ALERT_BAD_DEVID = 2;
	private static final int ALERT_BAD_PASSWORD = 3;

	private static final int SCREEN_UNKNOWN = 0;
	private static final int SCREEN_ENTER_TOKEN = 1;
	private static final int SCREEN_UNLOCK_TOKEN = 2;
	private static final int SCREEN_SELECT_PROFILE = 3;

	private String mTokenString;
	private String mTokenDevID;
	private String mTokenPassword;
	private String mNewVpnHostname;

	private String mUUID;
	private int mAlertType = ALERT_NONE;
	private int mScreen = SCREEN_UNKNOWN;

	private AlertDialog mAlert;
	private boolean mIsSecurid = true;
	private VpnProfile mProfile;
	private LibStoken mStoken;
	private List<VpnProfile> mVpnProfileList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mStoken = new LibStoken();

        Intent intent = getIntent();
        mUUID = intent.getStringExtra(EXTRA_UUID);

        if (savedInstanceState != null) {
        	mTokenString = savedInstanceState.getString("mTokenString", null);
        	mTokenDevID = savedInstanceState.getString("mTokenDevID", null);
        	mTokenPassword = savedInstanceState.getString("mTokenPassword", null);
        	mNewVpnHostname = savedInstanceState.getString("mNewVpnHostname", null);

        	mUUID = savedInstanceState.getString("mUUID", null);
        	mAlertType = savedInstanceState.getInt("mAlertType", ALERT_NONE);
        	mScreen = savedInstanceState.getInt("mScreen", SCREEN_UNKNOWN);
        }
        if (mUUID != null) {
    		mProfile = ProfileManager.get(mUUID);
    		if (mProfile != null) {
    			if (!mProfile.mPrefs.getString("software_token", "").equals("securid")) {
    				mIsSecurid = false;
    			}
    		}
        }

        if (mScreen == SCREEN_UNKNOWN) {
        	// initial entry into TokenImportActivity - figure out how we were called

            Uri URI = intent.getData();
        	if (URI != null) {
        		if (URI.getScheme().equals("file")) {
        			// User clicked on an sdtid file
        			readFromFile(URI.getPath());
        			return;
        		} else {
                    // We will have a URI string iff the user clicked on a recognized link from another
                    // application, e.g. http://127.0.0.1/securid/ctf?ctfData=279158828...
	        		mTokenString = URI.toString();
	        		validateToken();
	        		return;
        		}
        	} else {
        		if (mProfile != null) {
        			mTokenString = mProfile.mPrefs.getString("token_string", "");
        			updateScreen(SCREEN_ENTER_TOKEN);
        			return;
        		}
        	}
            Log.e(TAG, "TokenImportActivity: not enough info to start up");
            cancel();
        } else {
        	updateScreen(mScreen);
        	setAlert(mAlertType);
        }
    }

    private void setupCommonButtons(boolean allowCancel, boolean isLast, View.OnClickListener nextAction) {
    	if (allowCancel) {
			((Button)findViewById(R.id.cancel_button)).setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					cancel();
				}
			});
    	}
    	Button next = ((Button)findViewById(R.id.next_button));
    	next.setOnClickListener(nextAction);
    	if (isLast) {
    		next.setText(R.string.finish);
    	}
    }

    private void setupEnterTokenScreen() {
		setContentView(R.layout.token_string);

		((EditText)findViewById(R.id.token_string_entry)).setText(mTokenString);

		if (!mIsSecurid) {
			int resList[] = { R.id.token_string_examples_0, R.id.token_string_examples_1,
							  R.id.token_string_examples_2, R.id.token_string_examples_3 };
			for (int r : resList) {
				((View)findViewById(r)).setVisibility(View.GONE);
			}
		}

		((Button)findViewById(R.id.token_string_import)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent startFC = new Intent(TokenImportActivity.this, FileSelect.class);
				startFC.putExtra(FileSelect.START_DATA, Environment.getExternalStorageDirectory().getPath());
				startFC.putExtra(FileSelect.NO_INLINE_SELECTION, true);
				startActivityForResult(startFC, 0);
			}
		});

		((Button)findViewById(R.id.token_string_clear)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				((EditText)findViewById(R.id.token_string_entry)).setText("");
			}
		});

		setupCommonButtons(true, !mIsSecurid, new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				fetchFormEntries();
				validateToken();
			}
		});
    }

    private void setupUnlockTokenScreen() {
    	setContentView(R.layout.token_unlock);

    	if (mStoken.importString(mTokenString) != LibStoken.SUCCESS) {
    		// should never happen, as it passed in the previous screen
    		Log.e(TAG, "error processing previously-valid token string");
    		saveToken();
    		return;
    	}
    	if (!mStoken.isDevIDRequired()) {
    		((View)findViewById(R.id.token_need_devid)).setVisibility(View.GONE);
    		((View)findViewById(R.id.token_devid_entry)).setVisibility(View.GONE);
    		((View)findViewById(R.id.token_devid_space)).setVisibility(View.GONE);
    	}
    	if (!mStoken.isPassRequired()) {
    		((View)findViewById(R.id.token_need_password)).setVisibility(View.GONE);
    		((View)findViewById(R.id.token_password_entry)).setVisibility(View.GONE);
    	}

		setupCommonButtons(true, mProfile != null, new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				fetchFormEntries();
				decryptToken();
			}
		});
    }

    private void refreshProfileSelection(boolean updateText) {
    	boolean allowFinish = false;
    	boolean enableEntry = false;
    	int numProfiles = mVpnProfileList.size();

		EditText entry = (EditText)findViewById(R.id.new_vpn_hostname);
		TextView entryLabel = (TextView)findViewById(R.id.new_vpn_label);

    	Spinner sp = (Spinner)findViewById(R.id.vpn_spinner);
    	long id = sp.getSelectedItemId();

    	mUUID = null;
    	if (id != AdapterView.INVALID_ROW_ID) {
    		if (id < numProfiles) {
    			allowFinish = true;
    			mProfile = mVpnProfileList.get((int)id);
    			mUUID = mProfile.getUUIDString();
    		} else if (id == numProfiles) {
    			enableEntry = true;
    			if (!entry.getText().toString().equals("")) {
    				allowFinish = true;
    			}
    		}
    	}

    	Button b = (Button)findViewById(R.id.next_button);
    	b.setEnabled(allowFinish);

    	int visibility = enableEntry ? View.VISIBLE : View.INVISIBLE;
    	entryLabel.setVisibility(visibility);
    	entry.setVisibility(visibility);
    	if (!enableEntry && updateText) {
    		// This is safe to call from the spinner callbacks but not from TextWatcher
    		entry.setText("");
    	}
    }

    private void setupProfileSpinner() {
    	mVpnProfileList = new ArrayList<VpnProfile>(ProfileManager.getProfiles());
    	Collections.sort(mVpnProfileList);

    	List<String> choiceList = new ArrayList<String>();
    	for (VpnProfile v : mVpnProfileList) {
    		choiceList.add(v.getName());
    	}
    	choiceList.add(getString(R.string.new_vpn_option));

    	ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
    			android.R.layout.simple_spinner_item, choiceList);
    	adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

    	Spinner sp = (Spinner)findViewById(R.id.vpn_spinner);
    	sp.setAdapter(adapter);
    	sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> parent, View view,
					int position, long id) {
				refreshProfileSelection(true);
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
				refreshProfileSelection(true);
			}
		});

    	int position = 0;
    	if (mNewVpnHostname != null) {
    		// the very last entry in the spinner is "Add new VPN profile..."
    		position = mVpnProfileList.size();
    	} else if (mUUID != null) {
    		for (int i = 0; i < mVpnProfileList.size(); i++) {
    			if (mVpnProfileList.get(i).getName().equals(mUUID)) {
    				position = i;
    			}
    		}
    	}
    	sp.setSelection(position);

    	// Update "Finish" button status when chars are added/deleted
    	EditText entry = (EditText)findViewById(R.id.new_vpn_hostname);
		entry.addTextChangedListener(new TextWatcher() {
			@Override
			public void afterTextChanged(Editable arg0) {
				refreshProfileSelection(false);
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}
		});
    }

    private void setupSelectProfileScreen() {
    	setContentView(R.layout.token_profile);

    	if (mStoken.importString(mTokenString) != LibStoken.SUCCESS ||
    	    mStoken.decryptSeed(mTokenPassword, mTokenDevID) != LibStoken.SUCCESS) {
    		// should never happen, as it passed in the previous screen
    		Log.e(TAG, "error processing previously-valid token string");
    		cancel();
    		return;
    	}

    	// store the ctf in "canonical" format, throwing away any Android/iPhone/... URI prefixes
		mTokenString = mStoken.encryptSeed(null, null);

    	setupProfileSpinner();
    	refreshProfileSelection(true);

		setupCommonButtons(true, true, new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				fetchFormEntries();
				if (mUUID == null) {
					mProfile = ProfileManager.create(mNewVpnHostname);
				}
				mProfile.mPrefs.edit().putString("software_token", "securid").commit();
				writeAndExit();
			}
		});
    }

    private void updateScreen(int newScreen) {
    	mScreen = newScreen;
    	if (mScreen == SCREEN_ENTER_TOKEN) {
    		setupEnterTokenScreen();
    	} else if (mScreen == SCREEN_UNLOCK_TOKEN) {
    		setupUnlockTokenScreen();
    	} else if (mScreen == SCREEN_SELECT_PROFILE) {
    		setupSelectProfileScreen();
    	}
    }

    private boolean readFromFile(String filename) {
		StringBuilder out = new StringBuilder();

		String s = AssetExtractor.readStringFromFile(filename);
		if (s == null || s.length() == 0) {
			return false;
		}

		s = s.trim();
		if (s.startsWith("<?xml ")) {
			mTokenString = s;
			if (validateToken() == false) {
				/* go back to square one */
				mTokenString = null;
				return false;
			}
			return true;
		}

		/*
		 * Sanitize the input data to avoid messing up the UI too much, if the user
		 * imports junk files
		 */
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c >= 32 && c <= 126 && i < 128) {
				out.append(c);
			}
		}
		mTokenString = out.toString();
		updateScreen(SCREEN_ENTER_TOKEN);
		return true;
    }

    /* Called if we used FileSelect to import a token from a file */
	@Override
	public void onActivityResult(int idx, int resultCode, Intent data) {
		super.onActivityResult(idx, resultCode, data);
		if (resultCode == Activity.RESULT_OK) {
			readFromFile(data.getStringExtra(FileSelect.RESULT_DATA));
			return;
		}
		/* User canceled */
		updateScreen(SCREEN_ENTER_TOKEN);
	}

    private void cancel() {
		setResult(RESULT_CANCELED);
		finish();
    }

    private void writeAndExit() {
    	boolean wasEmpty = mProfile.mPrefs.getString("token_string", "").equals("");

    	mProfile.mPrefs.edit().putString("token_string", mTokenString).commit();
    	if (mTokenString.equals("") && !wasEmpty) {
    		mProfile.mPrefs.edit().putString("software_token", "disabled").commit();
    	}
    	setResult(RESULT_OK);
    	finish();
    }

    private void saveToken() {
		if (mProfile == null) {
			updateScreen(SCREEN_SELECT_PROFILE);
		} else {
			writeAndExit();
		}
    }

    private boolean validateToken() {
    	// No validation on TOTP tokens
    	if (!mIsSecurid || (mProfile != null && mTokenString.equals(""))) {
    		saveToken();
    		return true;
    	}

    	if (mStoken.importString(mTokenString) != LibStoken.SUCCESS) {
			Log.i(TAG, "rejecting invalid token string");
    		updateScreen(SCREEN_ENTER_TOKEN);
    		setAlert(ALERT_BAD_TOKEN);
    		return false;
    	}
		if (mStoken.isDevIDRequired() || mStoken.isPassRequired()) {
			updateScreen(SCREEN_UNLOCK_TOKEN);
		} else {
			if (mStoken.decryptSeed(null, null) == LibStoken.SUCCESS) {
				Log.i(TAG, "token seed was successfully decrypted");
				mTokenString = mStoken.encryptSeed(null, null);
				saveToken();
			} else {
				Log.w(TAG, "error processing NON-encrypted seed");
	    		updateScreen(SCREEN_ENTER_TOKEN);
	    		setAlert(ALERT_BAD_TOKEN);
	    		return false;
			}
		}
		return true;
    }

    private void decryptToken() {
    	if (mStoken.decryptSeed(mTokenPassword, mTokenDevID) == LibStoken.SUCCESS) {
    		Log.i(TAG, "storing decrypted token seed");
    		mTokenString = mStoken.encryptSeed(null, null);
    		saveToken();
    		return;
    	}

    	if (mStoken.isDevIDRequired() && !mStoken.checkDevID(mTokenDevID)) {
    		setAlert(ALERT_BAD_DEVID);
    	} else if (mStoken.isPassRequired()) {
    		// NOTE: Under some circumstances, checkDevID() can return true but we can still
    		// get a decryption failure due to a DevID mismatch.  Maybe the error messages
    		// should be changed.
    		setAlert(ALERT_BAD_PASSWORD);
    	} else {
    		setAlert(ALERT_BAD_TOKEN);
    	}
    }

    private void fetchFormEntries() {
    	if (mScreen == SCREEN_ENTER_TOKEN) {
    		mTokenString = ((EditText)findViewById(R.id.token_string_entry)).getText().toString().trim();
    	} else if (mScreen == SCREEN_UNLOCK_TOKEN) {
    		mTokenDevID = ((EditText)findViewById(R.id.token_devid_entry)).getText().toString().trim();
    		mTokenPassword = ((EditText)findViewById(R.id.token_password_entry)).getText().toString().trim();
    	} else if (mScreen == SCREEN_SELECT_PROFILE) {
    		// don't save/restore mNewVpnHostname unless "Add new VPN profile..." is the active selection
    		if (mUUID != null) {
    			mNewVpnHostname = null;
    		} else {
    			mNewVpnHostname = ((EditText)findViewById(R.id.new_vpn_hostname)).getText().toString().trim();
    		}
    	}
    }

    @Override
    protected void onSaveInstanceState(Bundle b) {
    	super.onSaveInstanceState(b);

    	fetchFormEntries();
    	b.putString("mTokenString", mTokenString);
    	b.putString("mTokenDevID", mTokenDevID);
    	b.putString("mTokenPassword", mTokenPassword);
    	b.putString("mNewVpnHostname", mNewVpnHostname);

    	b.putString("mUUID", mUUID);
    	b.putInt("mAlertType", mAlertType);
    	b.putInt("mScreen", mScreen);
    }

    private void setAlert(int newAlert) {
    	mAlertType = newAlert;
    	if (newAlert == ALERT_NONE) {
    		if (mAlert != null) {
    			mAlert.dismiss();
    			mAlert = null;
    		}
    		return;
    	}

    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	if (newAlert == ALERT_BAD_TOKEN) {
    		builder.setTitle(R.string.token_bad_string_title);
    		builder.setMessage(R.string.token_bad_string_summary);
    	} else if (newAlert == ALERT_BAD_DEVID) {
    		builder.setTitle(R.string.token_bad_devid_title);
    		builder.setMessage(R.string.token_bad_devid_summary);
    	} else if (newAlert == ALERT_BAD_PASSWORD) {
    		builder.setTitle(R.string.token_bad_password_title);
    		builder.setMessage(R.string.token_bad_password_summary);
    	}

    	builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface arg0, int arg1) {
				setAlert(ALERT_NONE);
			}
    	});
    	mAlert = builder.show();
    }

    @Override
    protected void onDestroy() {
    	super.onDestroy();
    	setAlert(ALERT_NONE);
    	mStoken.destroy();
    }
}
