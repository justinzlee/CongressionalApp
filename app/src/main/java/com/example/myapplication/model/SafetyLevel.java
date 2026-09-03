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

    /*
     * These thresholds are deliberately not trigger-happy. A verdict that
     * cries "take care" at a perfectly good beach teaches people to ignore
     * it, which costs more than it saves on the day the water is genuinely
     * bad. Reported hazards still escalate a beach regardless of these
     * numbers, and the note under the verdict always describes the swell in
     * plain words, so a keen swimmer can judge for themselves.
     */

    /**
     * Surf at or above this (feet) reads as dangerous on its own.
     *
     * <p>Public because the wave-height card bands on the same numbers. They
     * were separate once, and drifted: the card called 3ft "Moderate — strong
     * swimmers only" while the verdict directly above it called the same
     * reading "Good to swim". One source of truth, so the two can never
     * disagree about the same water again.
     */
    public static final double DANGER_WAVE_FEET = 6.0;
    /**
     * Surf at or above this (feet) is worth a warning. Around 4ft is where
     * waves start to genuinely push an average swimmer around; below that is
     * small-to-moderate and fine for most people, so it stays "good to swim".
     */
    public static final double CAUTION_WAVE_FEET = 4.0;
    /**
     * Only <em>extreme</em> UV moves the swim verdict. UV 8-10 ("very high")
     * describes most sunny summer days at most beaches, so treating it as a
     * warning flagged nearly everywhere at once — and UV is a sun-exposure
     * hazard, not a reason the water is unsafe. The sunscreen advice still
     * appears in the note under the verdict at "very high" and above.
     *
     * <p>Public for the same reason the surf thresholds are: ConditionHazards
     * writes the UV hazard the beach screen lists, and it has to appear at
     * exactly the reading that moves the verdict, not one of its own.
     */
    public static final int CAUTION_UV = 11;

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
