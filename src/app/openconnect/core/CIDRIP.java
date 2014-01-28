/*
 * Adapted from OpenVPN for Android
 * Copyright (c) 2012-2013, Arne Schwabe
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

import java.util.Locale;

class CIDRIP{
	String mIp;
	int len;

	public CIDRIP(String combo) {
		String ss[] = combo.split("/");

		mIp = ss[0];
		if (ss[1].matches("^[0-9]+$")) {
			len = Integer.parseInt(ss[1]);
		} else {
			len = maskToLen(ss[1]);
		}
		if (len < 0 || len > 32) {
			len = 32;
		}
		normalise();
	}

	public CIDRIP(String ip, String mask) {
		mIp = ip;
		len = maskToLen(mask);
	}

	public CIDRIP(String ip, int prefixLen) {
		mIp = ip;
		len = prefixLen;
	}

	private static int maskToLen(String mask) {
		long netmask=getInt(mask);

		// Add 33. bit to ensure the loop terminates
		netmask += 1l << 32;

		int lenZeros = 0;
		while((netmask & 0x1) == 0) {
			lenZeros++;
			netmask = netmask >> 1;
		}
		// Check if rest of netmask is only 1s
		if(netmask != (0x1ffffffffl >> lenZeros)) {
			// Asume no CIDR, set /32
			return 32;
		} else {
			return 32 - lenZeros; 
		}
	}

	@Override
	public String toString() {
		return String.format(Locale.ENGLISH,"%s/%d",mIp,len);
	}

	public boolean normalise(){
		long ip=getInt(mIp);

		long newip = ip & (0xffffffffl << (32 -len));
		if (newip != ip){
			mIp = String.format("%d.%d.%d.%d", (newip & 0xff000000) >> 24,(newip & 0xff0000) >> 16, (newip & 0xff00) >> 8 ,newip & 0xff);
			return true;
		} else {
			return false;
		}
	}
	static long getInt(String ipaddr) {
		String[] ipt = ipaddr.split("\\.");
		long ip=0;

		ip += Long.parseLong(ipt[0])<< 24;
		ip += Integer.parseInt(ipt[1])<< 16;
		ip += Integer.parseInt(ipt[2])<< 8;
		ip += Integer.parseInt(ipt[3]);

		return ip;
	}
	public long getInt() {
		return getInt(mIp);
	}
	
}
