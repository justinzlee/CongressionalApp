package com.example.myapplication.model;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.example.myapplication.R;

import java.util.List;

/**
 * The three-step swim verdict the home screen is built around: a single
 * plain-language answer to "can I go in?".
 *
 * <p>The verdict is <em>derived</em> from the conditions the app already
 * fetches (wave height, UV) plus any hazards on record for the beach — it is
 * not an official beach rating, and {@link #evaluate} deliberately returns
 * {@code null} rather than guessing when there is nothing to judge from.
 */
public enum SafetyLevel {
    SAFE(R.color.safe, R.color.safe_tint, R.drawable.ic_check_circle,
        R.string.verdict_safe, R.string.verdict_safe_short),
    CAUTION(R.color.caution, R.color.caution_tint, R.drawable.ic_warning_circle,
        R.string.verdict_caution, R.string.verdict_caution_short),
    DANGER(R.color.danger, R.color.danger_tint, R.drawable.ic_x_circle,
        R.string.verdict_danger, R.string.verdict_danger_short);

    /** Surf at or above this (feet) reads as dangerous on its own. */
    private static final double DANGER_WAVE_FEET = 6.0;
    /** Surf at or above this (feet) is worth a warning. */
    private static final double CAUTION_WAVE_FEET = 3.0;
    /** UV at or above this is "very high" on the app's own scale. */
    private static final int CAUTION_UV = 8;

    @ColorRes private final int colorRes;
    @ColorRes private final int tintRes;
    @DrawableRes private final int iconRes;
    @StringRes private final int verdictRes;
    @StringRes private final int shortVerdictRes;

    SafetyLevel(@ColorRes int colorRes, @ColorRes int tintRes, @DrawableRes int iconRes,
                @StringRes int verdictRes, @StringRes int shortVerdictRes) {
        this.colorRes = colorRes;
        this.tintRes = tintRes;
        this.iconRes = iconRes;
        this.verdictRes = verdictRes;
        this.shortVerdictRes = shortVerdictRes;
    }

    @ColorRes public int getColorRes() { return colorRes; }
    @ColorRes public int getTintRes() { return tintRes; }
    @DrawableRes public int getIconRes() { return iconRes; }
    @StringRes public int getVerdictRes() { return verdictRes; }
    @StringRes public int getShortVerdictRes() { return shortVerdictRes; }

    /**
     * Works out the verdict for a beach, or {@code null} when neither
     * conditions nor hazards are known yet — callers should show a
     * "checking conditions" state rather than implying the water is fine.
     */
    @Nullable
    public static SafetyLevel evaluate(Beach beach) {
        if (beach == null) return null;

        List<Hazard> hazards = beach.getHazards();
        BeachConditions conditions = beach.getConditions();
        if (conditions == null && hazards.isEmpty()) return null;

        SafetyLevel level = SAFE;

        for (Hazard hazard : hazards) {
            Severity severity = hazard.getSeverity();
            if (severity == Severity.HIGH || severity == Severity.EXTREME) {
                return DANGER;
            }
            if (severity == Severity.MODERATE) {
                level = CAUTION;
            }
        }

        if (conditions != null) {
            Double waveHeight = conditions.getWaveHeight();
            if (waveHeight != null) {
                if (waveHeight >= DANGER_WAVE_FEET) return DANGER;
                if (waveHeight >= CAUTION_WAVE_FEET) level = CAUTION;
            }
            Integer uvIndex = conditions.getUvIndex();
            if (uvIndex != null && uvIndex >= CAUTION_UV) {
                level = CAUTION;
            }
        }

        return level;
    }
}
