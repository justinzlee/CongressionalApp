package com.example.myapplication.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.myapplication.SettingsActivity;
import com.example.myapplication.model.SeagullRating;

/**
 * Remembers how many seagulls each beach had the last time somebody rated it.
 *
 * <p>One rating per beach, overwritten by the next, for the same reason as
 * {@link RockRatingStore}: the question is what the beach is like now. The time
 * it was given is kept alongside so the beach screen can say how old it is —
 * gulls come and go far faster than rocks do, and a rating from last month
 * should read as last month's.
 */
public class SeagullRatingStore {

    private static final String KEY_RATING_PREFIX = "gull_rating_";
    private static final String KEY_RATED_AT_PREFIX = "gull_rated_at_";

    private final SharedPreferences prefs;

    public SeagullRatingStore(Context context) {
        this.prefs = context.getApplicationContext()
            .getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * This beach's rating, or {@link SeagullRating#UNRATED}. Anything stored
     * off the scale reads back as unrated rather than as a step nobody chose.
     */
    public int ratingFor(String beachId) {
        int stored = prefs.getInt(KEY_RATING_PREFIX + beachId, SeagullRating.UNRATED);
        return SeagullRating.isRated(stored) ? stored : SeagullRating.UNRATED;
    }

    public long ratedAt(String beachId) {
        return prefs.getLong(KEY_RATED_AT_PREFIX + beachId, 0L);
    }

    /** Records the rating, stamped with now. Off-scale values are ignored. */
    public void save(String beachId, int rating) {
        if (!SeagullRating.isRated(rating)) return;
        prefs.edit()
            .putInt(KEY_RATING_PREFIX + beachId, rating)
            .putLong(KEY_RATED_AT_PREFIX + beachId, System.currentTimeMillis())
            .apply();
    }

    public void clear(String beachId) {
        prefs.edit()
            .remove(KEY_RATING_PREFIX + beachId)
            .remove(KEY_RATED_AT_PREFIX + beachId)
            .apply();
    }
}
