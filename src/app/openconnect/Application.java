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

package app.openconnect;

import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;

import app.openconnect.core.FragCache;
import app.openconnect.core.ProfileManager;

@ReportsCrashes(
		mode = ReportingInteractionMode.DIALOG,
		resDialogText = R.string.crash_dialog_text,
		resDialogCommentPrompt = R.string.crash_dialog_comment_prompt,

		reportType = org.acra.sender.HttpSender.Type.JSON,
		httpMethod = org.acra.sender.HttpSender.Method.PUT,
		formUri = "https://kpc.cloudant.com/acra-openconnect/_design/acra-storage/_update/report",
		formUriBasicAuthLogin="ineintlynnoveristimedesc",
		formUriBasicAuthPassword="mUmkrQIOKd3HalLf5AQuyxpA",

		formKey = ""
)

public class Application extends android.app.Application {

	public void onCreate() {
		super.onCreate();
		ACRA.init(this);
		System.loadLibrary("openconnect");
		System.loadLibrary("stoken");
		ProfileManager.init(getApplicationContext());
		FragCache.init();
	}
}
