package com.example.myapplication.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.myapplication.SettingsActivity;

/**
 * The last real fix the device gave us, kept so screens other than the home
 * one can tell what is near the user.
 *
 * <p>Only the home screen asks for location, and it holds the answer in fields
 * that die with it. The Alerts tab needs the same answer to know which beaches
 * are within range, and so does the tab bar's badge — which is drawn on every
 * screen in the app, none of which is going to start a location request of its
 * own just to size a number. Writing the fix down once, where it survives both
 * the screen and the process, is what lets those read it for free.
 *
 * <p>What is stored is the device's own position, never the coastal city the
 * user picked to browse. Choosing to look at San Diego from Oregon is a
 * browsing action; it should not start warning about rip currents nine hundred
 * miles from where the user is standing.
 */
public class LastKnownLocation {
    private static final String KEY_LATITUDE = "last_known_latitude";
    private static final String KEY_LONGITUDE = "last_known_longitude";

    private final SharedPreferences prefs;

    public LastKnownLocation(Context context) {
        this.prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Whether the device has ever given this app a position. */
    public boolean isKnown() {
        return prefs.contains(KEY_LATITUDE) && prefs.contains(KEY_LONGITUDE);
    }

    /** Only meaningful when {@link #isKnown()}; zero stands in otherwise. */
    public double getLatitude() {
        return read(KEY_LATITUDE);
    }

    public double getLongitude() {
        return read(KEY_LONGITUDE);
    }

    public void remember(double latitude, double longitude) {
        prefs.edit()
            .putLong(KEY_LATITUDE, Double.doubleToRawLongBits(latitude))
            .putLong(KEY_LONGITUDE, Double.doubleToRawLongBits(longitude))
            .apply();
    }

    /**
     * Stored as the double's own bits rather than through {@code putFloat},
     * which would round a coordinate off by a few metres on the way in and out.
     * Nothing here needs that precision, but a stored number that does not read
     * back as the one written is a bad thing to leave lying around.
     */
    private double read(String key) {
        return Double.longBitsToDouble(prefs.getLong(key, 0L));
    }
}
