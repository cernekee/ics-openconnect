/*
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

package app.openconnect.core;

import java.util.HashMap;

public class FragCache {

	private static HashMap<String,String> mCache;

	public static synchronized void init() {
		mCache = new HashMap<String,String>();
	}

	private static String hashCode(String UUID, String key) {
		StringBuilder sb = new StringBuilder();
		if (UUID != null) {
			sb.append(UUID.hashCode());
		}
		sb.append(".");
		if (key != null) {
			sb.append(key.hashCode());
		}
		return sb.toString();
	}

	public static synchronized String get(String UUID, String key) {
		return mCache.get(hashCode(UUID, key));
	}

	public static String get(String key) {
		return get(null, key);
	}

	public static synchronized void put(String UUID, String key, String value) {
		mCache.put(hashCode(UUID, key), value);
	}

	public static void put(String key, String value) {
		put(null, key, value);
	}

}
