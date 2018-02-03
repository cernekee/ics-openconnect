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

import java.util.Locale;
import java.util.UUID;

import android.content.SharedPreferences;

public class VpnProfile implements Comparable<VpnProfile> {
    public static final String INLINE_TAG = "[[INLINE]]";

    public SharedPreferences mPrefs;
    public String mName;

    private UUID mUuid;

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

	@Override
	public int compareTo(VpnProfile arg0) {
		Locale def = Locale.getDefault();
		return getName().toUpperCase(def).compareTo(arg0.getName().toUpperCase(def));
	}
}




