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

package app.openconnect.api;

import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.List;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.VpnService;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import app.openconnect.VpnProfile;
import app.openconnect.core.OpenVPN.ConnectionStatus;
import app.openconnect.core.OpenVpnService;
import app.openconnect.core.OpenVpnService.LocalBinder;
import app.openconnect.core.ProfileManager;

public class ExternalOpenVPNService extends Service {

	private static final int SEND_TOALL = 0;

	final RemoteCallbackList<IOpenVPNStatusCallback> mCallbacks =
			new RemoteCallbackList<IOpenVPNStatusCallback>();

	private OpenVpnService mService;
	private ExternalAppDatabase mExtAppDb;	


	private ServiceConnection mConnection = new ServiceConnection() {


		@Override
		public void onServiceConnected(ComponentName className,
				IBinder service) {
			// We've bound to LocalService, cast the IBinder and get LocalService instance
			LocalBinder binder = (LocalBinder) service;
			mService = binder.getService();
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			mService =null;
		}

	};

	@Override
	public void onCreate() {
		super.onCreate();
		mExtAppDb = new ExternalAppDatabase(this);

		Intent intent = new Intent(getBaseContext(), OpenVpnService.class);
		intent.setAction(OpenVpnService.START_SERVICE);

		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
		mHandler.setService(this);
	}

	private final IOpenVPNAPIService.Stub mBinder = new IOpenVPNAPIService.Stub() {

        private void checkOpenVPNPermission()  throws SecurityRemoteException{
			PackageManager pm = getPackageManager();

			for (String apppackage:mExtAppDb.getExtAppList()) {
				ApplicationInfo app;
				try {
					app = pm.getApplicationInfo(apppackage, 0);
					if (Binder.getCallingUid() == app.uid) {
						return;
					}
				} catch (NameNotFoundException e) {
					// App not found. Remove it from the list
					mExtAppDb.removeApp(apppackage);
					e.printStackTrace();
				}

			}
			throw new SecurityException("Unauthorized OpenVPN API Caller");
		}

		@Override
		public List<APIVpnProfile> getProfiles() throws RemoteException {
			checkOpenVPNPermission();

			List<APIVpnProfile> profiles = new LinkedList<APIVpnProfile>();

			for(VpnProfile vp: ProfileManager.getProfiles())
				profiles.add(new APIVpnProfile(vp.getUUIDString(),vp.mName,true));

			return profiles;
		}

		@Override
		public void startProfile(String profileUUID) throws RemoteException {
			checkOpenVPNPermission();

			/*
			Intent shortVPNIntent = new Intent(Intent.ACTION_MAIN);
			shortVPNIntent.setClass(getBaseContext(),app.openconnect.LaunchVPN.class);
			shortVPNIntent.putExtra(app.openconnect.LaunchVPN.EXTRA_KEY,profileUUID);
			shortVPNIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(shortVPNIntent);
			*/
		}

		public void startVPN(String inlineconfig) throws RemoteException {
			checkOpenVPNPermission();

			/*
			ConfigParser cp = new ConfigParser();
			try {
				cp.parseConfig(new StringReader(inlineconfig));
				VpnProfile vp = cp.convertProfile();
				if(vp.checkProfile(getApplicationContext()) != R.string.no_error_found)
					throw new RemoteException(getString(vp.checkProfile(getApplicationContext())));


				ProfileManager.setTemporaryProfile(vp);
				//VPNLaunchHelper.startOpenVpn(vp, getBaseContext());


			} catch (IOException e) {
				throw new RemoteException(e.getMessage());
			} catch (ConfigParseError e) {
				throw new RemoteException(e.getMessage());
			}
			*/
		}

		@Override
		public boolean addVPNProfile(String name, String config) throws RemoteException {
			checkOpenVPNPermission();

			/*
			ConfigParser cp = new ConfigParser();
			try {
				cp.parseConfig(new StringReader(config));
				VpnProfile vp = cp.convertProfile();
				vp.mName = name;
				ProfileManager pm = ProfileManager.getInstance(getBaseContext());
				pm.addProfile(vp);
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			} catch (ConfigParseError e) {
				e.printStackTrace();
				return false;
			}
			*/

			return true;
		}


		@Override
		public Intent prepare(String packagename) {
			if (new ExternalAppDatabase(ExternalOpenVPNService.this).isAllowed(packagename))
				return null;

			Intent intent = new Intent();
			intent.setClass(ExternalOpenVPNService.this, ConfirmDialog.class);
			return intent;
		}

		@Override
		public Intent prepareVPNService() throws RemoteException {
			checkOpenVPNPermission();

			if(VpnService.prepare(ExternalOpenVPNService.this)==null)
				return null;
			else 
				return new  Intent(getBaseContext(), GrantPermissionsActivity.class);
		}


		@Override
		public void registerStatusCallback(IOpenVPNStatusCallback cb)
				throws RemoteException {
			checkOpenVPNPermission();

			if (cb != null) {
				cb.newStatus(mMostRecentState.vpnUUID, mMostRecentState.state,
						mMostRecentState.logmessage, mMostRecentState.level.name());
				mCallbacks.register(cb);
			}


		}

		@Override
		public void unregisterStatusCallback(IOpenVPNStatusCallback cb)
				throws RemoteException {
			checkOpenVPNPermission();

			if (cb != null)  
				mCallbacks.unregister(cb);
		}

		@Override
		public void disconnect() throws RemoteException {
			checkOpenVPNPermission();
			if(mService != null)
				mService.stopVPN();
		}
	};



	private UpdateMessage mMostRecentState;	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		mCallbacks.kill();
		unbindService(mConnection);
	}

	class UpdateMessage {
		public String state;
		public String logmessage;
		public ConnectionStatus level;
		public String vpnUUID;

		public UpdateMessage(String state, String logmessage, ConnectionStatus level) {
			this.state = state;
			this.logmessage = logmessage;
			this.level = level;
		}
	}

	private static final OpenVPNServiceHandler mHandler = new OpenVPNServiceHandler();


	static class OpenVPNServiceHandler extends Handler {
		WeakReference<ExternalOpenVPNService> service= null;

		private void setService(ExternalOpenVPNService eos)
		{
			service = new WeakReference<ExternalOpenVPNService>(eos);
		}

		@Override public void handleMessage(Message msg) {

			RemoteCallbackList<IOpenVPNStatusCallback> callbacks;
			switch (msg.what) {
			case SEND_TOALL:
				if(service ==null || service.get() == null)
					return;

				callbacks = service.get().mCallbacks;


				// Broadcast to all clients the new value.
				final int N = callbacks.beginBroadcast();
				for (int i=0; i<N; i++) {
					try {
						sendUpdate(callbacks.getBroadcastItem(i),(UpdateMessage) msg.obj);
					} catch (RemoteException e) {
						// The RemoteCallbackList will take care of removing
						// the dead object for us.
					}
				}
				callbacks.finishBroadcast();
				break;
			}
		}

		private void sendUpdate(IOpenVPNStatusCallback broadcastItem,
				UpdateMessage um) throws RemoteException 
				{
			broadcastItem.newStatus(um.vpnUUID, um.state, um.logmessage, um.level.name());
				}
	}


}