package app.openconnect.core;

import app.openconnect.R;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;

public class CertWarningDialog extends UserDialog
	implements DialogInterface.OnClickListener, DialogInterface.OnDismissListener {

	public String mHostname;
	public String mCertSHA1;
	public String mReason;

	private boolean mAccept;
	private AlertDialog mAlert;

	public CertWarningDialog(SharedPreferences prefs, String hostname, String certSHA1, String reason) {
		super(prefs);
		mHostname = hostname;
		mCertSHA1 = certSHA1;
		mReason = reason;
	}

	@Override
	public Object earlyReturn() {
		String goodSHA1 = getStringPref("accepted_cert_sha1");
		if (mCertSHA1.equals(goodSHA1)) {
			return true;
		}
		return null;
	}

	@Override
	public void onStart(Context context) {
		super.onStart(context);
		mAlert = new AlertDialog.Builder(context)
			.setTitle(R.string.cert_warning_title)
			.setMessage(context.getString(R.string.cert_warning_message,
					mHostname, mReason, mCertSHA1))
			.setPositiveButton(R.string.cert_warning_always_connect, this)
			.setNeutralButton(R.string.cert_warning_just_once, this)
			.setNegativeButton(R.string.no, this)
			.create();
		mAlert.setOnDismissListener(this);
		mAlert.show();
	}

	@Override
	public void onDismiss(DialogInterface dialog) {
		// catches Pos/Neg/Neutral and Back button presses
		finish(mAccept);
		mAlert = null;
	}

	@Override
	public void onClick(DialogInterface dialog, int which) {
		if (which == DialogInterface.BUTTON_POSITIVE) {
			mAccept = true;
			setStringPref("accepted_cert_sha1", mCertSHA1);
		} else if (which == DialogInterface.BUTTON_NEUTRAL) {
			mAccept = true;
		}
	}

	@Override
	public void onStop(Context context) {
		super.onStop(context);
		finish(null);
		if (mAlert != null) {
			mAlert.dismiss();
		}
	}
}
