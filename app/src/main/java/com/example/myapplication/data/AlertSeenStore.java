package com.example.myapplication.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.myapplication.SettingsActivity;
import com.example.myapplication.model.BeachAlert;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Remembers which alerts the user has already read, so the badge on the
 * Alerts tab counts what is new rather than what exists. Backed by the app's
 * existing preferences file, the way {@link SavedBeachStore} is.
 */
public class AlertSeenStore {
    private static final String KEY_SEEN = "seen_alert_ids";

    private final SharedPreferences prefs;

    public AlertSeenStore(Context context) {
        this.prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** How many of these alerts the user has not opened the Alerts screen on yet. */
    public int countUnseen(List<BeachAlert> alerts) {
        Set<String> seen = getSeenIds();
        int unseen = 0;
        for (BeachAlert alert : alerts) {
            if (!seen.contains(alert.getId())) {
                unseen++;
            }
        }
        return unseen;
    }

    /**
     * Records this exact set of alerts as read. Storing the current ids rather
     * than adding to them also drops alerts that have since cleared, so the
     * set never grows without bound — and a hazard that returns later counts
     * as new again, which is what a swimmer would want.
     */
    public void markSeen(List<BeachAlert> alerts) {
        Set<String> ids = new LinkedHashSet<>();
        for (BeachAlert alert : alerts) {
            ids.add(alert.getId());
        }
        prefs.edit().putStringSet(KEY_SEEN, ids).apply();
    }

    private Set<String> getSeenIds() {
        // A defensive copy: SharedPreferences' own Set must not be mutated.
        return new LinkedHashSet<>(prefs.getStringSet(KEY_SEEN, Collections.emptySet()));
    }
}
