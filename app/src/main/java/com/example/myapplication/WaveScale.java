package com.example.myapplication;

import androidx.core.graphics.ColorUtils;

import com.example.myapplication.model.SafetyLevel;

/**
 * The height ramp behind the wave card. A swell height turns into two things:
 * a continuous colour blend for the water itself, so the sea shifts from green
 * through amber to red as the surf builds rather than snapping between three
 * looks, and a coarse band that supplies the tag, the advice line and the
 * marker colour.
 *
 * The ramp stops come from the "Wave Height Card" design. They are
 * interpolation data rather than theme tokens, so they live here instead of
 * colors.xml, and they stay the same in light and dark: the water panel is
 * imagery, like the hero photo, and only the card's chrome follows the theme.
 * The bands, by contrast, reuse the app's own safe/caution/danger tokens so the
 * card speaks the same colour language as the verdict chip above it.
 */
public final class WaveScale {

    /**
     * Top of the scale, in feet. Every position on the card is height / MAX_FT,
     * so this is the only place the card's range is stated — the ruler beside
     * it takes its tick count from here too.
     *
     * <p>Raising this changes how tall the card can draw, and nothing else.
     * The bands come from {@link SafetyLevel} and the colour ramp is in real
     * feet, so a six-foot swell is the same "hazardous", in the same red, on
     * any scale.
     */
    public static final float MAX_FT = 10f;

    /**
     * The three bands the card labels a height with. The lowest band runs to
     * {@link SafetyLevel#CAUTION_WAVE_FEET}, which is small-to-moderate surf
     * rather than flat water — hence "small", not "calm".
     */
    public enum Level {
        SMALL(R.string.wave_level_small, R.string.wave_advice_small,
            R.color.safe, R.color.safe_tint),
        MODERATE(R.string.wave_level_moderate, R.string.wave_advice_moderate,
            R.color.caution, R.color.caution_tint),
        HAZARDOUS(R.string.wave_level_hazardous, R.string.wave_advice_hazardous,
            R.color.danger, R.color.danger_tint);

        private final int labelRes, adviceRes, colorRes, tintRes;

        Level(int labelRes, int adviceRes, int colorRes, int tintRes) {
            this.labelRes = labelRes;
            this.adviceRes = adviceRes;
            this.colorRes = colorRes;
            this.tintRes = tintRes;
        }

        public int getLabelRes() { return labelRes; }
        public int getAdviceRes() { return adviceRes; }
        /** Tag text and scale marker. */
        public int getColorRes() { return colorRes; }
        /** Tag background. */
        public int getTintRes() { return tintRes; }
    }

    /**
     * The band a height falls in, on the same thresholds the swim verdict
     * uses. The card sits directly under that verdict, so borrowing
     * {@link SafetyLevel}'s numbers is what keeps the two from describing one
     * reading in contradictory terms.
     */
    public static Level levelFor(float feet) {
        if (feet < SafetyLevel.CAUTION_WAVE_FEET) return Level.SMALL;
        if (feet < SafetyLevel.DANGER_WAVE_FEET) return Level.MODERATE;
        return Level.HAZARDOUS;
    }

    // Ramp stops, in feet, with the two colours the water is built from: the
    // bright crest highlight and the body of the swell just under it. The
    // middle stops sit on the band edges so the water turns amber exactly
    // where the card starts saying "moderate", and red where it says
    // "hazardous", rather than shifting colour a foot early.
    //
    // They are heights rather than positions on the card, so widening the
    // scale leaves them where they are and the water at six feet looks the
    // same on an eight-foot ruler or a ten. The last stop is the exception: it
    // sat near the top of an 8ft card, and a 10ft one would have left its
    // whole top quarter flat, so the ramp carries on to the top of the scale
    // in the direction it was already going.
    private static final float[] STOP_FT = {0f, 2.5f, 4f, 6f, 7.5f, 10f};
    private static final int[] STOP_CREST = {
        0xFF9FE7C4, 0xFFC4E6A8, 0xFFF2CA7A, 0xFFF8BB92, 0xFFF7A493, 0xFFEC8574};
    private static final int[] STOP_BODY = {
        0xFF1F8F74, 0xFF4F9F60, 0xFFB6802C, 0xFFC2643A, 0xFFB8453F, 0xFF93302D};

    public static int crestColor(float feet) { return sample(STOP_CREST, feet); }

    public static int bodyColor(float feet) { return sample(STOP_BODY, feet); }

    private static int sample(int[] stops, float feet) {
        if (feet <= STOP_FT[0]) return stops[0];
        for (int i = 1; i < STOP_FT.length; i++) {
            if (STOP_FT[i] >= feet) {
                float t = (feet - STOP_FT[i - 1]) / (STOP_FT[i] - STOP_FT[i - 1]);
                return ColorUtils.blendARGB(stops[i - 1], stops[i], t);
            }
        }
        return stops[stops.length - 1];
    }

    private WaveScale() {
    }
}
