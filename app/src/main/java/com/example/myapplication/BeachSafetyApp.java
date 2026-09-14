package com.example.myapplication;

import android.app.Application;

public class BeachSafetyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Until someone flips the Dark Mode switch themselves, the app wears
        // whatever the phone is already wearing.
        SettingsActivity.applyStoredNightMode(this);
        // Language is remembered by the system from Android 13 on, and by us
        // below that — where it has to be re-applied here, before any activity
        // is built, so nothing is drawn in the wrong language first.
        SettingsActivity.applyStoredLanguage(this);
    }
}
