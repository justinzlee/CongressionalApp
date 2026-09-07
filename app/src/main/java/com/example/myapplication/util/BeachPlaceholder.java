package com.example.myapplication.util;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import androidx.annotation.VisibleForTesting;
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
 *
 * <p>There are five scenes rather than one. They share a shoreline and differ
 * in what stands on the sand — an umbrella, a palm, a surfboard and towel, a
 * lifeguard tower, a sandcastle. Most beaches in this app have no photograph,
 * so a single drawing would tile the whole list with the same picture and read
 * as a rendering fault rather than as an illustration.
 */
public final class BeachPlaceholder {

    /**
     * The five scenes, in no particular order — {@link #sceneFor} indexes into
     * this, so reordering it silently reshuffles which beach shows what.
     */
    private static final int[] SCENES = {
        R.drawable.ic_beach_placeholder,
        R.drawable.ic_beach_placeholder_palm,
        R.drawable.ic_beach_placeholder_surf,
        R.drawable.ic_beach_placeholder_tower,
        R.drawable.ic_beach_placeholder_castle,
    };

    private static Context daylightContext;
    private static Context afterDarkContext;

    private BeachPlaceholder() {}

    /** The shoreline as it would look at this beach right now. */
    public static Drawable forBeach(Context context, Beach beach) {
        boolean daylight = SunClock.isDaylightAt(
            beach.getLatitude(), beach.getLongitude(), System.currentTimeMillis());
        return AppCompatResources.getDrawable(
            resolveIn(context.getApplicationContext(), daylight),
            sceneFor(beach));
    }

    /**
     * Which of the five scenes this beach is drawn as.
     *
     * <p>Derived from the beach's id, so a beach keeps the same scene forever:
     * the list, the detail screen and the saved tab all agree, scrolling past a
     * row and back does not redraw it as somewhere else, and the choice
     * survives restarts without being stored. {@code String.hashCode} is
     * specified by the language rather than left to the runtime, so the
     * assignment is also stable across devices and releases.
     *
     * <p>{@code Math.floorMod} rather than {@code %}: ids hashing negative
     * would otherwise index out of the array.
     */
    @VisibleForTesting
    static int sceneFor(Beach beach) {
        String id = beach.getId();
        if (id == null) return SCENES[0];
        return SCENES[Math.floorMod(id.hashCode(), SCENES.length)];
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
