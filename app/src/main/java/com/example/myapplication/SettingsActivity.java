package com.example.myapplication;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.example.myapplication.util.EdgeToEdge;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsActivity extends AppCompatActivity {
    public static final String PREFS_NAME = "BeachSafetyPrefs";
    public static final String KEY_USE_METRIC = "use_metric";
    public static final String KEY_DARK_MODE = "dark_mode";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        MaterialToolbar toolbar = findViewById(R.id.settingsToolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        EdgeToEdge.applyTopInset(findViewById(R.id.settingsToolbarContainer));
        EdgeToEdge.applyBottomInset(findViewById(R.id.settingsContent));

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        SwitchMaterial metricSwitch = findViewById(R.id.metricUnitSwitch);
        metricSwitch.setChecked(prefs.getBoolean(KEY_USE_METRIC, false));
        metricSwitch.setOnCheckedChangeListener((view, isChecked) -> {
            prefs.edit().putBoolean(KEY_USE_METRIC, isChecked).apply();
        });

        SwitchMaterial darkModeSwitch = findViewById(R.id.darkModeSwitch);
        darkModeSwitch.setChecked(prefs.getBoolean(KEY_DARK_MODE, isSystemInDarkMode()));
        darkModeSwitch.setOnCheckedChangeListener((view, isChecked) -> {
            prefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply();
            AppCompatDelegate.setDefaultNightMode(isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        });
    }

    private boolean isSystemInDarkMode() {
        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
    }
}
