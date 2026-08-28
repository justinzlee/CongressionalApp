package com.example.myapplication;

import android.app.Application;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

public class BeachSafetyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Nocturne is a dark design system, so the app opens dark unless the
        // user has explicitly turned Dark Mode off.
        SharedPreferences prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE);
        boolean darkMode = prefs.getBoolean(
            SettingsActivity.KEY_DARK_MODE, SettingsActivity.DEFAULT_DARK_MODE);
        AppCompatDelegate.setDefaultNightMode(darkMode
            ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }
}
