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


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import app.openconnect.fragments.FileSelectionFragment;
import app.openconnect.fragments.InlineFileTab;

public class FileSelect extends Activity {
	public static final String RESULT_DATA = "RESULT_PATH";
	public static final String START_DATA = "START_DATA";
	public static final String WINDOW_TITLE = "WINDOW_TILE";
	public static final String NO_INLINE_SELECTION = "app.openconnect.NO_INLINE_SELECTION";
	public static final String FORCE_INLINE_SELECTION = "app.openconnect.FORCE_INLINE_SELECTION";
	public static final String SHOW_CLEAR_BUTTON = "app.openconnect.SHOW_CLEAR_BUTTON";
	public static final String DO_BASE64_ENCODE = "app.openconnect.BASE64ENCODE";

	private static final int MAX_FILE_LEN = 32768;

	private FileSelectionFragment mFSFragment;
	private InlineFileTab mInlineFragment;
	private String mData;
	private Tab inlineFileTab;
	private Tab fileExplorerTab;
	private boolean mNoInline;
	private boolean mForceInline;
	private boolean mShowClear;
	private boolean mBase64Encode;
	
		
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState); 
		setContentView(R.layout.file_dialog);

		mData = getIntent().getStringExtra(START_DATA);
		if(mData==null)
			mData=Environment.getExternalStorageDirectory().getPath();
		
		String title = getIntent().getStringExtra(WINDOW_TITLE);
		int titleId = getIntent().getIntExtra(WINDOW_TITLE, 0);
		if(titleId!=0) 
			title =getString(titleId);
		if(title!=null)
			setTitle(title);
		
		mNoInline = getIntent().getBooleanExtra(NO_INLINE_SELECTION, false);
		mForceInline = getIntent().getBooleanExtra(FORCE_INLINE_SELECTION, false);
		mShowClear = getIntent().getBooleanExtra(SHOW_CLEAR_BUTTON, false);
		mBase64Encode = getIntent().getBooleanExtra(DO_BASE64_ENCODE, false);
		
		ActionBar bar = getActionBar();
		bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS); 
		fileExplorerTab = bar.newTab().setText(R.string.file_explorer_tab);
		inlineFileTab = bar.newTab().setText(R.string.inline_file_tab); 

		mFSFragment = new FileSelectionFragment();
		fileExplorerTab.setTabListener(new MyTabsListener<FileSelectionFragment>(this, mFSFragment));
		bar.addTab(fileExplorerTab);
		
		if(!mNoInline) {
			mInlineFragment = new InlineFileTab();
			inlineFileTab.setTabListener(new MyTabsListener<InlineFileTab>(this, mInlineFragment));
			bar.addTab(inlineFileTab);
			if (mForceInline) {
				mFSFragment.setForceInLine();
			}
		} else {
			mFSFragment.setNoInLine();
		}

		
	}
	
	public boolean showClear() {
		if(mData == null || mData.equals(""))
			return false;
		else
			return mShowClear;
	}

	protected class MyTabsListener<T extends Fragment> implements ActionBar.TabListener
	{
		private Fragment mFragment;
		private boolean mAdded=false;

		public MyTabsListener( Activity activity, Fragment fragment){
			this.mFragment = fragment;
		}

		public void onTabSelected(Tab tab, FragmentTransaction ft) {
			// Check if the fragment is already initialized
			if (!mAdded) {
				// If not, instantiate and add it to the activity
				ft.add(android.R.id.content, mFragment);
				mAdded =true;
			} else {
				// If it exists, simply attach it in order to show it
				ft.attach(mFragment);
			}
		}	        

		@Override
		public void onTabUnselected(Tab tab, FragmentTransaction ft) {
			ft.detach(mFragment);
		}

		@Override
		public void onTabReselected(Tab tab, FragmentTransaction ft) {

		}
	}
	
	public void importFile(String path) {
		File ifile = new File(path);
		String error = null;

		try {

			String data = "";
			
			if (ifile.length() > MAX_FILE_LEN) {
				error = getString(R.string.file_too_large);
			} else {
				byte[] filedata = readBytesFromFile(ifile) ;
				if(mBase64Encode)
					data += Base64.encodeToString(filedata, Base64.DEFAULT);
				else
					data += new String(filedata);
				mData = data;

				/*
				mInlineFragment.setData(data);
				getActionBar().selectTab(inlineFileTab);
				*/
				saveInlineData(data);
			}
		} catch (FileNotFoundException e) {
			error = e.getLocalizedMessage();
		} catch (IOException e) {
			error = e.getLocalizedMessage();
		}

		if (error != null) {
			Builder ab = new AlertDialog.Builder(this);
			ab.setTitle(R.string.error_importing_file);
			ab.setMessage(getString(R.string.import_error_message) + ": " + error);
			ab.setPositiveButton(android.R.string.ok, null);
			ab.show();
		}
	}

	private byte[] readBytesFromFile(File file) throws IOException {
		InputStream input = new FileInputStream(file);

		long len= file.length();


		// Create the byte array to hold the data
		byte[] bytes = new byte[(int) len];

		// Read in the bytes
		int offset = 0;
		int bytesRead = 0;
		while (offset < bytes.length
				&& (bytesRead=input.read(bytes, offset, bytes.length-offset)) >= 0) {
			offset += bytesRead;
		}

		input.close();
		return bytes;
	}
	
	
	public void setFile(String path) {
		Intent intent = new Intent();
		intent.putExtra(RESULT_DATA, path);
		setResult(Activity.RESULT_OK,intent);
		finish();		
	}

	public String getSelectPath() {
		if(!mData.startsWith(VpnProfile.INLINE_TAG))
			return mData;
		else
			return Environment.getExternalStorageDirectory().getPath();
	}

	public CharSequence getInlineData() {
		if(mData.startsWith(VpnProfile.INLINE_TAG))
			return mData.substring(VpnProfile.INLINE_TAG.length());
		else
			return "";
	}
	
	public void clearData() {
		Intent intent = new Intent();
		intent.putExtra(RESULT_DATA, (String)null);
		setResult(Activity.RESULT_OK,intent);
		finish();
		
	}

	public void saveInlineData(String string) {
		Intent intent = new Intent();
		
		intent.putExtra(RESULT_DATA,VpnProfile.INLINE_TAG + string);
		setResult(Activity.RESULT_OK,intent);
		finish();
		
	}
}
