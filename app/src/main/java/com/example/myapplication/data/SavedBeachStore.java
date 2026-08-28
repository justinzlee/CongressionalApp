package com.example.myapplication.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.myapplication.SettingsActivity;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Remembers which beaches the user has saved. Backed by the app's existing
 * preferences file so it survives restarts without pulling in a database.
 */
public class SavedBeachStore {
    private static final String KEY_SAVED = "saved_beach_ids";

    private final SharedPreferences prefs;

    public SavedBeachStore(Context context) {
        this.prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Saved beach ids, in the order they were added. */
    public Set<String> getSavedIds() {
        // A defensive copy: SharedPreferences' own Set must not be mutated.
        return new LinkedHashSet<>(prefs.getStringSet(KEY_SAVED, Collections.emptySet()));
    }

    public boolean isSaved(String beachId) {
        return getSavedIds().contains(beachId);
    }

    /** Adds or removes the beach, returning its new saved state. */
    public boolean toggle(String beachId) {
        Set<String> ids = getSavedIds();
        boolean nowSaved = ids.add(beachId);
        if (!nowSaved) {
            ids.remove(beachId);
        }
        prefs.edit().putStringSet(KEY_SAVED, ids).apply();
        return nowSaved;
    }
}
