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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

import org.infradead.libopenconnect.LibOpenConnect;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class VPNLog {

	public static final String TAG = "OpenConnect";

	public static final int TIME_FORMAT_TOGGLE = -1;
	public static final int TIME_FORMAT_NONE = 0;
	public static final int TIME_FORMAT_SHORT = 1;
	public static final int TIME_FORMAT_LONG = 2;

	public static final int LEVEL_ERR = LibOpenConnect.PRG_ERR;
	public static final int LEVEL_INFO = LibOpenConnect.PRG_INFO;
	public static final int LEVEL_DEBUG = LibOpenConnect.PRG_DEBUG;
	public static final int LEVEL_TRACE = LibOpenConnect.PRG_TRACE;

	private static final int MAX_ENTRIES = 512;
	private ArrayList<VPNLogItem> circ = new ArrayList<VPNLogItem>();
	private LogArrayAdapter mArrayAdapter;

	public class LogArrayAdapter extends BaseAdapter {

		private Context mContext;
		private int mTimeFormat = TIME_FORMAT_LONG;

		public LogArrayAdapter(Context context) {
			mContext = context;
		}

		@Override
		public int getCount() {
			return circ.size();
		}

		@Override
		public Object getItem(int position) {
			return circ.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			TextView v;
			if (convertView != null && convertView instanceof TextView) {
				v = (TextView)convertView;
			} else {
				v = new TextView(mContext);
			}

			VPNLogItem li = (VPNLogItem)getItem(position);
			v.setText(li.format(mContext, mTimeFormat));
			return v;
		}

		public void setTimeFormat(int timeFormat) {
			if (timeFormat == TIME_FORMAT_TOGGLE) {
				mTimeFormat = (mTimeFormat + 1) % 3;
			} else {
				mTimeFormat = timeFormat;
			}
			notifyDataSetChanged();
		}
	};

	private void updateAdapter() {
		if (mArrayAdapter != null) {
			mArrayAdapter.notifyDataSetChanged();
		}
	}

	public void add(int level, String msg) {
		VPNLogItem li = new VPNLogItem(level, msg);
		circ.add(li);
		while (circ.size() > MAX_ENTRIES) {
			circ.remove(0);
		}
		updateAdapter();
	}

	public void clear() {
		circ.clear();
		updateAdapter();
	}

	public String dump() {
		StringBuilder ret = new StringBuilder();
		for (Object s : circ.toArray()) {
			ret.append(s.toString() + "\n");
		}
		return ret.toString();
	}

	public int saveToFile(String path) {
		int ret = -1;

		try {
			ObjectOutputStream s = new ObjectOutputStream(new FileOutputStream(path));

			s.writeObject((Integer)circ.size());
			for (VPNLogItem i : circ) {
				s.writeObject(i);
			}
			s.close();
			ret = 0;
		} catch (FileNotFoundException e) {
			Log.w(TAG, "file not found writing " + path, e);
		} catch (IOException e) {
			Log.w(TAG, "I/O error writing " + path, e);
		}
		return ret;
	}

	public int restoreFromFile(String path) {
		int ret = -1;

		try {
			ObjectInputStream s = new ObjectInputStream(new FileInputStream(path));
			int records = (Integer)s.readObject();

			circ.clear();
			for (; records > 0; records--) {
				circ.add((VPNLogItem)s.readObject());
			}
			s.close();
			ret = 0;
		} catch (FileNotFoundException e) {
			Log.d(TAG, "file not found reading " + path, e);
		} catch (IOException e) {
			Log.w(TAG, "I/O error reading " + path, e);
		} catch (ClassNotFoundException e) {
			Log.w(TAG, "Class not found reading " + path, e);
		}
		return ret;
	}

	public LogArrayAdapter getArrayAdapter(Context mContext) {
		if (mArrayAdapter != null) {
			Log.w(TAG, "duplicate LogArrayAdapter registration");
		}
		mArrayAdapter = new LogArrayAdapter(mContext);
		return mArrayAdapter;
	}

	public void putArrayAdapter(LogArrayAdapter arrayAdapter) {
		mArrayAdapter = null;
	}
}
