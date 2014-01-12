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

package app.openconnect.core;

import app.openconnect.R;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;

public class CertWarningDialog extends UserDialog
	implements DialogInterface.OnClickListener, DialogInterface.OnDismissListener {

	public static final int RESULT_NO = 0;
	public static final int RESULT_ONCE = 1;
	public static final int RESULT_ALWAYS = 2;

	public String mHostname;
	public String mCertSHA1;
	public String mReason;

	private int mAccept = RESULT_NO;
	private AlertDialog mAlert;

	public CertWarningDialog(SharedPreferences prefs, String hostname, String certSHA1, String reason) {
		super(prefs);
		mHostname = hostname;
		mCertSHA1 = certSHA1;
		mReason = reason;
	}

	@Override
	public Object earlyReturn() {
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
			mAccept = RESULT_ALWAYS;
		} else if (which == DialogInterface.BUTTON_NEUTRAL) {
			mAccept = RESULT_ONCE;
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
