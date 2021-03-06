package edu.northwestern.cbits.purple_robot_manager;

import edu.northwestern.cbits.purple_robot_manager.config.LegacyJSONConfigFile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;

public class BootUpReceiver extends BroadcastReceiver
{
	public static final String BOOT_KEY = "system_last_boot";

    public void onReceive(Context context, Intent intent)
    {
    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    	
    	Editor e = prefs.edit();
    	
    	e.putLong(BootUpReceiver.BOOT_KEY, System.currentTimeMillis());
    	
    	e.commit();

    	ManagerService.setupPeriodicCheck(context);

    	LegacyJSONConfigFile.getSharedFile(context.getApplicationContext());
    }
}
