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

import java.text.DateFormat;
import java.util.Calendar;
import java.util.TimeZone;

import org.stoken.LibStoken;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import app.openconnect.R;
import app.openconnect.VpnProfile;
import app.openconnect.core.FragCache;
import app.openconnect.core.ProfileManager;

public class TokenDiagFragment extends Fragment {

	public static final String TAG = "OpenConnect";

	public static final String EXTRA_UUID = "app.openconnect.UUID";
	public static final String EXTRA_PIN = "app.openconnect.PIN";
	public static final String EXTRA_PIN_PROMPTED = "app.openconnect.PIN_PROMPTED";

	private LibStoken mStoken;
	private TextView mTokencode;
	private String mRawTokencode = "";
	private ProgressBar mProgressBar;
	private View mView;
	private Calendar mLastUpdate;

	private String mPin;
	private boolean mPinPrompt = false;
	private boolean mNeedsPin = false;
	private AlertDialog mDialog;

	private Handler mHandler;
	private Runnable mRunnable;

	private String mUUID;

	@Override
	public void setArguments(Bundle b) {
		mUUID = b.getString(EXTRA_UUID);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
        mStoken = new LibStoken();
        mHandler = new Handler();

        mRunnable = new Runnable() {
			@Override
			public void run() {
				updateUI();
			}
        };
	}

	private int importToken(String UUID) {
		VpnProfile vp = ProfileManager.get(mUUID);
		if (vp == null) {
			return R.string.securid_internal_error;
		}

		String tokenString = vp.mPrefs.getString("token_string", "").trim();
		if (tokenString.equals("")) {
			return R.string.securid_internal_error;
		}

		if (mStoken.importString(tokenString) != LibStoken.SUCCESS) {
			return R.string.securid_parse_error;
		}

		if (mStoken.decryptSeed(null, null) != LibStoken.SUCCESS) {
			return R.string.securid_encrypted;
		}

		mPinPrompt = mNeedsPin = mStoken.isPINRequired();
		return 0;
	}

    private void writeStatusField(int id, int header_res, String value, boolean warn) {
    	String html = "<b>" + TextUtils.htmlEncode(getString(header_res)) + "</b><br>";
    	value = TextUtils.htmlEncode(value);
    	if (warn) {
    		/*
    		 * No CSS.  See:
    		 * http://commonsware.com/blog/Android/2010/05/26/html-tags-supported-by-textview.html
    		 */
    		html += "<font color=\"red\"><b>" + value + "</b></font>";
    	} else {
    		html += value;
    	}
    	TextView tv = (TextView)mView.findViewById(id);
    	tv.setText(Html.fromHtml(html));
    }

    private void writeStatusField(int id, int header_res, String value) {
    	writeStatusField(id, header_res, value, false);
    }

