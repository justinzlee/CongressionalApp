package com.example.myapplication.util;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.myapplication.SettingsActivity;
import java.util.Locale;

public class UnitConverter {
    public static String formatTemperature(Context context, Double tempF) {
        if (tempF == null) return "--";
        SharedPreferences prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
        boolean useMetric = prefs.getBoolean(SettingsActivity.KEY_USE_METRIC, false);
        
        if (useMetric) {
            double tempC = (tempF - 32) * 5 / 9;
            return String.format(Locale.US, "%.1f°C", tempC);
        } else {
            return String.format(Locale.US, "%.1f°F", tempF);
        }
    }

    public static String formatWindSpeed(Context context, Double speedMph) {
        if (speedMph == null) return "--";
        SharedPreferences prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
        boolean useMetric = prefs.getBoolean(SettingsActivity.KEY_USE_METRIC, false);
        
        if (useMetric) {
            double speedKmh = speedMph * 1.60934;
            return String.format(Locale.US, "%.1f km/h", speedKmh);
        } else {
            return String.format(Locale.US, "%.1f mph", speedMph);
        }
    }

    public static String formatWaveHeight(Context context, Double heightFeet) {
        if (heightFeet == null) return "--";
        SharedPreferences prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
        boolean useMetric = prefs.getBoolean(SettingsActivity.KEY_USE_METRIC, false);
        
        if (useMetric) {
            double heightMeters = heightFeet / 3.28084;
            return String.format(Locale.US, "%.1f m", heightMeters);
        } else {
            return String.format(Locale.US, "%.1f ft", heightFeet);
        }
    }

    public static String formatVisibility(Context context, Double visibilityMiles) {
        if (visibilityMiles == null) return "--";
        SharedPreferences prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
        boolean useMetric = prefs.getBoolean(SettingsActivity.KEY_USE_METRIC, false);
        
        if (useMetric) {
            double visibilityKm = visibilityMiles * 1.60934;
            return String.format(Locale.US, "%.1f km", visibilityKm);
        } else {
            return String.format(Locale.US, "%.1f mi", visibilityMiles);
        }
    }
}
