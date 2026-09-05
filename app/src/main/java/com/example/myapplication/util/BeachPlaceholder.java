package com.example.myapplication.util;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import androidx.appcompat.content.res.AppCompatResources;
import com.example.myapplication.R;
import com.example.myapplication.model.Beach;

/**
 * Hands out the drawn shoreline that stands in for a beach with no photo,
 * in daylight or after dark depending on where the sun is at that beach.
 *
 * <p>The two palettes live in {@code values/colors.xml} and
 * {@code values-night/colors.xml}, but the phone's dark-mode setting is not
 * what picks between them. The picture is standing in for a photograph of a
 * real place, and a photograph is light or dark because of the time of day
 * there — so a beach at noon is drawn in daylight even on a phone that is in
 * dark mode all day, and a beach after sunset is drawn at dusk even on a light
 * one. To get that, the drawable is resolved through a Context built from a
 * copy of the configuration with the night bit set from the sunrise
 * calculation rather than from the theme.
 */
public final class BeachPlaceholder {

    private static Context daylightContext;
    private static Context afterDarkContext;

    private BeachPlaceholder() {}

    /** The shoreline as it would look at this beach right now. */
    public static Drawable forBeach(Context context, Beach beach) {
        boolean daylight = SunClock.isDaylightAt(
            beach.getLatitude(), beach.getLongitude(), System.currentTimeMillis());
        return AppCompatResources.getDrawable(
            resolveIn(context.getApplicationContext(), daylight),
            R.drawable.ic_beach_placeholder);
    }

    /**
     * A Context whose resources answer as though it were day or night. Both are
     * built from the application context and cached, so binding a long list of
     * rows does not create one per row.
     */
    private static synchronized Context resolveIn(Context app, boolean daylight) {
        Context cached = daylight ? daylightContext : afterDarkContext;
        if (cached != null) return cached;

        Configuration config = new Configuration(app.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
            | (daylight ? Configuration.UI_MODE_NIGHT_NO : Configuration.UI_MODE_NIGHT_YES);
        Context shifted = app.createConfigurationContext(config);

        if (daylight) daylightContext = shifted; else afterDarkContext = shifted;
        return shifted;
    }
}
