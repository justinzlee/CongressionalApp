package com.example.myapplication.model;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import com.example.myapplication.R;

/**
 * The four things people come to a beach to do, and which this app can rank
 * beaches for.
 *
 * <p>Four because each one wants genuinely different conditions, to the point
 * of wanting opposite ones: the three-foot swell that makes a good body-surfing
 * afternoon is flat and dull to a surfer, and the still hot afternoon that suits
 * a picnic is the one with the worst ozone. A single "is this beach good today"
 * number would have to average those into something true of nobody. Asking what
 * you are going to do first is what lets the answer mean anything.
 *
 * <p>Swimming and body-surfing share an entry on purpose. They want the same
 * water - small rideable surf, clean, ideally patrolled - and splitting them
 * would offer a choice with no consequence.
 */
public enum Activity {

    SWIM(R.string.activity_swim, R.string.activity_swim_blurb, R.drawable.ic_wave),
    SURF(R.string.activity_surf, R.string.activity_surf_blurb, R.drawable.ic_surfboard),
    PICNIC(R.string.activity_picnic, R.string.activity_picnic_blurb, R.drawable.ic_picnic),
    FISH(R.string.activity_fish, R.string.activity_fish_blurb, R.drawable.ic_fish);

    @StringRes private final int labelRes;
    @StringRes private final int blurbRes;
    @DrawableRes private final int iconRes;

    Activity(@StringRes int labelRes, @StringRes int blurbRes, @DrawableRes int iconRes) {
        this.labelRes = labelRes;
        this.blurbRes = blurbRes;
        this.iconRes = iconRes;
    }

    @StringRes public int getLabelRes() { return labelRes; }

    /** One line on what this activity wants, for the picker. */
    @StringRes public int getBlurbRes() { return blurbRes; }

    @DrawableRes public int getIconRes() { return iconRes; }

    /** Stored in preferences by name; anything unrecognised falls back to swimming. */
    public static Activity parse(String name) {
        if (name != null) {
            for (Activity activity : values()) {
                if (activity.name().equals(name)) return activity;
            }
        }
        return SWIM;
    }
}
