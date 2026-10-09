package com.example.myapplication.util;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.myapplication.R;
import com.example.myapplication.SettingsActivity;
import java.util.Locale;

public class UnitConverter {

    /** One mile in kilometres, for every conversion in both directions. */
    public static final double KM_PER_MILE = 1.60934;

    public static String formatTemperature(Context context, Double tempF) {
        if (tempF == null) return "--";
        if (useMetric(context)) {
            double tempC = (tempF - 32) * 5 / 9;
            return String.format(Locale.US, "%.1f°C", tempC);
        } else {
            return String.format(Locale.US, "%.1f°F", tempF);
        }
    }

    public static String formatWindSpeed(Context context, Double speedMph) {
        if (speedMph == null) return "--";
        if (useMetric(context)) {
            double speedKmh = speedMph * KM_PER_MILE;
            return String.format(Locale.US, "%.1f km/h", speedKmh);
        } else {
            return String.format(Locale.US, "%.1f mph", speedMph);
        }
    }

    /** True when the user has asked for metric units in Settings. */
    public static boolean useMetric(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(SettingsActivity.KEY_USE_METRIC, false);
    }

    /** Wave height in the user's unit, without the unit itself - for axis ticks. */
    public static double waveHeightValue(Context context, double heightFeet) {
        return useMetric(context) ? heightFeet / 3.28084 : heightFeet;
    }

    /** "m" or "ft", to label a value from {@link #waveHeightValue}. */
    public static String waveHeightUnit(Context context) {
        return useMetric(context) ? "m" : "ft";
    }

    /**
     * Wave height for an axis tick or scale caption: "6" in feet, "1.8" in
     * metres. A trailing ".0" is only noise at that size.
     */
    public static String waveHeightTick(Context context, double heightFeet) {
        double value = Math.round(waveHeightValue(context, heightFeet) * 10d) / 10d;
        return value == Math.rint(value)
            ? String.format(Locale.US, "%.0f", value)
            : String.format(Locale.US, "%.1f", value);
    }

    public static String formatWaveHeight(Context context, Double heightFeet) {
        if (heightFeet == null) return "--";
        return String.format(Locale.US, "%.1f %s",
            waveHeightValue(context, heightFeet), waveHeightUnit(context));
    }

    public static String formatVisibility(Context context, Double visibilityMiles) {
        if (visibilityMiles == null) return "--";
        if (useMetric(context)) {
            double visibilityKm = visibilityMiles * KM_PER_MILE;
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
        
        // Harden against floating point errors that could make a > 1
        a = Math.min(1.0, Math.max(0.0, a));
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }

    /** "km" or "mi", to label a distance already in the user's unit. */
    public static String distanceUnit(Context context) {
        return useMetric(context) ? "km" : "mi";
    }

    public static String formatDistance(Context context, Double distanceMiles) {
        if (distanceMiles == null) return "";
        return useMetric(context)
            ? context.getString(R.string.distance_away_km, distanceMiles * KM_PER_MILE)
            : context.getString(R.string.distance_away, distanceMiles);
    }
}
