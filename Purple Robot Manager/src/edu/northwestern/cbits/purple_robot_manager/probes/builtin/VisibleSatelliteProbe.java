package edu.northwestern.cbits.purple_robot_manager.probes.builtin;

import java.util.ArrayList;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import edu.northwestern.cbits.purple_robot_manager.R;
import edu.northwestern.cbits.purple_robot_manager.probes.Probe;

public class VisibleSatelliteProbe extends Probe implements GpsStatus.Listener, GpsStatus.NmeaListener, LocationListener
{
	private static final boolean DEFAULT_ENABLED = false;

	protected Context _context = null;

	private ArrayList<Bundle> _satellites = new ArrayList<Bundle>();
	private long _lastCheck = 0;
	private long _startCheck = 0;
	private long _lastTransmit = 0;

	private boolean _listening = false;

	public Map<String, Object> configuration(Context context)
	{
		Map<String, Object> map = super.configuration(context);
		
		SharedPreferences prefs = Probe.getPreferences(context);

		long freq = Long.parseLong(prefs.getString("config_probe_satellites_frequency", Probe.DEFAULT_FREQUENCY));
		
		map.put(Probe.PROBE_FREQUENCY, freq);
		
		return map;
	}
	
	public String probeCategory(Context context)
	{
		return context.getString(R.string.probe_external_environment_category);
	}
	
	public void updateFromMap(Context context, Map<String, Object> params) 
	{
		super.updateFromMap(context, params);
		
		if (params.containsKey(Probe.PROBE_FREQUENCY))
		{
			Object frequency = params.get(Probe.PROBE_FREQUENCY);
			
			if (frequency instanceof Long)
			{
				SharedPreferences prefs = Probe.getPreferences(context);
				Editor e = prefs.edit();
				
				e.putString("config_probe_satellites_frequency", frequency.toString());
				e.commit();
			}
		}
	}

	@SuppressWarnings("deprecation")
	public PreferenceScreen preferenceScreen(PreferenceActivity activity)
	{
		PreferenceManager manager = activity.getPreferenceManager();

		PreferenceScreen screen = manager.createPreferenceScreen(activity);
		screen.setTitle(this.title(activity));
		screen.setSummary(R.string.summary_visible_satellite_probe_desc);

		String key = "satellites";

		CheckBoxPreference enabled = new CheckBoxPreference(activity);
		enabled.setTitle(R.string.title_enable_probe);
		enabled.setKey("config_probe_" + key + "_enabled");
		enabled.setDefaultValue(VisibleSatelliteProbe.DEFAULT_ENABLED);

		screen.addPreference(enabled);

		ListPreference duration = new ListPreference(activity);
		duration.setKey("config_probe_" + key + "_frequency");
		duration.setEntryValues(R.array.probe_satellite_frequency_values);
		duration.setEntries(R.array.probe_satellite_frequency_labels);
		duration.setTitle(R.string.probe_frequency_label);
		duration.setDefaultValue(Probe.DEFAULT_FREQUENCY);

		screen.addPreference(duration);

		return screen;
	}

	public void enable(Context context)
	{
		SharedPreferences prefs = Probe.getPreferences(context);
		
		Editor e = prefs.edit();
		e.putBoolean("config_probe_satellites_enabled", true);
		
		e.commit();
	}

	public void disable(Context context)
	{
		SharedPreferences prefs = Probe.getPreferences(context);
		
		Editor e = prefs.edit();
		e.putBoolean("config_probe_satellites_enabled", false);
		
		e.commit();
	}

