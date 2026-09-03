package com.example.myapplication;

import android.app.Application;

public class BeachSafetyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Until someone flips the Dark Mode switch themselves, the app wears
        // whatever the phone is already wearing.
        SettingsActivity.applyStoredNightMode(this);
    }
}
