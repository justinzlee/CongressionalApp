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

    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 3958.8; // Miles
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }

    public static String formatDistance(Context context, Double distanceMiles) {
        if (distanceMiles == null) return "";
        SharedPreferences prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
        boolean useMetric = prefs.getBoolean(SettingsActivity.KEY_USE_METRIC, false);
        
        if (useMetric) {
            double distanceKm = distanceMiles * 1.60934;
            return String.format(Locale.US, "%.1f km away", distanceKm);
        } else {
            return String.format(Locale.US, "%.1f miles away", distanceMiles);
        }
    }
}
