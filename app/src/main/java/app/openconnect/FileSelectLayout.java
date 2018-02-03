/*
 * Adapted from OpenVPN for Android
 * Copyright (c) 2012-2013, Arne Schwabe
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

import app.openconnect.core.X509Utils;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;


public class FileSelectLayout extends LinearLayout implements OnClickListener {

    private final boolean mIsCertificate;
    private TextView mDataView;
	private String mData;
	private Fragment mFragment;
	private int mTaskId;
	private Button mSelectButton;
	private boolean mBase64Encode;
	private String mTitle;
	private boolean mShowClear;
	private TextView mDataDetails;

	public FileSelectLayout( Context context, AttributeSet attrset) {
		super(context,attrset);
		inflate(getContext(), R.layout.file_select, this);

		TypedArray ta = context.obtainStyledAttributes(attrset, R.styleable.FileSelectLayout);

		mTitle = ta.getString(R.styleable.FileSelectLayout_title);
        mIsCertificate = ta.getBoolean(R.styleable.FileSelectLayout_certificate,true);

		TextView tview = (TextView) findViewById(R.id.file_title);
		tview.setText(mTitle);

		mDataView = (TextView) findViewById(R.id.file_selected_item);
		mDataDetails = (TextView) findViewById(R.id.file_selected_description);
		mSelectButton = (Button) findViewById(R.id.file_select_button);
		mSelectButton.setOnClickListener(this);

		ta.recycle();
	}

	public void setFragment(Fragment fragment, int i)
	{
		mTaskId = i;
		mFragment = fragment;
	}

	public void getCertificateFileDialog() {
		Intent startFC = new Intent(getContext(),FileSelect.class);
		startFC.putExtra(FileSelect.START_DATA, mData);
		startFC.putExtra(FileSelect.WINDOW_TITLE,mTitle);
		if(mBase64Encode)
			startFC.putExtra(FileSelect.DO_BASE64_ENCODE, true);
		if(mShowClear)
			startFC.putExtra(FileSelect.SHOW_CLEAR_BUTTON, true);
		mFragment.startActivityForResult(startFC,mTaskId);
	}


	public String getData() {
		return mData;
	}

	public void setData(String data, Context c) {
		mData = data;
		if(data==null) { 
			mDataView.setText(mFragment.getString(R.string.no_data));
			mDataDetails.setText("");
		}else {
			if(mData.startsWith(VpnProfile.INLINE_TAG))
				mDataView.setText(R.string.inline_file_data);
			else
				mDataView.setText(data);
            if(mIsCertificate)
			    mDataDetails.setText(X509Utils.getCertificateFriendlyName(c,data));
		}

	}

	@Override
	public void onClick(View v) {
		if(v == mSelectButton) {
			getCertificateFileDialog();
		}
	}

	public void setBase64Encode() {
		mBase64Encode =true;
	}

	public void setShowClear() {
		mShowClear=true;
	}

}
