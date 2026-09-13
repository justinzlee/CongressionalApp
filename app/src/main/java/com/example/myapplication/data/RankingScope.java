package com.example.myapplication.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.example.myapplication.SettingsActivity;
import com.example.myapplication.model.Beach;

import java.util.List;

/**
 * Which beaches "Best beach for…" ranks.
 *
 * <p>The same rule as the Alerts tab — every saved beach, plus every beach
 * within {@link AlertScope#NEARBY_MILES} miles of the phone — with two
 * differences. The ranking screen asks the phone where it is as it opens and
 * passes that in, falling back to the last position the home screen wrote down
 * only when there is no fresh one: a top three for somewhere you drove away
 * from this morning answers the wrong question. And it has a saved-only
 * setting of its own, because asking for a quieter Alerts tab says nothing
 * about where you would go for a swim.
 */
public final class RankingScope {

    private RankingScope() {}

    /** True when the user has asked for only their saved beaches to be ranked. */
    public static boolean savedOnly(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
            SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(SettingsActivity.KEY_RANKING_SAVED_ONLY, false);
    }

    /**
     * The beaches to rank, out of the ones given. A null position means the
     * phone could not say where it is, which leaves the saved beaches alone.
     */
    public static List<Beach> inScope(Context context, List<Beach> beaches,
                                      @Nullable Double latitude, @Nullable Double longitude) {
        return AlertScope.filter(beaches, new SavedBeachStore(context).getSavedIds(),
            savedOnly(context), latitude, longitude);
    }
}
