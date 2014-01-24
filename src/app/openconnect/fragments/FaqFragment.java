/*
 * Adapted from OpenVPN for Android
 * Copyright (c) 2012-2013, Arne Schwabe
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

import android.app.Fragment;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import app.openconnect.R;

public class FaqFragment extends Fragment  {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
    		Bundle savedInstanceState) {
    	View v = inflater.inflate(R.layout.faq, container, false);

    	String items[] = getResources().getStringArray(R.array.faq_text);
    	StringBuilder html = new StringBuilder();
    	for (int i = 0; i < items.length; i += 2) {
    		String question = TextUtils.htmlEncode(items[i]).replace("\n", "<br>");
    		String answer = TextUtils.htmlEncode(items[i + 1]).replace("\n", "<br>");
    		html.append("<b>Q: " + question + "</b><br><br>");
    		html.append("A: " + answer + "<br><br>");
    	}

    	TextView tv = (TextView)v.findViewById(R.id.faq_text);
    	tv.setText(Html.fromHtml(html.toString()));

		return v;
    }
}
