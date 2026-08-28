package com.example.myapplication;

import android.content.SharedPreferences;
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

    /** Nocturne is a dark design system, so dark is the app's resting state. */
    public static final boolean DEFAULT_DARK_MODE = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        EdgeToEdge.applyTopInset(findViewById(R.id.settingsToolbarContainer));
        EdgeToEdge.applyBottomInset(findViewById(R.id.tabBar));

        TabBar.setup(this, TabBar.Tab.MORE);

        findViewById(R.id.guideRow).setOnClickListener(
            v -> startActivity(new android.content.Intent(this, RipGuideActivity.class)));

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        MaterialSwitch metricSwitch = findViewById(R.id.metricUnitSwitch);
        metricSwitch.setChecked(prefs.getBoolean(KEY_USE_METRIC, false));
        // The whole row is the target, per the design's settings list.
        bindRow(findViewById(R.id.metricRow), metricSwitch, isChecked ->
            prefs.edit().putBoolean(KEY_USE_METRIC, isChecked).apply());

        MaterialSwitch darkModeSwitch = findViewById(R.id.darkModeSwitch);
        darkModeSwitch.setChecked(prefs.getBoolean(KEY_DARK_MODE, DEFAULT_DARK_MODE));
        bindRow(findViewById(R.id.darkModeRow), darkModeSwitch, isChecked -> {
            prefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply();
            AppCompatDelegate.setDefaultNightMode(isChecked
                ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        });
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
