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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import app.openconnect.R;
import app.openconnect.core.AssetExtractor;

public class FaqFragment extends Fragment  {

	private String htmlEncode(String in) {
		in = TextUtils.htmlEncode(in).replace("\n", "<br>");

		// match markdown-formatted links: [link text](http://foo.bar.com)
		// replace with: <a href="http://foo.bar.com">link text</a>
		StringBuilder out = new StringBuilder();
		Pattern p = Pattern.compile("\\[(.+?)\\]\\((\\S+)\\)");
		Matcher m;

		while (true) {
			m = p.matcher(in);
			if (!m.find()) {
				break;
			}
			out.append(in.substring(0, m.start()));
			out.append("<a href=\"" + m.group(2) + "\">");
			out.append(m.group(1));
			out.append("</a>");
			in = in.substring(m.end());
		}

		out.append(in);
		return out.toString();
	}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
    		Bundle savedInstanceState) {
    	View v = inflater.inflate(R.layout.faq, container, false);
    	Activity act = getActivity();

    	String items[] = getResources().getStringArray(R.array.faq_text);
    	StringBuilder html = new StringBuilder();
    	html.append(AssetExtractor.readString(act, "header.html"));

    	// "Q: " and "A: "
    	String q_abbrev = act.getString(R.string.question_abbrev) + " ";
    	String a_abbrev = act.getString(R.string.answer_abbrev) + " ";

    	for (int i = 0; i < items.length; i += 2) {
    		html.append("<b>" + q_abbrev + htmlEncode(items[i]) + "</b><br><br>");
    		html.append(a_abbrev + htmlEncode(items[i + 1]) + "<br><br>");
    	}
    	html.append(AssetExtractor.readString(act, "footer.html"));

    	WebView contents = (WebView)v.findViewById(R.id.faq_text);
    	contents.loadDataWithBaseURL("file:///android_asset/", html.toString(), null, null, null);

    	// setting this through CSS breaks the gradient background
    	contents.setBackgroundColor(0);

		return v;
    }
}