	public boolean isEnabled(Context context)
	{
		LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

		if (super.isEnabled(context))
		{
	        this._context = context.getApplicationContext();

			long now = System.currentTimeMillis();

			SharedPreferences prefs = Probe.getPreferences(context);

			if (prefs.getBoolean("config_probe_satellites_enabled", VisibleSatelliteProbe.DEFAULT_ENABLED))
			{
				if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
				{
					locationManager.addGpsStatusListener(this);
					locationManager.addNmeaListener(this);

					synchronized(this)
					{
						long freq = Long.parseLong(prefs.getString("config_probe_satellite_frequency", Probe.DEFAULT_FREQUENCY));

						if (now - this._lastCheck > 60000 && now - this._lastCheck < freq) // Try to get satellites in 60 seconds...
						{
							locationManager.removeGpsStatusListener(this);
							locationManager.removeNmeaListener(this);

							locationManager.removeUpdates(this);
						}
						if (now - this._lastCheck > freq)
						{
							locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, this);

							this._lastCheck = now;
							this._startCheck = now;

							this._listening = true;
						}
					}
				}

				return true;
			}
		}

		locationManager.removeGpsStatusListener(this);
		locationManager.removeNmeaListener(this);

		locationManager.removeUpdates(this);

		return false;
	}

	public void updateFromJSON(Context context, JSONObject json) throws JSONException
	{
		// TODO...
	}

	public String title(Context context)
	{
		return context.getString(R.string.title_visible_satellite_probe);
	}

	@SuppressWarnings("unchecked")
	public void onGpsStatusChanged(int event)
	{
		long now = System.currentTimeMillis();

		if (now - this._lastTransmit < 10000) // 10s
			return;

		this._lastTransmit = now;

		switch (event)
		{
			case GpsStatus.GPS_EVENT_STARTED:
				break;
			case GpsStatus.GPS_EVENT_STOPPED:
				/* this._satellites.clear();

				Bundle b = new Bundle();

				b.putString("PROBE", this.name(this._context));
				b.putLong("TIMESTAMP", now / 1000);

				b.putParcelableArrayList("SATELLITES", (ArrayList<Bundle>) this._satellites.clone());
				b.putInt("SATELLITE_COUNT", this._satellites.size());

				synchronized(this)
				{
					this.transmitData(b);
				}
				*/
				break;
			case GpsStatus.GPS_EVENT_FIRST_FIX:
				break;
			case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
				LocationManager locationManager = (LocationManager) this._context.getSystemService(Context.LOCATION_SERVICE);

				GpsStatus status = locationManager.getGpsStatus(null);

				Iterable<GpsSatellite> sats = status.getSatellites();

				this._satellites.clear();

				float snrSum = 0;
				int snrCount = 0;

				for (GpsSatellite sat : sats)
				{
					Bundle satBundle = new Bundle();

					satBundle.putFloat("AZIMUTH", sat.getAzimuth());
					satBundle.putFloat("ELEVATION", sat.getElevation());
					satBundle.putInt("RANDOM_NUMBER", sat.getPrn());
					satBundle.putFloat("SIGNAL_RATIO", sat.getSnr());
					satBundle.putBoolean("HAS_ALMANAC", sat.hasAlmanac());
					satBundle.putBoolean("HAS_EPHEMERIS", sat.hasEphemeris());
					satBundle.putBoolean("USED_IN_FIX", sat.usedInFix());

					snrSum += sat.getSnr();
					snrCount += 1;

					this._satellites.add(satBundle);
				}

				Bundle bundle = new Bundle();

				bundle.putString("PROBE", this.name(this._context));
				bundle.putLong("TIMESTAMP", now / 1000);
				bundle.putParcelableArrayList("SATELLITES", (ArrayList<Bundle>) this._satellites.clone());
				bundle.putInt("SATELLITE_COUNT", this._satellites.size());

				if (snrSum > 0)
					snrSum = snrSum / snrCount;

				bundle.putFloat("AVERAGE_SIGNAL_RATIO", snrSum);

				synchronized(this)
				{
					this.transmitData(this._context, bundle);
				}

				break;
		}

		if (this._context != null)
		{
			LocationManager locationManager = (LocationManager) this._context.getSystemService(Context.LOCATION_SERVICE);

			if (now - this._startCheck > 10000 && this._listening)
			{
				synchronized(this)
				{
					this._listening = false;

					locationManager.removeUpdates(this);

					this._startCheck = now;
				}

				this.isEnabled(this._context);
			}

			locationManager = null;
		}
	}

	public String name(Context context)
	{
		return "edu.northwestern.cbits.purple_robot_manager.probes.builtin.VisibleSatelliteProbe";
	}

	public void onNmeaReceived(long timestamp, String nmea)
	{
//		Log.e("PRM", "NMEA (" + timestamp + "): " + nmea);
	}

	public void onLocationChanged(Location location)
	{
//		Log.e("PRM", "LOCATION CHANGED: " + location);
	}

	public String summarizeValue(Context context, Bundle bundle)
	{
		int count = (int) bundle.getDouble("SATELLITE_COUNT");
		float snr = bundle.getFloat("AVERAGE_SIGNAL_RATIO");

		return String.format(context.getResources().getString(R.string.summary_satellite_probe), count, snr);
	}

	public void onProviderDisabled(String provider)
	{

	}

	public void onProviderEnabled(String provider)
	{

	}

	public void onStatusChanged(String provider, int status, Bundle extras)
	{

	}
	

	private Bundle bundleForSatelliteArray(Context context, ArrayList<Bundle> objects)
	{
		Bundle bundle = new Bundle();

		for (Bundle value : objects)
		{
			ArrayList<String> keys = new ArrayList<String>();

			String key = String.format(context.getString(R.string.display_satellite_label), value.getInt("RANDOM_NUMBER"));

			Bundle satBundle = new Bundle();

			satBundle.putInt(context.getString(R.string.display_satellite_id_label), value.getInt("RANDOM_NUMBER"));
			satBundle.putFloat(context.getString(R.string.display_satellite_elevation_label), value.getFloat("ELEVATION"));
			satBundle.putFloat(context.getString(R.string.display_satellite_azimuth_label), value.getFloat("AZIMUTH"));
			satBundle.putFloat(context.getString(R.string.display_satellite_signal_label), value.getFloat("SIGNAL_RATIO"));
			
			if (value.getBoolean("HAS_EPHEMERIS"))
				satBundle.putString(context.getString(R.string.display_satellite_ephemeris_label), context.getString(R.string.display_satellite_has_ephemeris));
			else
				satBundle.putString(context.getString(R.string.display_satellite_ephemeris_label), context.getString(R.string.display_satellite_no_ephemeris));

			if (value.getBoolean("HAS_ALMANAC"))
				satBundle.putString(context.getString(R.string.display_satellite_almanac_label), context.getString(R.string.display_satellite_has_almanac));
			else
				satBundle.putString(context.getString(R.string.display_satellite_almanac_label), context.getString(R.string.display_satellite_no_almanac));
			
			keys.add(context.getString(R.string.display_satellite_id_label));
			keys.add(context.getString(R.string.display_satellite_elevation_label));
			keys.add(context.getString(R.string.display_satellite_azimuth_label));
			keys.add(context.getString(R.string.display_satellite_signal_label));
			keys.add(context.getString(R.string.display_satellite_ephemeris_label));
			keys.add(context.getString(R.string.display_satellite_almanac_label));

			satBundle.putStringArrayList("KEY_ORDER", keys);

			bundle.putBundle(key, satBundle);
		}

		return bundle;
	}

	public Bundle formattedBundle(Context context, Bundle bundle)
	{
		Bundle formatted = super.formattedBundle(context, bundle);

		@SuppressWarnings("unchecked")
		ArrayList<Bundle> array = (ArrayList<Bundle>) bundle.get("SATELLITES");
		int count = (int) bundle.getDouble("SATELLITE_COUNT");

		Bundle satsBundle = this.bundleForSatelliteArray(context, array);

		formatted.putBundle(String.format(context.getString(R.string.display_satellites_title), count), satsBundle);

		formatted.putFloat(context.getString(R.string.display_satellites_signal_ratio), bundle.getFloat("AVERAGE_SIGNAL_RATIO"));

		
		return formatted;
	};

}