	private void populateView(View v) {
		mTokencode = (TextView)v.findViewById(R.id.tokencode);
		mProgressBar = (ProgressBar)v.findViewById(R.id.progress_bar);
		mView = v;

		Button copyButton = (Button)v.findViewById(R.id.copy_button);
		copyButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Activity act = getActivity();
				ClipboardManager clipboard = (ClipboardManager)
						act.getSystemService(Context.CLIPBOARD_SERVICE);
				ClipData clip = ClipData.newPlainText("Tokencode", mRawTokencode);
				clipboard.setPrimaryClip(clip);
				Toast.makeText(act.getBaseContext(), R.string.copied_entry, Toast.LENGTH_SHORT).show();
			}
		});

		Button pinButton = (Button)v.findViewById(R.id.change_pin_button);
		pinButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				pinDialog();
			}
		});

		/* static fields */
		LibStoken.StokenInfo info = mStoken.getInfo();

		writeStatusField(R.id.token_sn, R.string.token_sn, info.serial);

		DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT);
		long exp = info.unixExpDate * 1000;

		/* show field in red if expiration is <= 2 weeks away */
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DAY_OF_MONTH, 14);
		writeStatusField(R.id.exp_date, R.string.exp_date, df.format(exp), cal.getTimeInMillis() >= exp);

		/* TODO */
		writeStatusField(R.id.dev_id, R.string.dev_id, getString(R.string.unknown));
	}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
    		Bundle savedInstanceState) {

    	View v;
    	int error = 0;

    	if (mUUID == null) {
    		error = R.string.securid_none_defined;
    	} else {
    		error = importToken(mUUID);
    	}

    	if (error != 0) {
    		v = inflater.inflate(R.layout.token_diag_error, container, false);

    		TextView tv = (TextView)v.findViewById(R.id.msg);
    		tv.setText(error);
    	} else {
        	mPin = FragCache.get(mUUID, EXTRA_PIN);
    		if ("true".equals(FragCache.get(mUUID, EXTRA_PIN_PROMPTED))) {
    			mPinPrompt = false;
    		}

    		v = inflater.inflate(R.layout.token_diag_info, container, false);
    		populateView(v);
    		setPin(mPin);
    	}

    	return v;
    }

    private String formatTokencode(String s) {
    	int midpoint = s.length() / 2;
    	return s.substring(0, midpoint) + " " + s.substring(midpoint);
    }

    private void updateUI() {
    	/* in this case we're just displaying a static error message */
    	if (mTokencode == null) {
    		return;
    	}

    	/* don't update if the PIN dialog is up */
    	if (mDialog != null) {
    		mHandler.postDelayed(mRunnable, 500);
    		return;
    	}

    	Calendar now = Calendar.getInstance();
    	mProgressBar.setProgress(59 - now.get(Calendar.SECOND));

    	if (mLastUpdate == null ||
    		now.get(Calendar.MINUTE) != mLastUpdate.get(Calendar.MINUTE)) {

			// if the library already stored a PIN, it is necessary to pass
			// in "0000" (to overwrite) instead of null (to keep the existing PIN)
    		String pin = mPin == null ? "0000" : mPin;

    		long t = now.getTimeInMillis() / 1000;
    		mRawTokencode = mStoken.computeTokencode(t, pin);
    		mTokencode.setText(formatTokencode(mRawTokencode));
    		writeStatusField(R.id.next_tokencode, R.string.next_tokencode,
    				formatTokencode(mStoken.computeTokencode(t + 60, pin)));
    	}

		DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.LONG);
		df.setTimeZone(TimeZone.getTimeZone("GMT"));
		String gmt = df.format(now.getTime()).replaceAll(" GMT.*", "");
		writeStatusField(R.id.gmt, R.string.gmt, gmt);

    	mLastUpdate = now;
    	mHandler.postDelayed(mRunnable, 500);
    }

    private void setPin(String s) {
    	int res;
    	boolean warn = false;

    	mPin = s;
		if (!mNeedsPin) {
			res = R.string.not_required;
		} else if (s == null || !mStoken.checkPIN(s)) {
			mPin = null;
			warn = true;
			res = R.string.no;
		} else {
			res = R.string.yes;
		}

		writeStatusField(R.id.using_pin, R.string.using_pin, getString(res), warn);
		FragCache.put(mUUID, EXTRA_PIN, mPin);
		mLastUpdate = null;
    }

    private void pinDialog() {
    	Context ctx = getActivity();

    	if (mDialog != null) {
    		return;
    	}

    	final TextView tv = new EditText(ctx);
		tv.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_TEXT_VARIATION_PASSWORD);
		tv.setTransformationMethod(PasswordTransformationMethod.getInstance());

    	AlertDialog.Builder builder = new AlertDialog.Builder(ctx)
    		.setView(tv)
    		.setTitle(R.string.enter_pin)
    		.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface arg0, int arg1) {
					setPin(tv.getText().toString().trim());
					FragCache.put(mUUID, EXTRA_PIN_PROMPTED, "true");
				}
    		})
    		.setNegativeButton(R.string.skip, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface arg0, int arg1) {
					setPin(null);
					FragCache.put(mUUID, EXTRA_PIN_PROMPTED, "true");
				}
    		});
    	mDialog = builder.create();
    	mDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
			@Override
			public void onDismiss(DialogInterface dialog) {
				mPinPrompt = false;
				mDialog = null;
			}
    	});
    	mDialog.show();

    	/* gray out the OK button until a sane-looking PIN is entered */
		final Button okButton = mDialog.getButton(AlertDialog.BUTTON_POSITIVE);
		okButton.setEnabled(false);

		tv.addTextChangedListener(new TextWatcher() {
			@Override
			public void afterTextChanged(Editable arg0) {
				String s = tv.getText().toString();
				okButton.setEnabled(mStoken.checkPIN(s));
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}
		});
    }

    @Override
    public void onResume() {
    	super.onResume();
    	mLastUpdate = null;
    	mHandler.post(mRunnable);

    	if (mPinPrompt) {
    		pinDialog();
    	}
    }

    @Override
    public void onPause() {
    	super.onPause();
    	mHandler.removeCallbacks(mRunnable);

    	if (mDialog != null) {
    		mDialog.dismiss();
    		mDialog = null;
    	}
    }

    @Override
    public void onDestroy() {
    	super.onDestroy();
    	mStoken.destroy();
    }

}
