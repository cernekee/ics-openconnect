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

import android.app.Fragment;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import app.openconnect.R;

public class AboutFragment extends Fragment  {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
    		Bundle savedInstanceState) {
    	View v= inflater.inflate(R.layout.about, container, false);
    	TextView ver = (TextView) v.findViewById(R.id.version);
    	
    	String version;
    	String name="Openvpn";
		try {
			PackageInfo packageinfo = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0);
			version = packageinfo.versionName;
			name = getString(R.string.app);
		} catch (NameNotFoundException e) {
			version = "error fetching version";
		}

    	
    	ver.setText(getString(R.string.version_info,name,version));
    	
    	TextView paypal = (TextView) v.findViewById(R.id.donatestring);
    	
    	String donatetext = getActivity().getString(R.string.donatewithpaypal);
    	Spanned htmltext = Html.fromHtml(donatetext);
    	paypal.setText(htmltext);
    	paypal.setMovementMethod(LinkMovementMethod.getInstance());
    	
    	TextView translation = (TextView) v.findViewById(R.id.translation);
    	
    	// Don't print a text for myself
    	if ( getString(R.string.translationby).contains("Arne Schwabe"))
    		translation.setText("");
    	else
    		translation.setText(R.string.translationby);
    	return v;
    }

}
