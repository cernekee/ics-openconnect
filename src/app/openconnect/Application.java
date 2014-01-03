package app.openconnect;

import app.openconnect.core.ProfileManager;

public class Application extends android.app.Application {

	public void onCreate() {
		ProfileManager.init(getApplicationContext());
	}
}
