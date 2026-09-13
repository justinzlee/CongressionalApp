package com.example.myapplication;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.example.myapplication.util.EdgeToEdge;
import com.google.android.material.materialswitch.MaterialSwitch;

public class SettingsActivity extends AppCompatActivity {
    public static final String PREFS_NAME = "BeachSafetyPrefs";
    public static final String KEY_USE_METRIC = "use_metric";
    public static final String KEY_DARK_MODE = "dark_mode";
    public static final String KEY_ALERTS_SAVED_ONLY = "alerts_saved_only";
    public static final String KEY_RANKING_SAVED_ONLY = "ranking_saved_only";

    /**
     * Puts the app in whichever appearance the user last picked, and leaves it
     * following the phone's own light/dark setting until they pick one. A phone
     * already in dark mode should open a dark app, and the same the other way.
     */
    public static void applyStoredNightMode(Context context) {
        SharedPreferences prefs =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.contains(KEY_DARK_MODE)) {
            AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            return;
        }
        AppCompatDelegate.setDefaultNightMode(prefs.getBoolean(KEY_DARK_MODE, false)
            ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
    }

    /** True when the app is drawing itself dark right now, however it got there. */
    private static boolean isShowingDark(Context context) {
        int nightFlags = context.getResources().getConfiguration().uiMode
            & Configuration.UI_MODE_NIGHT_MASK;
        return nightFlags == Configuration.UI_MODE_NIGHT_YES;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        EdgeToEdge.applyTopInset(findViewById(R.id.settingsToolbarContainer));
        EdgeToEdge.applyBottomInset(findViewById(R.id.tabBar));

        TabBar.setup(this, TabBar.Tab.MORE);

        // The lessons that used to sit here now have their own Learn tab.

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        MaterialSwitch metricSwitch = findViewById(R.id.metricUnitSwitch);
        metricSwitch.setChecked(prefs.getBoolean(KEY_USE_METRIC, false));
        // The whole row is the target, per the design's settings list.
        bindRow(findViewById(R.id.metricRow), metricSwitch, isChecked ->
            prefs.edit().putBoolean(KEY_USE_METRIC, isChecked).apply());

        // Narrows the Alerts tab to the user's own beaches. Off, it still only
        // covers what is near them or saved — this drops the distance half.
        MaterialSwitch savedAlertsSwitch = findViewById(R.id.savedAlertsSwitch);
        savedAlertsSwitch.setChecked(prefs.getBoolean(KEY_ALERTS_SAVED_ONLY, false));
        bindRow(findViewById(R.id.savedAlertsRow), savedAlertsSwitch, isChecked -> {
            prefs.edit().putBoolean(KEY_ALERTS_SAVED_ONLY, isChecked).apply();
            // The badge counts what the Alerts tab would show, so it has to be
            // re-counted here rather than waiting for that tab to be opened.
            TabBar.refreshAlertBadge(this);
        });

        // The same narrowing for "Best beach for…", kept apart from the Alerts
        // one: wanting fewer warnings says nothing about where you would go for
        // a swim.
        MaterialSwitch savedRankingSwitch = findViewById(R.id.savedRankingSwitch);
        savedRankingSwitch.setChecked(prefs.getBoolean(KEY_RANKING_SAVED_ONLY, false));
        bindRow(findViewById(R.id.savedRankingRow), savedRankingSwitch, isChecked ->
            prefs.edit().putBoolean(KEY_RANKING_SAVED_ONLY, isChecked).apply());

        MaterialSwitch darkModeSwitch = findViewById(R.id.darkModeSwitch);
        // With no choice stored we are following the system, so the switch
        // reports the appearance actually on screen, not a hardcoded default.
        darkModeSwitch.setChecked(isShowingDark(this));
        bindRow(findViewById(R.id.darkModeRow), darkModeSwitch, isChecked -> {
            prefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply();
            AppCompatDelegate.setDefaultNightMode(isChecked
                ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        });

        View contactRow = findViewById(R.id.contactDeveloperRow);
        if (contactRow != null) {
            contactRow.setOnClickListener(v -> {
                Intent intent = new Intent(this, ContactDeveloperActivity.class);
                startActivity(intent);
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Alerts may have been read since this screen was last in front.
        TabBar.refreshAlertBadge(this);
    }

    private interface OnToggle {
        void onToggled(boolean isChecked);
    }

    private void bindRow(View row, MaterialSwitch toggle, OnToggle callback) {
        row.setOnClickListener(v -> {
            boolean next = !toggle.isChecked();
            toggle.setChecked(next);
            callback.onToggled(next);
        });
    }
}
