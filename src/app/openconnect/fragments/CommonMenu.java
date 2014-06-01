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

package app.openconnect.fragments;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.Menu;
import android.view.MenuItem;
import app.openconnect.FragActivity;
import app.openconnect.R;

public class CommonMenu {

	private static final int MENU_SETTINGS = 15;
	private static final int MENU_SECURID = 20;
	private static final int MENU_FEEDBACK = 25;
	private static final int MENU_TRANSLATE = 29;
	private static final int MENU_ABOUT = 30;

	private static final String translateURL = "https://www.transifex.com/projects/p/ics-openconnect/";

	private Context mContext;

	public CommonMenu(Context ctx, Menu menu, boolean isConnected) {
		mContext = ctx;
		menu.add(Menu.NONE, MENU_SETTINGS, Menu.NONE, R.string.generalsettings)
			.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
		menu.add(Menu.NONE, MENU_SECURID, Menu.NONE, R.string.securid_info)
			.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
		menu.add(Menu.NONE, MENU_FEEDBACK, Menu.NONE, R.string.send_feedback)
			.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
		menu.add(Menu.NONE, MENU_TRANSLATE, Menu.NONE, R.string.help_with_translations)
			.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
		menu.add(Menu.NONE, MENU_ABOUT, Menu.NONE, R.string.about_openconnect)
			.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
	}

	private boolean startFragActivity(String fragName) {
		Intent intent = new Intent(mContext, FragActivity.class);
		intent.putExtra(FragActivity.EXTRA_FRAGMENT_NAME, fragName);
		mContext.startActivity(intent);
		return true;
	}

	private boolean startBrowserActivity(String URL) {
		Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(URL));
		mContext.startActivity(intent);
		return true;
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		final int itemId = item.getItemId();
		if (itemId == MENU_ABOUT) {
			return startFragActivity("AboutFragment");
		} else if (itemId == MENU_SECURID) {
			return startFragActivity("TokenParentFragment");
		} else if (itemId == MENU_TRANSLATE) {
			return startBrowserActivity(translateURL);
		} else if (itemId == MENU_FEEDBACK) {
			return startFragActivity("FeedbackFragment");
		} else if (itemId == MENU_SETTINGS) {
			return startFragActivity("GeneralSettings");
		} else {
			return false;
		}
	}

}
