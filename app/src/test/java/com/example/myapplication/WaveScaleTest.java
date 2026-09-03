package com.example.myapplication;

import com.example.myapplication.model.Beach;
import com.example.myapplication.model.BeachConditions;
import com.example.myapplication.model.SafetyLevel;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * The wave card sits directly beneath the swim verdict on the beach detail
 * screen, describing the same number. They used to band it differently — the
 * card called anything over 2ft "Moderate — strong swimmers only" while the
 * verdict called anything under 4ft "Good to swim" — so a 3ft reading showed
 * both at once, on a screen whose whole job is answering "can I go in?".
 *
 * <p>These tests pin them together across the range.
 */
public class WaveScaleTest {

    private static Beach beachWithSurf(double feet) {
        return new Beach("t1", "Test Beach", 32.8589, -117.2558, null,
            new BeachConditions(72.0, 3, 5.0, feet, 10.0), null, null);
    }

    /** The band the card shows, and the verdict shown above it, must rank alike. */
    private static void assertAgreeAt(double feet, WaveScale.Level band, SafetyLevel verdict) {
        assertSame("card band at " + feet + "ft", band, WaveScale.levelFor((float) feet));
        assertEquals("swim verdict at " + feet + "ft", verdict,
            SafetyLevel.evaluate(beachWithSurf(feet)));
    }

    @Test
    public void threeFootSwell_readsSmallAndGoodToSwim() {
        // 0.9m, the reading that first showed the contradiction.
        assertAgreeAt(2.95, WaveScale.Level.SMALL, SafetyLevel.SAFE);
    }

    @Test
    public void justUnderCautionThreshold_isStillSmallAndSafe() {
        assertAgreeAt(3.9, WaveScale.Level.SMALL, SafetyLevel.SAFE);
    }

    @Test
    public void atCautionThreshold_bothStepUpTogether() {
        assertAgreeAt(SafetyLevel.CAUTION_WAVE_FEET, WaveScale.Level.MODERATE, SafetyLevel.CAUTION);
    }

    @Test
    public void justUnderDangerThreshold_isStillModerate() {
        assertAgreeAt(5.9, WaveScale.Level.MODERATE, SafetyLevel.CAUTION);
    }

    @Test
    public void atDangerThreshold_bothStepUpTogether() {
        assertAgreeAt(SafetyLevel.DANGER_WAVE_FEET, WaveScale.Level.HAZARDOUS, SafetyLevel.DANGER);
    }

    @Test
    public void wellOverhead_isHazardousAndDangerous() {
        assertAgreeAt(9.0, WaveScale.Level.HAZARDOUS, SafetyLevel.DANGER);
    }

    /**
     * The guard that matters: sweep the whole scale and require the card's band
     * and the swim verdict to move in lockstep, so neither can be retuned on
     * its own again.
     */
    @Test
    public void cardAndVerdictNeverDisagreeAcrossTheScale() {
        for (int tenths = 0; tenths <= 120; tenths++) {
            double feet = tenths / 10.0;
            WaveScale.Level band = WaveScale.levelFor((float) feet);
            SafetyLevel verdict = SafetyLevel.evaluate(beachWithSurf(feet));

            SafetyLevel expected;
            switch (band) {
                case SMALL:     expected = SafetyLevel.SAFE;    break;
                case MODERATE:  expected = SafetyLevel.CAUTION; break;
                default:        expected = SafetyLevel.DANGER;  break;
            }
            assertEquals("at " + feet + "ft the card says " + band, expected, verdict);
        }
    }
}
// The colour ramp's stops were moved onto the same band edges, but blending
// runs through android.graphics.Color, which a plain JVM test cannot call —
// that one is left to the on-device check rather than pulling in Robolectric.
