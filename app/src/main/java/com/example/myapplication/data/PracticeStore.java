package com.example.myapplication.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.TimeZone;

/**
 * Remembers which day's practice has been finished, and how many days in a
 * row. The streak is the only reason a daily habit sticks, and it costs two
 * numbers to keep.
 */
public class PracticeStore {

    private static final String PREFS_NAME = "practice";
    private static final String KEY_LAST_DAY = "last_completed_day";
    private static final String KEY_STREAK = "streak";

    private static final long MILLIS_PER_DAY = 24L * 60L * 60L * 1000L;

    private final SharedPreferences prefs;

    public PracticeStore(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Today's number, counted in the phone's own time zone. Epoch millis
     * divided straight through would roll the day over at UTC midnight, which
     * is the middle of the afternoon on the west coast.
     */
    public static long today() {
        long now = System.currentTimeMillis();
        return (now + TimeZone.getDefault().getOffset(now)) / MILLIS_PER_DAY;
    }

    public boolean isDoneToday() {
        return prefs.getLong(KEY_LAST_DAY, Long.MIN_VALUE) == today();
    }

    /**
     * Records today's practice as finished and returns the streak that leaves.
     * Finishing twice in one day changes nothing, and a missed day starts the
     * count again at one.
     */
    public int markDoneToday() {
        long today = today();
        long last = prefs.getLong(KEY_LAST_DAY, Long.MIN_VALUE);
        if (last == today) {
            return getStreak();
        }

        int streak = (last == today - 1) ? getStreak() + 1 : 1;
        prefs.edit()
            .putLong(KEY_LAST_DAY, today)
            .putInt(KEY_STREAK, streak)
            .apply();
        return streak;
    }

    /** Days in a row up to and including the last one finished, or 0 if the run is broken. */
    public int getStreak() {
        long last = prefs.getLong(KEY_LAST_DAY, Long.MIN_VALUE);
        long today = today();
        if (last != today && last != today - 1) {
            // Yesterday came and went without a session, so the run is over.
            return 0;
        }
        return prefs.getInt(KEY_STREAK, 0);
    }
}
