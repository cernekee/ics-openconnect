package app.openconnect;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import app.openconnect.core.ProfileManager;

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

	private String mUUID;
	private int mAlertType = ALERT_NONE;
	private int mScreen = SCREEN_UNKNOWN;

	private AlertDialog mAlert;
	private boolean mIsSecurid = true;
	private VpnProfile mProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        mUUID = intent.getStringExtra(EXTRA_UUID);

        // We will have a URI string iff the user clicked on a recognized link from another
        // application, e.g. http://127.0.0.1/securid/ctf?ctfData=279158828...
        Uri URI = intent.getData();
        String intentImport = null;
        if (URI != null) {
        	intentImport = URI.toString();
        }

        if (savedInstanceState != null) {
        	mTokenString = savedInstanceState.getString("mTokenString", null);
        	mTokenDevID = savedInstanceState.getString("mTokenDevID", null);
        	mTokenPassword = savedInstanceState.getString("mTokenPassword", null);

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
        	if (intentImport != null) {
        		mTokenString = intentImport;
        		validateToken();
        		return;
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
				startFC.putExtra(FileSelect.FORCE_INLINE_SELECTION, true);
				startActivityForResult(startFC, 0);
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

    	if (!tokenNeedsDevID()) {
    		((View)findViewById(R.id.token_need_devid)).setVisibility(View.GONE);
    		((View)findViewById(R.id.token_devid_entry)).setVisibility(View.GONE);
    		((View)findViewById(R.id.token_devid_space)).setVisibility(View.GONE);
    	}
    	if (!tokenNeedsPassword()) {
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

    private void setupSelectProfileScreen() {
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

    /* Called if we used FileSelect to import a token from a file */
	@Override
	public void onActivityResult(int idx, int resultCode, Intent data) {
		super.onActivityResult(idx, resultCode, data);
		if (resultCode == Activity.RESULT_OK) {
			String s = data.getStringExtra(FileSelect.RESULT_DATA);
			StringBuilder out = new StringBuilder();
			if (s.startsWith("[[INLINE]]")) {
				/*
				 * Sanitize the input data to avoid messing up the UI too much, if the user
				 * imports junk files
				 */
				for (int i = 10; i < s.length(); i++) {
					char c = s.charAt(i);
					if (c >= 32 && c <= 126 && i < 128) {
						out.append(c);
					}
				}
				mTokenString = out.toString();
			}
		}
		updateScreen(SCREEN_ENTER_TOKEN);
	}

    private void cancel() {
		setResult(RESULT_CANCELED);
		finish();
    }

    private void writeAndExit() {
    	mProfile.mPrefs.edit().putString("token_string", mTokenString).commit();
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

    private void validateToken() {
    	// No validation on TOTP tokens
    	if (!mIsSecurid) {
    		saveToken();
    		return;
    	}

    	if (!isTokenStringValid()) {
    		updateScreen(SCREEN_ENTER_TOKEN);
    		setAlert(ALERT_BAD_TOKEN);
    	} else {
    		if (tokenNeedsDevID() || tokenNeedsPassword()) {
    			updateScreen(SCREEN_UNLOCK_TOKEN);
    		} else {
    			saveToken();
    		}
    	}
    }

    private void decryptToken() {
    	if (!isTokenDevIDValid()) {
    		setAlert(ALERT_BAD_DEVID);
    	} else if (!isTokenPasswordValid()) {
    		setAlert(ALERT_BAD_PASSWORD);
    	} else {
    		saveToken();
    	}
    }

    private boolean isTokenDevIDValid() {
    	// FIXME
    	return true;
    }

    private boolean isTokenPasswordValid() {
    	// FIXME
    	return true;
    }

    private boolean isTokenStringValid() {
    	// FIXME
    	return mTokenString.length() > 2;
    }

    private boolean tokenNeedsDevID() {
    	// FIXME
    	return false;
    }

    private boolean tokenNeedsPassword() {
    	// FIXME
    	return false;
    }

    private void fetchFormEntries() {
    	if (mScreen == SCREEN_ENTER_TOKEN) {
    		mTokenString = ((EditText)findViewById(R.id.token_string_entry)).getText().toString();
    	} else if (mScreen == SCREEN_UNLOCK_TOKEN) {
    		mTokenDevID = ((EditText)findViewById(R.id.token_devid_entry)).getText().toString();
    		mTokenPassword = ((EditText)findViewById(R.id.token_password_entry)).getText().toString();
    	}
    }

    @Override
    protected void onSaveInstanceState(Bundle b) {
    	super.onSaveInstanceState(b);

    	fetchFormEntries();
    	b.putString("mTokenString", mTokenString);
    	b.putString("mTokenDevID", mTokenDevID);
    	b.putString("mTokenPassword", mTokenPassword);

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

    protected void onDestroy() {
    	super.onDestroy();
    	setAlert(ALERT_NONE);
    }
}
