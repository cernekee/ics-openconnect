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

import org.acra.ACRA;
import org.acra.ACRAConfiguration;

import android.content.Context;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import app.openconnect.FragActivity;
import app.openconnect.R;

public class CommonMenu {

	private static final int MENU_SETTINGS = 15;
	private static final int MENU_SECURID = 20;
	private static final int MENU_REPORT_PROBLEM = 25;
	private static final int MENU_ABOUT = 30;

	private Context mContext;

	public CommonMenu(Context ctx, Menu menu, boolean isConnected) {
		mContext = ctx;
		menu.add(Menu.NONE, MENU_SETTINGS, Menu.NONE, R.string.generalsettings)
			.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
		menu.add(Menu.NONE, MENU_SECURID, Menu.NONE, R.string.securid_info)
			.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
		menu.add(Menu.NONE, MENU_REPORT_PROBLEM, Menu.NONE, R.string.report_problem)
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

	private void sendProblemReport() {
		ACRAConfiguration cfg = ACRA.getConfig();
		cfg.setResDialogText(R.string.problem_dialog_text);
		cfg.setResDialogCommentPrompt(R.string.problem_dialog_comment_prompt);
		ACRA.setConfig(cfg);
		ACRA.getErrorReporter().handleException(null);

		// FIXME: we really want to restore the default strings after the report dialog
		// is finished, but changing them here would override the problem_dialog_* strings
		// set above.
		//ACRA.setConfig(ACRA.getNewDefaultConfig((Application)getApplicationContext()));
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		final int itemId = item.getItemId();
		if (itemId == MENU_ABOUT) {
			return startFragActivity("AboutFragment");
		} else if (itemId == MENU_SECURID) {
			return startFragActivity("TokenParentFragment");
		} else if (itemId == MENU_REPORT_PROBLEM) {
			sendProblemReport();
			return true;
		} else if (itemId == MENU_SETTINGS) {
			return startFragActivity("GeneralSettings");
		} else {
			return false;
		}
	}

}
