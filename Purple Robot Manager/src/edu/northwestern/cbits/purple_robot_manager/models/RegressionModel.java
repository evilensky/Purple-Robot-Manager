package edu.northwestern.cbits.purple_robot_manager.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import android.content.Context;
import android.net.Uri;
import edu.northwestern.cbits.purple_robot_manager.util.Slugify;

public class RegressionModel extends TrainedModel 
{
	public static final String TYPE = "linear-regression";

	private double _intercept = 0.0;
	
	private Map<String, Double> _coefficients = new HashMap<String, Double>();
	private Set<String> _variables = new HashSet<String>();
	
	public RegressionModel(Context context, Uri uri) 
	{
		super(context, uri);
	}

	protected void generateModel(Context context, String modelString) 
	{
		/*
		08-01 11:39:30.163: E/PR(15876): undefined_feature_value_dt_calmr1 =
				08-01 11:39:30.163: E/PR(15876):       0.4026 * networkprobe_hostname=?,android-a6e300e929cef636 +
				08-01 11:39:30.163: E/PR(15876):       0.4026 * networkprobe_hostname=android-a6e300e929cef636 +
				08-01 11:39:30.163: E/PR(15876):       0.4026 * networkprobe_ip_address=?,192168121 +
				08-01 11:39:30.163: E/PR(15876):       0.4026 * networkprobe_ip_address=192168121 +
				08-01 11:39:30.163: E/PR(15876):      -2.8561 * robothealthprobe_cpu_usage +
				08-01 11:39:30.163: E/PR(15876):      -1.1707 * telephonyprobe_call_state +
				08-01 11:39:30.163: E/PR(15876):       0.286  * wifiaccesspointsprobe_access_point_count +
				08-01 11:39:30.163: E/PR(15876):       0.4026 * wifiaccesspointsprobe_current_bssid=?,640f28c1da89 +
				08-01 11:39:30.163: E/PR(15876):       0.0191 * wifiaccesspointsprobe_current_rssi +
				08-01 11:39:30.163: E/PR(15876):       0.4026 * wifiaccesspointsprobe_current_ssid=allison-and-alex +
				08-01 11:39:30.163: E/PR(15876):       2.3794
		*/

		for (String line : modelString.split("[\\r\\n]+"))
		{
			if (line.startsWith("  "))
			{
				try
				{
					this._intercept = Double.parseDouble(line);
				}
				catch (NumberFormatException e)
				{
					String[] toks = line.split("\\*");
					
					String coef = toks[0].trim();
					String args = toks[1].trim();
					
					int index = line.indexOf("=");
					
					if (index == -1)
					{
						args = args.replaceAll(Pattern.quote("+"), "").trim();
						this._coefficients.put(args, Double.parseDouble(coef));
						
						this._variables.add(args);
					}
					else
					{
						String[] argToks = args.split("=");
						
						String probe = argToks[0].trim();
						this._variables.add(probe);
						
						ArrayList<String> values = new ArrayList<String>();
						
						String probeValues = argToks[1].trim();
						
						if (probeValues.indexOf(",") == -1)
							values.add(probeValues);
						else
						{
							String[] valueToks = probeValues.split(",");
							
							for (String tok : valueToks)
							{
								values.add(tok.trim());
							}
						}
						
						for (String value : values)
						{
							value = value.replaceAll(Pattern.quote("+"), "").trim();
							
							String coefKey = probe + "_" + value;
							
							this._coefficients.put(coefKey, Double.parseDouble(coef));
						}
					}
				}
			}
		}
	}
	
	protected Object evaluateModel(Context context, HashMap<String, Object> snapshot) 
	{
		ArrayList<String> requiredKeys = new ArrayList<String>();
		requiredKeys.addAll(this._variables);

		double prediction = this._intercept;
		
		for (String key : snapshot.keySet())
		{
			Object value = snapshot.get(key);
			
			if (requiredKeys.contains(key))
			{
				Double coef = this._coefficients.get(key);
				
				double valueValue = 1.0;
				
				if (value instanceof Double || value instanceof Float || value instanceof Long || value instanceof Integer)
					valueValue = Double.parseDouble("" + value);

				if (coef == null)
				{
					coef = this._coefficients.get(key + "_" + Slugify.slugify(value.toString()));

					if (coef == null)
					{
						coef = this._coefficients.get(key + "_?");
						
						if (coef == null)
							coef = Double.valueOf(0.0);
					}
				}
				
				prediction += (coef * valueValue);
				
				requiredKeys.remove(key);
			}
		}
		
		return Double.valueOf(prediction);
	}

	public String modelType() 
	{
		return RegressionModel.TYPE;
	}
}
