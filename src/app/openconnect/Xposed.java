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

package app.openconnect;

import java.lang.reflect.Field;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Binder;

import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class Xposed implements IXposedHookZygoteInit {

	public static final String PKG_NAME = "app.openconnect";

	@Override
	public void initZygote(StartupParam startupParam) throws Throwable {

		final Class<?> clazz0 = XposedHelpers.findClass("android.net.BaseNetworkStateTracker", null);
		final String className = "com.android.server.connectivity.Vpn";

		XposedHelpers.findAndHookMethod(className, null, "prepare",
				String.class, String.class, new XC_MethodHook() {

			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				Object a[] = param.args;
				if (PKG_NAME.equals(a[0]) && a[1] == null) {
					// Normally ConfirmDialog calls prepare("app.openconnect", null) to see whether
					// to prompt the user.  prepare() returns false unless we've already been authorized.
					// We will swap the argument order so the prepare() call succeeds.
					a[1] = a[0];
					a[0] = null;
					XposedBridge.log("OpenConnect: bypassing VPN confirmation dialog");
				}
			}
		});

		XposedHelpers.findAndHookMethod(className, null, "enforceControlPermission",
				new XC_MethodHook() {

			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				// prepare() normally expects to get called from the system-managed
				// confirmation dialog; it will throw a SecurityException if some other
				// caller tries to permit VPN access.  Override this check when appropriate.

				// Same as UserHandle.getAppId(), but without using the hidden API
				int appId = Binder.getCallingUid() % 100000;

				try {
					Field f = clazz0.getDeclaredField("mContext");
					f.setAccessible(true);
					Context mContext = (Context)f.get(param.thisObject);
					PackageManager pm = mContext.getPackageManager();
					ApplicationInfo app = pm.getApplicationInfo(PKG_NAME, 0);

					if (appId == app.uid) {
						// return early, skipping Android's checks
						param.setResult(null);
					}
				} catch (Exception e) {
					XposedBridge.log("OpenConnect: exception checking UIDs: " + e.getLocalizedMessage());
				}
			}
		});

	}
}
