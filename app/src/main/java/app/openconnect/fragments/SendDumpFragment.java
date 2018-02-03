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

package app.openconnect.fragments;

import java.io.File;
import java.util.ArrayList;

import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import app.openconnect.R;
import app.openconnect.core.OpenVPN;

public class SendDumpFragment extends Fragment  {

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		View v = inflater.inflate(R.layout.fragment_senddump, container, false);
		v.findViewById(R.id.senddump).setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				emailMiniDumps();
			}
		});
		return v;
	}

	public void emailMiniDumps()
	{
		//need to "send multiple" to get more than one attachment
		final Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND_MULTIPLE);
		emailIntent.setType("*/*");
		emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, 
				new String[]{"Kevin Cernekee <cernekee@gmail.com>"});

		String version;
		String name="ics-openconnect";
		try {
			PackageInfo packageinfo = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0);
			version = packageinfo.versionName;
			name = packageinfo.applicationInfo.name;
		} catch (NameNotFoundException e) {
			version = "error fetching version";
		}


		emailIntent.putExtra(Intent.EXTRA_SUBJECT, String.format("%s %s Minidump",name,version));

		emailIntent.putExtra(Intent.EXTRA_TEXT, "Please describe the issue you have experienced");

		ArrayList<Uri> uris = new ArrayList<Uri>();

		File ldump = getLastestDump(getActivity());
		if(ldump==null) {
			OpenVPN.logError("No Minidump found!");
		}

		uris.add(Uri.parse("content://app.openconnect.FileProvider/" + ldump.getName()));
		uris.add(Uri.parse("content://app.openconnect.FileProvider/" + ldump.getName() + ".log"));

		emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		emailIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
		startActivity(emailIntent);
	}

	static public File getLastestDump(Context c) {
		long newestDumpTime=0;
		File newestDumpFile=null;

		for(File f:c.getCacheDir().listFiles()) {
			if(!f.getName().endsWith(".dmp"))
				continue;

			if (newestDumpTime < f.lastModified()) {
				newestDumpTime = f.lastModified();
				newestDumpFile=f;
			}
		}
		// Ignore old dumps
		//if(System.currentTimeMillis() - 48 * 60 * 1000 > newestDumpTime )
		//return null;

		return newestDumpFile;
	}
}
