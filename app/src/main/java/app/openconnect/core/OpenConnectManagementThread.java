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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.security.MessageDigest;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.util.Base64;

import org.infradead.libopenconnect.LibOpenConnect;

import com.stericson.RootTools.execution.CommandCapture;
import com.stericson.RootTools.execution.Shell;

import app.openconnect.AuthFormHandler;
import app.openconnect.R;
import app.openconnect.VpnProfile;

public class OpenConnectManagementThread implements Runnable, OpenVPNManagement {

	public static final String TAG = "OpenConnect";

	// Keep these in sync with the "connection_states" string array
	public static final int STATE_AUTHENTICATING = 1;
	public static final int STATE_USER_PROMPT = 2;
	public static final int STATE_AUTHENTICATED = 3;
	public static final int STATE_CONNECTING = 4;
	public static final int STATE_CONNECTED = 5;
	public static final int STATE_DISCONNECTED = 6;

	private Context mContext;
	private VpnProfile mProfile;
	private OpenVpnService mOpenVPNService;
	private SharedPreferences mPrefs;
	private SharedPreferences mAppPrefs;
	private String mFilesDir;
	private String mCacheDir;
	private String mServerAddr;

	private LibOpenConnect mOC;
	private boolean mAuthgroupSet = false;
	private String mLastFormDigest;
	private HashMap<String,Boolean> mAcceptedCerts = new HashMap<String,Boolean>();
	private HashMap<String,Boolean> mRejectedCerts = new HashMap<String,Boolean>();
	private boolean mAuthDone = false;

	private boolean mRequestPause;
	private boolean mRequestDisconnect;
	private Object mMainloopLock = new Object();

    public OpenConnectManagementThread(Context context, VpnProfile profile, OpenVpnService openVpnService) {
    	mContext = context;
		mProfile = profile;
		mOpenVPNService = openVpnService;
		mPrefs = mProfile.mPrefs;
		mAppPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
	}

    private String getStringPref(final String key) {
    	return mPrefs.getString(key, "");
    }

    private boolean getBoolPref(final String key) {
    	return mPrefs.getBoolean(key, false);
    }

    private void putStringPref(final String key, String value) {
    	mPrefs.edit().putString(key, value).commit();
    }

