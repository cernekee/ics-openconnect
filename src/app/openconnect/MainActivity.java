package app.openconnect;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import app.openconnect.core.OpenConnectManagementThread;
import app.openconnect.core.OpenVpnService;
import app.openconnect.core.VPNConnector;
import app.openconnect.fragments.AboutFragment;
import app.openconnect.fragments.FaqFragment;
import app.openconnect.fragments.GeneralSettings;
import app.openconnect.fragments.StatusFragment;
import app.openconnect.fragments.VPNProfileList;

public class MainActivity extends Activity {

	public static final String TAG = "OpenConnect";

	private Tab mVpnListTab;
	private Tab mSettingsTab;
	private Tab mStatusTab;
	private Tab mFaqtab;
	private Tab mAboutTab;

	private int mConnectionState = OpenConnectManagementThread.STATE_DISCONNECTED;
	private VPNConnector mConn;

	@Override
	protected void onCreate(android.os.Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		ActionBar bar = getActionBar();
		bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

		mVpnListTab = bar.newTab().setText(R.string.vpn_list_title);
		mSettingsTab = bar.newTab().setText(R.string.generalsettings);
		mStatusTab = bar.newTab().setText(R.string.status);
		mFaqtab = bar.newTab().setText(R.string.faq);
		mAboutTab = bar.newTab().setText(R.string.about);

		mVpnListTab.setTabListener(new TabListener<VPNProfileList>("profiles",
				VPNProfileList.class));
		mSettingsTab.setTabListener(new TabListener<GeneralSettings>("settings",
				GeneralSettings.class));
		mStatusTab.setTabListener(new TabListener<StatusFragment>("status",
				StatusFragment.class));
		mFaqtab.setTabListener(new TabListener<FaqFragment>("faq",
				FaqFragment.class));
		mAboutTab.setTabListener(new TabListener<AboutFragment>("about",
				AboutFragment.class));

		bar.addTab(mVpnListTab);
		bar.addTab(mSettingsTab);
		bar.addTab(mStatusTab);
	}

	private void updateUI(OpenVpnService service) {
		int newState = service.getConnectionState();
		if (mConnectionState == newState) {
			return;
		}

		ActionBar bar = getActionBar();

		if (newState == OpenConnectManagementThread.STATE_DISCONNECTED) {
			bar.addTab(mVpnListTab, 0);
			bar.addTab(mSettingsTab, 1);
		} else if (mConnectionState == OpenConnectManagementThread.STATE_DISCONNECTED) {
			bar.removeTab(mVpnListTab);
			bar.removeTab(mSettingsTab);
		}
		mConnectionState = newState;
	}

	@Override
	protected void onStart() {
		super.onStart();

		mConn = new VPNConnector(this) {
			@Override
			public void onUpdate(OpenVpnService service) {
				updateUI(service);
			}
		};
	}

	@Override
	protected void onStop() {
		mConn.unbind();
		super.onStop();
	}

	protected class TabListener<T extends Fragment> implements
			ActionBar.TabListener {
		private Fragment mFragment;
		private String mTag;
		private Class<T> mClass;

		public TabListener(String tag, Class<T> clz) {
			mTag = tag;
			mClass = clz;

			// Check to see if we already have a fragment for this tab, probably
			// from a previously saved state. If so, deactivate it, because our
			// initial state is that a tab isn't shown.
			mFragment = getFragmentManager().findFragmentByTag(mTag);
			if (mFragment != null && !mFragment.isDetached()) {
				FragmentTransaction ft = getFragmentManager()
						.beginTransaction();
				ft.detach(mFragment);
				ft.commit();
			}
		}

		public void onTabSelected(Tab tab, FragmentTransaction ft) {
			if (mFragment == null) {
				mFragment = Fragment.instantiate(MainActivity.this,
						mClass.getName());
				ft.add(android.R.id.content, mFragment, mTag);
			} else {
				ft.attach(mFragment);
			}
		}

		public void onTabUnselected(Tab tab, FragmentTransaction ft) {
			if (mFragment != null) {
				ft.detach(mFragment);
			}
		}

		@Override
		public void onTabReselected(Tab tab, FragmentTransaction ft) {

		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		System.out.println(data);

	}

}
