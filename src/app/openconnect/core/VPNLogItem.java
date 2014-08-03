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

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.content.Context;

public class VPNLogItem implements Serializable {
	private static final long serialVersionUID = 7341923752956090364L;

	private long mLogtime = System.currentTimeMillis();
	private String mMsg;
	@SuppressWarnings("unused")	private int mLevel;

	public VPNLogItem(int level, String msg) {
		this.mLevel = level;
		this.mMsg = msg;
	}

	public String format(Context context, String timeFormat) {
		String pfx = "";
		if (!timeFormat.equals("none")) {
			Date d = new Date(mLogtime);
			java.text.DateFormat formatter;

			if (timeFormat.equals("long")) { 
				formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault());
			} else {
				formatter = new SimpleDateFormat("HH:mm:ss",Locale.getDefault());
			}
			pfx = formatter.format(d) + " ";
		}
		return pfx + mMsg;
	}

	public String toString() {
		return format(null, "long");
	}
}
