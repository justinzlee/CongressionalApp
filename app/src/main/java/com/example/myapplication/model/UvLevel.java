package com.example.myapplication.model;

import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.example.myapplication.R;

/**
 * The UV index in bands: where each one starts, what it is called, the colour
 * it is drawn in, and what the sun asks of you while it is that high.
 *
 * <p>One source of truth, for the same reason {@link SafetyLevel} publishes
 * its wave thresholds: the reading on the home and beach screens, the hazard
 * card and the lesson in the Learn tab all name the bands from here, so they
 * can never disagree about where "very high" starts — or call the same
 * reading two different things, which is how "Higher" and "High" came to
 * describe UV 6 in the same app.
 *
 * <p>The boundaries are the WHO/EPA scale, which is what every forecast and
 * beach sign this app sits alongside is quoting.
 */
public enum UvLevel {
    LOW(0, R.string.uv_level_low, R.string.uv_band_low_body, R.color.uv_low),
    MODERATE(3, R.string.uv_level_moderate, R.string.uv_band_moderate_body, R.color.uv_moderate),
    HIGH(6, R.string.uv_level_high, R.string.uv_band_high_body, R.color.uv_high),
    VERY_HIGH(8, R.string.uv_level_very_high, R.string.uv_band_very_high_body,
        R.color.uv_very_high),
    EXTREME(11, R.string.uv_level_extreme, R.string.uv_band_extreme_body, R.color.uv_extreme);

    private final int min;
    @StringRes private final int labelRes;
    @StringRes private final int adviceRes;
    @ColorRes private final int colorRes;

    UvLevel(int min, @StringRes int labelRes, @StringRes int adviceRes, @ColorRes int colorRes) {
        this.min = min;
        this.labelRes = labelRes;
        this.adviceRes = adviceRes;
        this.colorRes = colorRes;
    }

    /** The lowest reading in this band. */
    public int getMin() { return min; }

    /**
     * The highest reading in this band, or {@code null} for the top band,
     * which has no ceiling — UV 11 is where the published scale stops
     * counting, not where the sun does.
     *
     * <p>Derived from the next band's floor rather than written down twice,
     * so the ranges the lesson prints cannot drift from the ones
     * {@link #of(int)} sorts readings into.
     */
    @Nullable
    public Integer getMax() {
        UvLevel[] bands = values();
        return ordinal() == bands.length - 1 ? null : bands[ordinal() + 1].min - 1;
    }

    @StringRes public int getLabelRes() { return labelRes; }
    @StringRes public int getAdviceRes() { return adviceRes; }
    @ColorRes public int getColorRes() { return colorRes; }

    /** The band a reading falls in. Anything above the scale reads as extreme. */
    public static UvLevel of(int uvIndex) {
        UvLevel band = LOW;
        for (UvLevel candidate : values()) {
            if (uvIndex >= candidate.min) band = candidate;
        }
        return band;
    }
}
