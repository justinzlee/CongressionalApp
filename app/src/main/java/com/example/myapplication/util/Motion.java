package com.example.myapplication.util;

import android.content.Context;
import android.provider.Settings;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

/**
 * Shared easing curves, and the one question every animation in the launch
 * sequence has to ask first.
 *
 * <p>The design specifies its motion in CSS cubic-beziers; the constants below
 * are those curves verbatim, so the timings in the handoff can be read against
 * the code without converting anything.
 */
public final class Motion {
    private Motion() {}

    /** Settles quickly and coasts: the sun rising, the surf swelling up. */
    public static final Interpolator SETTLE = new PathInterpolator(.16f, .84f, .3f, 1f);
    /** A shorter, flatter version of the same: the pole drawing, the tagline. */
    public static final Interpolator DRAW = new PathInterpolator(.2f, .7f, .3f, 1f);
    /** Overshoots past 1 and comes back — the snap of a flag catching wind. */
    public static final Interpolator UNFURL = new PathInterpolator(.3f, 1.5f, .5f, 1f);
    /** The wordmark wiping up into place. */
    public static final Interpolator WIPE = new PathInterpolator(.18f, .8f, .28f, 1f);

    /**
     * Whether this phone has been asked to stop animating.
     *
     * <p>Android's equivalent of the web's {@code prefers-reduced-motion} is
     * the animator duration scale, which "Remove animations" in accessibility
     * settings (and developer options) sets to zero. The design's reduced-motion
     * rule is all-or-nothing — render the final frame and fade the screen in —
     * so a single boolean is all the launch sequence needs.
     */
    public static boolean isReduced(Context context) {
        float scale = Settings.Global.getFloat(
            context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        return scale == 0f;
    }

    /** Where {@code now} sits inside a window that opens at {@code delay}, 0..1. */
    public static float phase(long now, long delay, long duration) {
        if (now <= delay) return 0f;
        if (now >= delay + duration) return 1f;
        return (now - delay) / (float) duration;
    }
}
