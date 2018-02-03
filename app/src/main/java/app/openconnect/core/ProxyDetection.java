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

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

import app.openconnect.R;
import app.openconnect.VpnProfile;

public class ProxyDetection {
	static SocketAddress detectProxy(VpnProfile vp) {
		// Construct a new url with https as protocol
		try {
			URL url = new URL("http://localhost"); // FIXME
			Proxy proxy = getFirstProxy(url);

			if(proxy==null)
				return null;
			SocketAddress addr = proxy.address();
			if (addr instanceof InetSocketAddress) {
				return addr; 
			}
			
		} catch (MalformedURLException e) {
			OpenVPN.logError(R.string.getproxy_error,e.getLocalizedMessage());
		} catch (URISyntaxException e) {
			OpenVPN.logError(R.string.getproxy_error,e.getLocalizedMessage());
		}
		return null;
	}

	static Proxy getFirstProxy(URL url) throws URISyntaxException {
		System.setProperty("java.net.useSystemProxies", "true");

		List<Proxy> proxylist = ProxySelector.getDefault().select(url.toURI());


		if (proxylist != null) {
			for (Proxy proxy: proxylist) {
				SocketAddress addr = proxy.address();

				if (addr != null) {
					return proxy;
				}
			}

		}
		return null;
	}
}