    private String formatTime(long in) {
    	if (in <= 0) {
    		return "NEVER";
    	}
    	DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.US);
    	return df.format(in);
    }

    private void updateStatPref(final String key) {
    	long count = mPrefs.getLong(key, 0) + 1;
    	long now = System.currentTimeMillis();
    	long first = mPrefs.getLong(key + "_first", now);

    	SharedPreferences.Editor ed = mPrefs.edit();
    	ed.putLong(key, count);
    	ed.putLong(key + "_first", first);
    	ed.putLong(key + "_prev", now);
    	ed.apply();
    }

    private void logOneStat(final String key) {
    	long count = mPrefs.getLong(key, 0);
    	long first = mPrefs.getLong(key + "_first", 0);
    	long prev = mPrefs.getLong(key + "_prev", 0);

    	log("STAT: " + key + "=" + count + "; first=" + formatTime(first) + "; prev=" + formatTime(prev));
    }

    private void logStats() {
    	logOneStat("attempt");
    	logOneStat("connect");
    	logOneStat("cancel");
    }

    private void log(String msg) {
    	mOpenVPNService.log(VPNLog.LEVEL_INFO, msg);
    }

    private boolean isCertAccepted(String hash) {
    	if (mAcceptedCerts.containsKey(hash) ||
    			getStringPref("ACCEPTED-CERT-" + hash).equals("true")) {
    		return true;
    	}
    	return false;
    }

    private void acceptCert(String hash, boolean save) {
		mAcceptedCerts.put(hash, true);
		if (save) {
			putStringPref("ACCEPTED-CERT-" + hash, "true");
		}
    }

	private class AndroidOC extends LibOpenConnect {
		private String getPeerCertSHA1() {
			MessageDigest md;
			try {
				md = MessageDigest.getInstance("SHA-1");
			} catch (Exception e) {
				// if this ever actually happens, the NPE will generate a crash report
				log("getPeerCertSHA1: could not initialize MessageDigest");
				return null;
			}

			md.reset();
			md.update(getPeerCertDER());

			Formatter f = new Formatter();
			for (byte b : md.digest()) {
				f.format("%02X", b);
			}

			String ret = f.toString();
			f.close();
			return ret;
		}

		public int onValidatePeerCert(String reason) {
			log("CALLBACK: onValidatePeerCert");

			// This can be called repeatedly on the same (re)connection attempt
			String hash = getPeerCertSHA1().toLowerCase(Locale.US);
			if (isCertAccepted(hash)) {
				return 0;
			}
			if (mRejectedCerts.containsKey(hash)) {
				return -1;
			}
			if (mAuthDone) {
				log("AUTH: certificate mismatch on existing connection");
				return -1;
			}

			Integer response = (Integer)mOpenVPNService.promptUser(
					new CertWarningDialog(mPrefs, getHostname(), hash, reason));

			if (response != CertWarningDialog.RESULT_NO) {
				acceptCert(hash, response == CertWarningDialog.RESULT_ALWAYS);
				return 0;
			} else {
				log("AUTH: user rejected bad certificate");

				// these aren't cached in the profile, but the library can call
				// onValidatePeerCert() multiple times if it's retrying with and without
				// XML POST enabled
				mRejectedCerts.put(hash, true);
				return -1;
			}
		}

		public int onWriteNewConfig(byte[] buf) {
			log("CALLBACK: onWriteNewConfig");
			return 0;
		}

		public int onProcessAuthForm(LibOpenConnect.AuthForm authForm) {
			log("CALLBACK: onProcessAuthForm");
			if (authForm.error != null) {
				log("AUTH: error '" + authForm.error + "'");
			}
			if (authForm.message != null) {
				log("AUTH: message '" + authForm.message + "'");
			}

			setState(STATE_USER_PROMPT);
			AuthFormHandler h = new AuthFormHandler(mPrefs, authForm, mAuthgroupSet, mLastFormDigest);

			Integer response = (Integer)mOpenVPNService.promptUser(h);
			if (response == OC_FORM_RESULT_OK) {
				setState(STATE_AUTHENTICATING);
				mLastFormDigest = h.getFormDigest();
			} else if (response == OC_FORM_RESULT_NEWGROUP) {
				log("AUTH: requesting authgroup change " +
						(mAuthgroupSet ? "(interactive)" : "(non-interactive)"));
				mAuthgroupSet = true;
			} else {
				log("AUTH: form result is " + response);
			}
			return response;
		}

		public void onProgress(int level, String msg) {
			mOpenVPNService.log(level, "LIB: " + msg.trim());
		}

		public void onProtectSocket(int fd) {
			if (mOpenVPNService.protect(fd) != true) {
				log("Error protecting fd " + fd);
			}
		}

		public void onStatsUpdate(LibOpenConnect.VPNStats stats) {
			mOpenVPNService.setStats(stats);
		}
	}

	@Override
	public void run() {
		logStats();

		try {
			if (mAppPrefs.getBoolean("loadTunModule", false)) {
				Shell.runRootCommand(new CommandCapture(0, "insmod /system/lib/modules/tun.ko"));
			}
			if (mAppPrefs.getBoolean("useCM9Fix", false)) {
				Shell.runRootCommand(new CommandCapture(0, "chown 1000 /dev/tun"));
			}
		} catch (Exception e) {
			log("error running root commands: " + e.getLocalizedMessage());
		}

		if (!runVPN()) {
			log("VPN terminated with errors");
		}
		setState(STATE_DISCONNECTED);

		synchronized (mMainloopLock) {
			mOC.destroy();
			mOC = null;
		}
		UserDialog.clearDeferredPrefs();

		mOpenVPNService.threadDone();
	}

	private synchronized void setState(int state) {
		mOpenVPNService.setConnectionState(state);
	}

	/* if the wrapper script starts with "#!/path/to/nonexistent/file", use /system/bin/sh instead */
	private boolean rewriteShell(String s) {
		Matcher m = Pattern.compile("^#![ \\t]*(/\\S+)[ \\t\\n]").matcher(s);
		if (!m.find()) {
			return false;
		}

		File f = new File(m.group(1));
		if (f.exists()) {
			return false;
		}
		return true;
	}

	private byte[] decodeBase64(String in)
			throws IllegalArgumentException {
		// android.util.Base64.Decoder.process() only validates the padding, so it
		// cannot be relied upon to distinguish real base64 from e.g. a PEM cert string
		if (!in.matches("^[A-Za-z0-9+/=\\n]+$")) {
			throw new IllegalArgumentException("invalid chars");
		}
		return Base64.decode(in, Base64.DEFAULT);
	}

	private boolean setExecutable(String path) throws IOException {
		File f = new File(path);
		if (!f.exists()) {
			log("PREF: file does not exist");
			return false;
		}
		if (!f.setExecutable(true)) {
			throw new IOException();
		}
		return true;
	}

	private int writeCertOrScript(String path, String prefData, boolean isExecutable)
			throws IOException {
		FileOutputStream fos = new FileOutputStream(path);
		Writer writer = new BufferedWriter(new OutputStreamWriter(fos, "utf-8"));

		if (isExecutable && rewriteShell(prefData)) {
			writer.write("#!/system/bin/sh\n");
		}
		writer.write(prefData);
		writer.close();

		if (isExecutable) {
			setExecutable(path);
		}

		return prefData.length();
	}

	private int inlineToTempFile(String path, String prefData, boolean isExecutable)
			throws IOException {
		byte data[] = null;
		int bytes = 0;

		try {
			FileOutputStream fos = new FileOutputStream(path);
			data = decodeBase64(prefData);
			bytes = data.length;

			try {
				/* Allow reuse of standard x86 Linux CSD scripts */
				if (isExecutable && rewriteShell(new String(data))) {
					fos.write("#!/system/bin/sh\n".getBytes());
				}
			} catch (Exception e) {
				/* in case we're trying to pattern-match a binary blob */
			}
			fos.write(data);
			fos.close();

			if (isExecutable) {
				setExecutable(path);
			}
		} catch (IllegalArgumentException e) {
			/* legacy profiles didn't use base64 encoding */
			bytes = writeCertOrScript(path, prefData, isExecutable);
		} catch (IOException e) {
			return -1;
		}
		return bytes;
	}

	private String prefToTempFile(String prefName, boolean isExecutable) throws IOException {
		String prefData = getStringPref(prefName);
		String path = mCacheDir + File.separator + prefName + ".tmp";

		if (prefData.equals("")) {
			return null;
		}
		if (prefData.startsWith(VpnProfile.INLINE_TAG)) {
			int bytes = inlineToTempFile(path, prefData.substring(10), isExecutable);
			if (bytes < 0) {
				log("PREF: I/O exception writing " + prefName);
				return null;
			} else {
				log("PREF: wrote out " + path + " (" + bytes + ")");
			}
		} else {
			String srcPath;

			log("PREF: using existing file " + prefData);
			if (prefData.startsWith("/")) {
				srcPath = prefData;
			} else {
				srcPath = ProfileManager.getCertPath() + prefData;
			}

			if (isExecutable) {
				/* Make sure that "#!/system/bin/sh" gets prepended to CSD scripts, if needed */
				String contents = AssetExtractor.readStringFromFile(srcPath);
				if (contents == null) {
					return null;
				}
				int bytes = writeCertOrScript(path, contents, true);
				if (bytes < 0) {
					log("PREF: I/O exception writing " + prefName);
					return null;
				} else {
					log("PREF: wrote out " + path + " (" + bytes + ")");
				}
			} else {
				path = srcPath;
			}
		}
		return path;
	}

	private boolean setPreferences() {
		String s;

		try {
			String PATH = System.getenv("PATH");

			if (!PATH.startsWith(mFilesDir)) {
				PATH = mFilesDir + ":" + PATH;
			}
			s = prefToTempFile("custom_csd_wrapper", true);
			mOC.setCSDWrapper(s != null ? s : (mFilesDir + File.separator + "android_csd.sh"), mCacheDir, PATH);

			s = prefToTempFile("ca_certificate", false);
			if (s != null) {
				mOC.setCAFile(s);
			}

			s = prefToTempFile("user_certificate", false);
			String key = prefToTempFile("private_key", false);
			if (s != null) {
				if (key == null) {
					// assume the file contains the cert + key
					mOC.setClientCert(s, s);
				} else {
					mOC.setClientCert(s, key);
				}
			}
		} catch (IOException e) {
			log("Error writing temporary file");
			return false;
		}

		mServerAddr = getStringPref("server_address");
		mOC.setXMLPost(!getBoolPref("disable_xml_post"));
		mOC.setPFS(getBoolPref("require_pfs"));

		String os = getStringPref("reported_os");
		mOC.setReportedOS(os);
		if (os.equals("android") || os.equals("apple-ios")) {
			// if ocserv sees the X-AnyConnect-Identifier-* "mobile headers" it
			// will use the mobile-idle-timeout instead of idle-timeout
			mOC.setMobileInfo("1.0", os, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
		}

		if (getBoolPref("dpd_override")) {
			try {
				int dpd = Integer.parseInt(getStringPref("dpd_value"));
				if (dpd > 0) {
					mOC.setDPD(dpd);
				}
			} catch (Exception e) {
				log("DPD: bad dpd_value, ignoring");
			}
		}

		s = getStringPref("software_token");
		String token = getStringPref("token_string");
		int ret = 0;

		if (s.equals("securid")) {
			ret = mOC.setTokenMode(LibOpenConnect.OC_TOKEN_MODE_STOKEN, token);
		} else if (s.equals("totp")) {
			ret = mOC.setTokenMode(LibOpenConnect.OC_TOKEN_MODE_TOTP, token);
		}
		if (ret < 0) {
			log("Error " + ret + " setting token string");
			return false;
		}

		prefChanged();

		return true;
	}

	private void updateLogLevel() {
		if (mAppPrefs.getBoolean("trace_log", false)) {
			mOC.setLogLevel(LibOpenConnect.PRG_TRACE);
		} else {
			mOC.setLogLevel(LibOpenConnect.PRG_DEBUG);
		}
	}

	private boolean getSubnetPref(ArrayList<String> subnets) {
		for (String s : getStringPref("split_tunnel_networks").split("[,\\s]+")) {
			if (s.equals("")) {
				continue;
			}
			subnets.add(s);
		}
		if (subnets.isEmpty()) {
			log("ROUTE: split tunnel list is empty; check your VPN settings");
			return false;
		}
		return true;
	}

	private void addDefaultRoutes(VpnService.Builder b, LibOpenConnect.IPInfo ip, ArrayList<String> subnets) {
		boolean ip4def = true, ip6def = true;

		for (String s : subnets) {
			if (s.contains(":")) {
				ip6def = false;
			} else {
				ip4def = false;
			}
		}

		if (ip4def && ip.addr != null) {
			b.addRoute("0.0.0.0", 0);
			log("ROUTE: 0.0.0.0/0");
		}

		if (ip6def && ip.netmask6 != null) {
			b.addRoute("::", 0);
			log("ROUTE: ::/0");
		}
	}

	private void addSubnetRoutes(VpnService.Builder b, LibOpenConnect.IPInfo ip, ArrayList<String> subnets) {
		for (String s : subnets) {
			try {
				if (s.contains(":")) {
					String ss[] = s.split("/");
					if (ss.length == 1) {
						b.addRoute(ss[0], 128);
					} else {
						b.addRoute(ss[0], Integer.parseInt(ss[1]));
					}
					log("ROUTE: " + s);
				} else {
					CIDRIP cdr;
					if (!s.contains("/")) {
						cdr = new CIDRIP(s + "/32");
					} else {
						cdr = new CIDRIP(s);
					}
					b.addRoute(cdr.mIp, cdr.len);
					log("ROUTE: " + cdr.mIp + "/" + cdr.len);
				}
			} catch (Exception e) {
				log("ROUTE: skipping invalid route '" + s + "'");
			}
		}
	}

	private void setIPInfo(VpnService.Builder b) {
		LibOpenConnect.IPInfo ip = mOC.getIPInfo();
		CIDRIP cdr;
		int minMtu = 576;

		/* IP + MTU */

		if (ip.addr != null && ip.netmask != null) {
			cdr = new CIDRIP(ip.addr, ip.netmask);
			b.addAddress(cdr.mIp, cdr.len);
			log("IPv4: " + cdr.mIp + "/" + cdr.len);
		}
		if (ip.netmask6 != null) {
			String ss[] = ip.netmask6.split("/");
			if (ss.length == 2) {
				int netmask = Integer.parseInt(ss[1]);
				b.addAddress(ss[0], netmask);
				log("IPv6: " + ip.netmask6);
				/* RFC 2460 */
				minMtu = 1280;
			}
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			/*
			 * KK 4.4.3 and 4.4.4 won't connect if MTU < 1280.  See:
			 * https://code.google.com/p/android/issues/detail?id=70916
			 */
			minMtu = 1280;
		}

		if (ip.MTU < minMtu) {
			b.setMtu(minMtu);
			log("MTU: " + minMtu + " (forced)");
		} else {
			b.setMtu(ip.MTU);
			log("MTU: " + ip.MTU);
		}

		/* routing */

		ArrayList<String> subnets = new ArrayList<String>(), dns = ip.DNS;
		String domain = ip.domain;

		if (getStringPref("split_tunnel_mode").equals("on_vpn_dns")) {
			getSubnetPref(subnets);
		} else if (getStringPref("split_tunnel_mode").equals("on_uplink_dns")) {
			getSubnetPref(subnets);
			dns = new ArrayList<String>();
			domain = null;
		} else {
			subnets = ip.splitIncludes;
			addDefaultRoutes(b, ip, subnets);
		}
		addSubnetRoutes(b, ip, subnets);

		/* DNS */

		for (String s : dns) {
			b.addDnsServer(s);
			b.addRoute(s, s.contains(":") ? 128 : 32);
			log("DNS: " + s);
		}
		if (domain != null) {
			b.addSearchDomain(domain);
			log("DOMAIN: " + domain);
		}

		mOpenVPNService.setIPInfo(ip, mOC.getHostname());
	}

	private void errorAlert(String message) {
		mOpenVPNService.promptUser(new ErrorDialog(mPrefs,
				mContext.getString(R.string.error_connection_failed),
				message));
	}

	private void errorAlert() {
		// general (unspecified) connection failure
		errorAlert(mContext.getString(R.string.error_cant_connect, mOC.getHostname()));
	}

	private void extractBinaries() {
		if (!AssetExtractor.extractAll(mContext)) {
			log("Error extracting assets");
		}

		// curl wrapper script:
		// <= ICS: always use run_pie
		// >  ICS: never use run_pie
		try {
			String curl_bin = mFilesDir + "/curl-bin";
			String run_pie = mFilesDir + "/run_pie ";

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
				run_pie = "";
			}
			writeCertOrScript(mFilesDir + "/curl",
				"#!/system/bin/sh\nexec " + run_pie + curl_bin + " \"$@\"\n", true);
		} catch (IOException e) {
			// mkdir won't throw an exception
			log("Error writing curl wrapper scripts");
		}
	}

	private boolean runVPN() {
		updateStatPref("attempt");

		mFilesDir = mContext.getFilesDir().getPath();
		mCacheDir = mContext.getCacheDir().getPath();
		extractBinaries();

		setState(STATE_CONNECTING);
		synchronized (mMainloopLock) {
			mOC = new AndroidOC();
		}

		if (setPreferences() == false) {
			return false;
		}

		if (mOC.parseURL(mServerAddr) != 0) {
			log("Error parsing server address");
			errorAlert(mContext.getString(R.string.error_invalid_hostname, mServerAddr));
			return false;
		}
		int ret = mOC.obtainCookie();
		if (ret < 0) {
			// don't pop up an alert if the user rejected the server cert
			if (mRejectedCerts.isEmpty() && !mRequestDisconnect) {
				log("Error obtaining cookie");
				errorAlert();
			} else {
				updateStatPref("cancel");
			}
			return false;
		} else if (ret > 0) {
			log("User canceled auth dialog");
			updateStatPref("cancel");
			return false;
		}

		mAuthDone = true;
		UserDialog.writeDeferredPrefs();
		setState(STATE_AUTHENTICATED);
		if (mOC.makeCSTPConnection() != 0) {
			if (!mRequestDisconnect) {
				log("Error establishing CSTP connection");
				errorAlert();
			}
			return false;
		}

		VpnService.Builder b = mOpenVPNService.getVpnServiceBuilder();
		setIPInfo(b);

		ParcelFileDescriptor pfd;
		try {
			pfd = b.establish();
		} catch (Exception e) {
			log("Exception during establish(): " + e.getLocalizedMessage());
			return false;
		}

		if (pfd == null || mOC.setupTunFD(pfd.getFd()) != 0) {
			log("Error setting up tunnel fd");
			errorAlert();
			return false;
		}
		setState(STATE_CONNECTED);
		updateStatPref("connect");

		mOC.setupDTLS(60);

		while (true) {
			if (mOC.mainloop(300, LibOpenConnect.RECONNECT_INTERVAL_MIN) < 0) {
				break;
			}
			synchronized (mMainloopLock) {
				if (mRequestDisconnect) {
					// let the library send the BYE packet and wrap up
					continue;
				}
				while (mRequestPause) {
					try {
						mMainloopLock.wait();
					} catch (InterruptedException e) {
					}
				}
			}
		}

		try {
			pfd.close();
		} catch (IOException e) {
		}
		return true;
	}

	public void reconnect() {
		log("RECONNECT");
		// if mRequestPause is false, this will drop the connection and immediately
		// restart the mainloop
		synchronized (mMainloopLock) {
			if (mOC != null) {
				mOC.pause();
			}
		}
	}

	@Override
	public void pause () {
		log("PAUSE");
		synchronized (mMainloopLock) {
			if (!mRequestPause && !mRequestDisconnect && mOC != null) {
				mRequestPause = true;
				mOC.pause();
			}
		}
	}

	@Override
	public void resume() {
		log("RESUME");
		synchronized (mMainloopLock) {
			if (mRequestPause) {
				mRequestPause = false;
				mMainloopLock.notify();
			}
		}
	}

	@Override
	public boolean stopVPN() {
		log("STOP");
		synchronized (mMainloopLock) {
			if (mRequestDisconnect || mOC == null) {
				return true;
			}
			mRequestDisconnect = true;
			mRequestPause = false;
			mOC.cancel();
			mMainloopLock.notify();
		}
		return true;
	}

	public void requestStats() {
		boolean noStats = false;
		synchronized (mMainloopLock) {
			if (mRequestPause || mRequestDisconnect || mOC == null) {
				noStats = true;
			} else {
				mOC.requestStats();
			}
		}
		if (noStats) {
			// Generate fake callback to the activity that requested stats, so it
			// isn't waiting forever for a nonexistent event
			mOpenVPNService.setStats(null);
		}
	}

	public void prefChanged() {
		updateLogLevel();
	}

}
