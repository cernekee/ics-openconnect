package app.openconnect.core;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import org.infradead.libopenconnect.LibOpenConnect;

import android.content.Context;
import android.text.format.DateFormat;
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
	private ArrayList<LogItem> circ = new ArrayList<LogItem>();
	private LogArrayAdapter mArrayAdapter;

	private class LogItem {
		private long mLogtime = System.currentTimeMillis();
		private int mLevel;
		private String mMsg;

		public LogItem(int level, String msg) {
			this.mLevel = level;
			this.mMsg = msg;
		}

		public String format(Context context, int timeFormat) {
			String pfx = "";
			if (timeFormat != TIME_FORMAT_NONE) {
				Date d = new Date(mLogtime);
				java.text.DateFormat formatter;

				if (timeFormat == TIME_FORMAT_LONG) { 
					formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault());
				} else {
					formatter = DateFormat.getTimeFormat(context);
				}
				pfx = formatter.format(d) + " ";
			}
			return pfx + mMsg;
		}

		public String toString() {
			return "<" + mLevel + "> " + mMsg;
		}
	};

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

			LogItem li = (LogItem)getItem(position);
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
		LogItem li = new LogItem(level, msg);
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
			ret.append((String)s + "\n");
		}
		return ret.toString();
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
