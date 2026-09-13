package com.example.myapplication.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.example.myapplication.SettingsActivity;
import com.example.myapplication.model.Beach;
import com.example.myapplication.util.UnitConverter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Which beaches the Alerts tab speaks for.
 *
 * <p>The tab used to flatten every hazard at all sixty beaches into one list.
 * On a hot afternoon that is a screen of warnings about water on the other side
 * of the country, and a badge counting them, which teaches people to ignore
 * both. An alert is only worth raising about a beach the user might actually
 * walk into, so the default scope is the beaches within
 * {@link #NEARBY_MILES} miles of where the phone is, together with every beach
 * the user has saved however far away it is — a saved beach is one they have
 * said they care about, and a trip next week is exactly when a warning matters.
 *
 * <p>Someone who wants a quieter tab can turn on the saved-only setting, and
 * then distance stops counting entirely: they hear about their own beaches and
 * nothing else.
 *
 * <p>Everything that reads alerts goes through here — the Alerts screen, the
 * "mark as read" that clears the badge, and the badge itself. They have to
 * agree on the same set of beaches, or the badge counts warnings the screen
 * will not show and no amount of reading it ever clears the number.
 */
public final class AlertScope {

    /** How far from the phone a beach can be and still raise an alert. */
    public static final double NEARBY_MILES = 50;

    private AlertScope() {}

    /** True when the user has asked to hear only about their saved beaches. */
    public static boolean savedOnly(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
            SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(SettingsActivity.KEY_ALERTS_SAVED_ONLY, false);
    }

    /** Whether the app knows where the phone is, which the empty state explains. */
    public static boolean knowsWhereYouAre(Context context) {
        return new LastKnownLocation(context).isKnown();
    }

    /** The beaches in scope for alerts right now, out of the ones given. */
    public static List<Beach> inScope(Context context, List<Beach> beaches) {
        Set<String> saved = new SavedBeachStore(context).getSavedIds();
        LastKnownLocation where = new LastKnownLocation(context);
        return filter(beaches, saved, savedOnly(context),
            where.isKnown() ? where.getLatitude() : null,
            where.isKnown() ? where.getLongitude() : null);
    }

    /**
     * The rule itself, with everything it needs passed in so it can be tested
     * without a device.
     *
     * <p>A null latitude or longitude means the app has no idea where the phone
     * is. Nothing is then within range of anything, so the scope falls back to
     * the saved beaches alone rather than to all of them: a user who has not
     * granted location has not thereby asked to hear about every coast in the
     * country. The Alerts screen says as much rather than just looking empty.
     *
     * <p>{@link RankingScope} applies the same rule to "Best beach for…", with
     * its own saved-only setting.
     *
     * @param savedOnly when true, distance is ignored and only saved beaches
     *                  count, however near the user happens to be to others.
     */
    static List<Beach> filter(List<Beach> beaches, Set<String> savedIds, boolean savedOnly,
                              @Nullable Double latitude, @Nullable Double longitude) {
        if (beaches == null || beaches.isEmpty()) return Collections.emptyList();
        Set<String> saved = savedIds == null ? Collections.emptySet() : savedIds;
        boolean knowsWhere = !savedOnly && latitude != null && longitude != null;

        List<Beach> scoped = new ArrayList<>();
        for (Beach beach : beaches) {
            if (saved.contains(beach.getId())) {
                scoped.add(beach);
                continue;
            }
            if (knowsWhere && UnitConverter.calculateDistance(
                    latitude, longitude, beach.getLatitude(), beach.getLongitude())
                    <= NEARBY_MILES) {
                scoped.add(beach);
            }
        }
        return scoped;
    }
}
