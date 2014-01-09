/*
 * Adapted from OpenVPN for Android
 * Copyright (c) 2012-2013, Arne Schwabe
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

package app.openconnect;

import java.io.*;
import java.security.PrivateKey;
import java.util.Locale;
import java.util.UUID;

import android.content.SharedPreferences;

public class VpnProfile implements Serializable, Comparable<VpnProfile> {
    // Note that this class cannot be moved to core where it belongs since
    // the profile loading depends on it being here
    // The Serializable documentation mentions that class name change are possible
    // but the how is unclear
    //

    public static final int TYPE_CERTIFICATES = 0;
    public static final int TYPE_PKCS12 = 1;
    public static final int TYPE_KEYSTORE = 2;
    public static final int TYPE_USERPASS = 3;
    public static final int TYPE_STATICKEYS = 4;
    public static final int TYPE_USERPASS_CERTIFICATES = 5;
    public static final int TYPE_USERPASS_PKCS12 = 6;
    public static final int TYPE_USERPASS_KEYSTORE = 7;
    public static final int X509_VERIFY_TLSREMOTE = 0;
    public static final int X509_VERIFY_TLSREMOTE_COMPAT_NOREMAPPING = 1;
    public static final int X509_VERIFY_TLSREMOTE_DN = 2;
    public static final int X509_VERIFY_TLSREMOTE_RDN = 3;
    public static final int X509_VERIFY_TLSREMOTE_RDN_PREFIX = 4;
    // Don't change this, not all parts of the program use this constant
    public static final String EXTRA_PROFILEUUID = "app.openconnect.profileUUID";
    public static final String INLINE_TAG = "[[INLINE]]";
    public static final String MINIVPN = "miniopenvpn";
    private static final long serialVersionUID = 7085688938959334563L;
    public static String DEFAULT_DNS1 = "8.8.8.8";
    public static String DEFAULT_DNS2 = "8.8.4.4";
    public transient String mTransientPW = null;
    public transient String mTransientPCKS12PW = null;
    // variable named wrong and should haven beeen transient
    // but needs to keep wrong name to guarante loading of old
    // profiles
    public transient boolean profileDleted = false;
    public int mAuthenticationType = TYPE_KEYSTORE;
    public String mName;
    public String mAlias;
    public String mClientCertFilename;
    public String mTLSAuthDirection = "";
    public String mTLSAuthFilename;
    public String mClientKeyFilename;
    public String mCaFilename;
    public boolean mUseLzo = true;
    public String mServerPort = "1194";
    public boolean mUseUdp = true;
    public String mPKCS12Filename;
    public String mPKCS12Password;
    public boolean mUseTLSAuth = false;
    public String mServerName = "openvpn.blinkt.de";
    public String mDNS1 = DEFAULT_DNS1;
    public String mDNS2 = DEFAULT_DNS2;
    public String mIPv4Address;
    public String mIPv6Address;
    public boolean mOverrideDNS = false;
    public String mSearchDomain = "blinkt.de";
    public boolean mUseDefaultRoute = true;
    public boolean mUsePull = true;
    public String mCustomRoutes;
    public boolean mCheckRemoteCN = false;
    public boolean mExpectTLSCert = true;
    public String mRemoteCN = "";
    public String mPassword = "";
    public String mUsername = "";
    public boolean mRoutenopull = false;
    public boolean mUseRandomHostname = false;
    public boolean mUseFloat = false;
    public boolean mUseCustomConfig = false;
    public String mCustomConfigOptions = "";
    public String mVerb = "1";
    public String mCipher = "";
    public boolean mNobind = false;
    public boolean mUseDefaultRoutev6 = true;
    public String mCustomRoutesv6 = "";
    public String mKeyPassword = "";
    public boolean mPersistTun = false;
    public String mConnectRetryMax = "5";
    public String mConnectRetry = "5";
    public boolean mUserEditable = true;
    public String mAuth = "";
    public int mX509AuthType = X509_VERIFY_TLSREMOTE_RDN;
    private transient PrivateKey mPrivateKey;
    // Public attributes, since I got mad with getter/setter
    // set members to default values
    private UUID mUuid;
    public SharedPreferences mPrefs;

    private void loadPrefs(SharedPreferences prefs) {
    	mPrefs = prefs;

    	String uuid = mPrefs.getString("profile_uuid", null);
    	if (uuid != null) {
    		mUuid = UUID.fromString(uuid);
    	}
    	mName = mPrefs.getString("profile_name", null);
    }

    public VpnProfile(SharedPreferences prefs, String uuid, String name) {
    	prefs.edit()
    		.putString("profile_uuid", uuid)
    		.putString("profile_name", name)
    		.commit();
    	loadPrefs(prefs);
    }

    public VpnProfile(SharedPreferences prefs) {
    	loadPrefs(prefs);
    }

    public VpnProfile(String name, String uuid) {
        mUuid = UUID.fromString(uuid);
        mName = name;
    }

    public boolean isValid() {
    	if (mName == null || mUuid == null) {
    		return false;
    	}
    	return true;
    }

    public UUID getUUID() {
        return mUuid;

    }

    public String getName() {
        return mName;
    }

    // Used by the Array Adapter
    @Override
    public String toString() {
        return mName;
    }

    public String getUUIDString() {
        return mUuid.toString();
    }

    public PrivateKey getKeystoreKey() {
        return mPrivateKey;
    }

	@Override
	public int compareTo(VpnProfile arg0) {
		Locale def = Locale.getDefault();
		return getName().toUpperCase(def).compareTo(arg0.getName().toUpperCase(def));
	}
}




