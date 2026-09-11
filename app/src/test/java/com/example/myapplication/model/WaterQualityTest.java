package com.example.myapplication.model;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The runoff and sewage advisories.
 *
 * <p>These decide whether the app tells somebody the water is probably fine, so
 * the direction of every failure here matters more than the precision. A missed
 * advisory sends a swimmer into contaminated water; a spurious one costs a
 * swim. The rules are tuned to fail the second way, and these pin that.
 */
public class WaterQualityTest {

    /** Far from the border: La Jolla. */
    private Beach cleanCoast() {
        return new Beach("CA1", "La Jolla Shores", 32.8589, -117.2558,
            Collections.emptyList(), null, null, null);
    }

    /** Two miles north of the river mouth: Imperial Beach. */
    private Beach southCounty() {
        return new Beach("CA10", "Imperial Beach", 32.5793, -117.1350,
            Collections.emptyList(), null, null, null);
    }

    /** Sixteen kilometres north: Coronado, affected on rain days only. */
    private Beach coronado() {
        return new Beach("CA2", "Coronado Beach", 32.6859, -117.1831,
            Collections.emptyList(), null, null, null);
    }

    private static final double[] DRY = {0, 0, 0, 0, 0};
    private static final double[] RAINED_TODAY = {0, 0, 0, 0, 12.0};
    private static final double[] RAINED_LAST_WEEK = {30.0, 0, 0, 0, 0};

    @Test
    public void aDryBeachFarFromTheBorderIsLikelyClean() {
        WaterQuality water = WaterQuality.evaluate(cleanCoast(), DRY);
        assertEquals(WaterQuality.Status.LIKELY_CLEAN, water.getStatus());
        assertFalse(water.isAdvisory());
        assertEquals(100, water.cleanlinessScore());
    }

    /** Rain anywhere raises the runoff advisory. */
    @Test
    public void recentRainRaisesARunoffAdvisory() {
        WaterQuality water = WaterQuality.evaluate(cleanCoast(), RAINED_TODAY);
        assertEquals(WaterQuality.Status.RUNOFF_ADVISORY, water.getStatus());
        assertTrue(water.isAdvisory());
        assertEquals(Integer.valueOf(0), water.getDaysSinceRain());
    }

    /**
     * And it lifts again. Rain outside the window is over — an advisory that
     * never expires is one people learn to ignore.
     */
    @Test
    public void oldRainDoesNotHoldTheAdvisoryOpen() {
        assertEquals(WaterQuality.Status.LIKELY_CLEAN,
            WaterQuality.evaluate(cleanCoast(), RAINED_LAST_WEEK).getStatus());
    }

    /** A shower too light to run the drains is not an advisory. */
    @Test
    public void aTraceOfRainIsNotEnough() {
        double[] drizzle = {0, 0, 0, 0, WaterQuality.RAIN_TRIGGER_MM - 0.5};
        assertEquals(WaterQuality.Status.LIKELY_CLEAN,
            WaterQuality.evaluate(cleanCoast(), drizzle).getStatus());
    }

    /** Exactly at the trigger counts — the threshold is inclusive. */
    @Test
    public void rainAtTheTriggerCounts() {
        double[] atTrigger = {0, 0, 0, 0, WaterQuality.RAIN_TRIGGER_MM};
        assertEquals(WaterQuality.Status.RUNOFF_ADVISORY,
            WaterQuality.evaluate(cleanCoast(), atTrigger).getStatus());
    }

    // ── The border ───────────────────────────────────────────────────────

    /**
     * Imperial Beach is under advisory on a dry day. Telling somebody standing
     * there that the water is likely clean would be the single most wrong thing
     * this app could say about water.
     */
    @Test
    public void theSouthCountyIsUnderAdvisoryEvenWhenDry() {
        WaterQuality water = WaterQuality.evaluate(southCounty(), DRY);
        assertEquals(WaterQuality.Status.SEWAGE_RISK, water.getStatus());
        assertTrue(water.isAdvisory());
    }

    /** Rain on top of it is the combination that closes those beaches. */
    @Test
    public void rainOnTheBorderBeachesIsTheWorstCase() {
        WaterQuality water = WaterQuality.evaluate(southCounty(), RAINED_TODAY);
        assertEquals(WaterQuality.Status.SEWAGE_AND_RAIN, water.getStatus());
        assertEquals(0, water.cleanlinessScore());
    }

    /**
     * Coronado is far enough north to be fine most of the time and not fine
     * after a storm, which is the whole reason the plume has two bands.
     */
    @Test
    public void coronadoIsOnlyAffectedOnRainDays() {
        assertEquals(WaterQuality.Status.LIKELY_CLEAN,
            WaterQuality.evaluate(coronado(), DRY).getStatus());
        assertEquals(WaterQuality.Status.SEWAGE_AND_RAIN,
            WaterQuality.evaluate(coronado(), RAINED_TODAY).getStatus());
    }

    /** A beach on another coast entirely is never in the plume. */
    @Test
    public void theOtherSideOfTheCountryIsNeverInThePlume() {
        Beach florida = new Beach("FL1", "South Beach", 25.7826, -80.1341,
            Collections.emptyList(), null, null, null);
        assertEquals(TijuanaPlume.Reach.NONE, TijuanaPlume.reachFor(florida));
        assertEquals(WaterQuality.Status.LIKELY_CLEAN,
            WaterQuality.evaluate(florida, DRY).getStatus());
    }

    /** The current runs north, so beaches south of the mouth are not ours to warn about. */
    @Test
    public void southOfTheMouthIsOutOfScope() {
        Beach mexico = new Beach("MX1", "Playas de Tijuana", 32.5300, -117.1200,
            Collections.emptyList(), null, null, null);
        assertEquals(TijuanaPlume.Reach.NONE, TijuanaPlume.reachFor(mexico));
    }

    /** Advisories are ordered: each step down is genuinely worse to swim in. */
    @Test
    public void theCleanlinessScaleIsOrdered() {
        int clean = WaterQuality.evaluate(cleanCoast(), DRY).cleanlinessScore();
        int runoff = WaterQuality.evaluate(cleanCoast(), RAINED_TODAY).cleanlinessScore();
        int sewage = WaterQuality.evaluate(southCounty(), DRY).cleanlinessScore();
        int both = WaterQuality.evaluate(southCounty(), RAINED_TODAY).cleanlinessScore();
        assertTrue(clean > runoff);
        assertTrue(runoff > sewage);
        assertTrue(sewage > both);
    }

    /** No rainfall data at all reads as no advisory, not as a crash. */
    @Test
    public void anEmptyRainSeriesIsHandled() {
        assertEquals(WaterQuality.Status.LIKELY_CLEAN,
            WaterQuality.evaluate(cleanCoast(), new double[0]).getStatus());
        assertEquals(WaterQuality.Status.SEWAGE_RISK,
            WaterQuality.evaluate(southCounty(), new double[0]).getStatus());
    }
}
