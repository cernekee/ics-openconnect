package app.openconnect.core;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import app.openconnect.VpnProfile;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;

public class ProfileManager {
	private static final String PREFS_NAME =  "VPNList";



	private static final String ONBOOTPROFILE = "onBootProfile";



	private static ProfileManager instance;



	private static VpnProfile mLastConnectedVpn=null;
	private HashMap<String,VpnProfile> profiles=new HashMap<String, VpnProfile>();
	private static VpnProfile tmpprofile=null;


	private static VpnProfile get(String key) {
		if (tmpprofile!=null && tmpprofile.getUUIDString().equals(key))
			return tmpprofile;
			
		if(instance==null)
			return null;
		return instance.profiles.get(key);
		
	}


	
	private ProfileManager() { }
	
	private static void checkInstance(Context context) {
		if(instance == null) {
			instance = new ProfileManager();
			instance.loadVPNList(context);
		}
	}

	synchronized public static ProfileManager getInstance(Context context) {
		checkInstance(context);
		return instance;
	}
	
	public static void setConntectedVpnProfileDisconnected(Context c) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
		Editor prefsedit = prefs.edit();
		prefsedit.putString(ONBOOTPROFILE, null);
		prefsedit.apply();
		
	}

	public static void setConnectedVpnProfile(Context c, VpnProfile connectedrofile) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
		Editor prefsedit = prefs.edit();
		
		prefsedit.putString(ONBOOTPROFILE, connectedrofile.getUUIDString());
		prefsedit.apply();
		mLastConnectedVpn=connectedrofile;
		
	}
	
	public static VpnProfile getOnBootProfile(Context c) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);

		boolean useStartOnBoot = prefs.getBoolean("restartvpnonboot", false);

		
		String mBootProfileUUID = prefs.getString(ONBOOTPROFILE,null);
		if(useStartOnBoot && mBootProfileUUID!=null)
			return get(c, mBootProfileUUID);
		else 
			return null;
	}
	
	
	
	
	public Collection<VpnProfile> getProfiles() {
		return profiles.values();
	}
	
	public VpnProfile getProfileByName(String name) {
		for (VpnProfile vpnp : profiles.values()) {
			if(vpnp.getName().equals(name)) {
				return vpnp;
			}
		}
		return null;			
	}

	public void saveProfileList(Context context) {
		SharedPreferences sharedprefs = context.getSharedPreferences(PREFS_NAME,Activity.MODE_PRIVATE);
		Editor editor = sharedprefs.edit();
		editor.putStringSet("vpnlist", profiles.keySet()).commit();
	}

	public void addProfile(VpnProfile profile) {
		profiles.put(profile.getUUID().toString(),profile);
		
	}
	
	public static void setTemporaryProfile(VpnProfile tmp) {
		ProfileManager.tmpprofile = tmp;
	}
	
	private void loadVPNList(Context context) {
		profiles = new HashMap<String, VpnProfile>();
		SharedPreferences listpref = context.getSharedPreferences(PREFS_NAME,Activity.MODE_PRIVATE);
		Set<String> vlist = listpref.getStringSet("vpnlist", null);
		if(vlist==null){
			vlist = new HashSet<String>();
		}

		for (String vpnentry : vlist) {
			SharedPreferences sp = context.getSharedPreferences(vpnentry, Activity.MODE_PRIVATE);
			VpnProfile vp = new VpnProfile(sp.getString("profile_name", ""), vpnentry);
			profiles.put(vpnentry, vp);
		}
	}

	public int getNumberOfProfiles() {
		return profiles.size();
	}

	public void removeProfile(Context context,VpnProfile profile) {
		String vpnentry = profile.getUUID().toString();
		profiles.remove(vpnentry);
		saveProfileList(context);
		// FIXME: delete prefs file
		if(mLastConnectedVpn==profile)
			mLastConnectedVpn=null;
	}



	public static VpnProfile get(Context context, String profileUUID) {
		checkInstance(context);
		return get(profileUUID);
	}



	public static VpnProfile getLastConnectedVpn() {
		return mLastConnectedVpn;
	}

}
