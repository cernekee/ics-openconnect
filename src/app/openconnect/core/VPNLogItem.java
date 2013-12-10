package app.openconnect.core;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.content.Context;
import android.text.format.DateFormat;

public class VPNLogItem implements Serializable {
	private static final long serialVersionUID = 7341923752956090364L;

	private long mLogtime = System.currentTimeMillis();
	private String mMsg;
	@SuppressWarnings("unused")	private int mLevel;

	public VPNLogItem(int level, String msg) {
		this.mLevel = level;
		this.mMsg = msg;
	}

	public String format(Context context, int timeFormat) {
		String pfx = "";
		if (timeFormat != VPNLog.TIME_FORMAT_NONE) {
			Date d = new Date(mLogtime);
			java.text.DateFormat formatter;

			if (timeFormat == VPNLog.TIME_FORMAT_LONG) { 
				formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault());
			} else {
				formatter = DateFormat.getTimeFormat(context);
			}
			pfx = formatter.format(d) + " ";
		}
		return pfx + mMsg;
	}

	public String toString() {
		return format(null, VPNLog.TIME_FORMAT_LONG);
	}
}
