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
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import app.openconnect.R;

public class FaqFragment extends Fragment  {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
    		Bundle savedInstanceState) {
    	View v= inflater.inflate(R.layout.faq, container, false);
    	
    	insertHtmlEntry(v,R.id.broken_images_faq,R.string.broken_images_faq);
    	insertHtmlEntry(v,R.id.faq_howto,R.string.faq_howto);
    	insertHtmlEntry(v, R.id.baterry_consumption, R.string.baterry_consumption);  
    	insertHtmlEntry(v, R.id.faq_tethering, R.string.faq_tethering);
		
		return v;
		
		

    }

	private void insertHtmlEntry (View v, int viewId, int stringId) {
		TextView faqitem = (TextView) v.findViewById(viewId);
    	faqitem.setText(Html.fromHtml(getActivity().getString(stringId)));
    	faqitem.setMovementMethod(LinkMovementMethod.getInstance());
	}

}
