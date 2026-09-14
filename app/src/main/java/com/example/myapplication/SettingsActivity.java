package com.example.myapplication;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import com.example.myapplication.util.EdgeToEdge;
import com.google.android.material.materialswitch.MaterialSwitch;

public class SettingsActivity extends AppCompatActivity {
    public static final String PREFS_NAME = "BeachSafetyPrefs";
    public static final String KEY_USE_METRIC = "use_metric";
    public static final String KEY_DARK_MODE = "dark_mode";
    public static final String KEY_ALERTS_SAVED_ONLY = "alerts_saved_only";
    public static final String KEY_RANKING_SAVED_ONLY = "ranking_saved_only";
    public static final String KEY_LANGUAGE = "language";

    /**
     * The languages Sandbar is written in, as the tags stored against {@link
     * #KEY_LANGUAGE}. They match res/xml/locales_config.xml, which is what
     * Android 13 and up reads for its own per-app language screen, so neither
     * list can grow without the other.
     */
    public static final String LANGUAGE_SYSTEM = "";
    public static final String LANGUAGE_ENGLISH = "en";
    public static final String LANGUAGE_SPANISH = "es";

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

    /**
     * Puts the app in whichever language the user last picked, for the Android
     * versions that would not remember it themselves.
     *
     * <p>From Android 13 the per-app language is the system's own setting and
     * the system persists it; below that AppCompat holds the locale in memory
     * only, so without this it would be gone by the next cold start. That is
     * why {@link BeachSafetyApp} calls this on every start — and why it does
     * nothing on 13 and up, where a call this early cannot work anyway:
     * AppCompat reaches the framework through an activity's delegate, and at
     * Application.onCreate there is no activity yet.
     */
    public static void applyStoredLanguage(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return;
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(storedLanguage(context)));
    }

    /**
     * The language actually in force, as one of the tags above.
     *
     * <p>On Android 13 and up this asks the system rather than our own
     * preference, because there the language can also be changed from the
     * phone's Settings — res/xml/locales_config.xml is what lists Sandbar on
     * that screen. A row here reading "System default" under a Spanish app
     * would be this screen arguing with the one next door. Below 13 there is no
     * such screen and the preference is the only record there is.
     *
     * <p>Must be called from an activity: that is what AppCompat reads the
     * system setting through.
     */
    public static String currentLanguage(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            LocaleListCompat inForce = AppCompatDelegate.getApplicationLocales();
            return inForce.isEmpty() ? LANGUAGE_SYSTEM : inForce.get(0).getLanguage();
        }
        return storedLanguage(context);
    }

    /** The last tag written here, which below Android 13 is the only store. */
    private static String storedLanguage(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, LANGUAGE_SYSTEM);
    }

    /** The name of a language, written in that language. */
    private static int languageLabelRes(String tag) {
        if (LANGUAGE_ENGLISH.equals(tag)) return R.string.language_english;
        if (LANGUAGE_SPANISH.equals(tag)) return R.string.language_spanish;
        return R.string.language_system;
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

        bindLanguageRow(prefs);

        View contactRow = findViewById(R.id.contactDeveloperRow);
        if (contactRow != null) {
            contactRow.setOnClickListener(v -> {
                Intent intent = new Intent(this, ContactDeveloperActivity.class);
                startActivity(intent);
            });
        }
    }

    /**
     * The Language row and the sheet behind it.
     *
     * <p>Choosing a language hands the new locale to AppCompat, which recreates
     * every activity that is up — this one included — so there is nothing to
     * redraw afterwards: the rebuilt row reads the language now in force and
     * names it in that language.
     */
    private void bindLanguageRow(SharedPreferences prefs) {
        View row = findViewById(R.id.languageRow);
        TextView value = findViewById(R.id.languageValue);
        value.setText(languageLabelRes(currentLanguage(this)));

        row.setOnClickListener(v -> LanguageSheet.show(this, currentLanguage(this), tag -> {
            // Both, always: the call is what changes the app now, and on
            // Android 12 and below the preference is what remembers it. Unlike
            // applyStoredLanguage this runs at every API level, because here
            // there is an activity for AppCompat to reach the system through.
            prefs.edit().putString(KEY_LANGUAGE, tag).apply();
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag));
        }));
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
