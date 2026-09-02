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

    /**
     * The Beach Safety Palette leads with sand and sunlight, so light is the
     * app's resting state and dark is the night mode you opt into.
     */
    public static final boolean DEFAULT_DARK_MODE = false;

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

        MaterialSwitch darkModeSwitch = findViewById(R.id.darkModeSwitch);
        darkModeSwitch.setChecked(prefs.getBoolean(KEY_DARK_MODE, DEFAULT_DARK_MODE));
        bindRow(findViewById(R.id.darkModeRow), darkModeSwitch, isChecked -> {
            prefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply();
            AppCompatDelegate.setDefaultNightMode(isChecked
                ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        });
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
