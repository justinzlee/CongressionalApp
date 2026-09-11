package com.example.myapplication.model;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Which air and water readings are worth an alert, and how loud.
 *
 * <p>The decisions pinned here are about restraint as much as coverage. An app
 * that raises an alert for ordinary air teaches people to stop reading alerts,
 * and a permanent red card over the south county — which sits under a standing
 * advisory most of the year — would bleach out every genuine warning beside it.
 *
 * <p>These exercise the severity decisions, which are deliberately kept apart
 * from building the hazard itself. Writing a description needs resources and so
 * needs a Context, which a plain JVM test has none of; deciding whether there is
 * anything to describe does not, and that decision is the part worth pinning.
 */
public class EnvironmentHazardsTest {

    private Beach beach(double lat, double lon) {
        return new Beach("X", "Test", lat, lon, Collections.emptyList(), null, null, null);
    }

    private static final double[] DRY = {0, 0, 0, 0, 0};
    private static final double[] WET = {0, 0, 0, 0, 12.0};

    // ── Water ────────────────────────────────────────────────────────────

    /** Clean water raises nothing. Good news is not an alert. */
    @Test
    public void cleanWaterRaisesNoHazard() {
        WaterQuality clean = WaterQuality.evaluate(beach(32.86, -117.25), DRY);
        assertNull(EnvironmentHazards.waterSeverity(clean));
    }

    /** And neither does having no reading yet. */
    @Test
    public void noWaterReadingRaisesNoHazard() {
        assertNull(EnvironmentHazards.waterSeverity(null));
    }

    /**
     * Rain alone is a real reason not to swim, but it is a condition rather
     * than an emergency — the same call the app makes about extreme UV.
     */
    @Test
    public void runoffIsModerate() {
        WaterQuality runoff = WaterQuality.evaluate(beach(32.86, -117.25), WET);
        assertEquals(Severity.MODERATE, EnvironmentHazards.waterSeverity(runoff));
    }

    /**
     * A standing cross-border advisory is also moderate. Imperial Beach is
     * under one for most of the year; a permanent HIGH there would train
     * everyone in the county to ignore the colour.
     */
    @Test
    public void aStandingSewageAdvisoryIsModerate() {
        WaterQuality sewage = WaterQuality.evaluate(beach(32.5793, -117.1350), DRY);
        assertEquals(WaterQuality.Status.SEWAGE_RISK, sewage.getStatus());
        assertEquals(Severity.MODERATE, EnvironmentHazards.waterSeverity(sewage));
    }

    /** Both at once is the combination that closes those beaches. */
    @Test
    public void sewageAndRainTogetherIsHigh() {
        WaterQuality both = WaterQuality.evaluate(beach(32.5793, -117.1350), WET);
        assertEquals(WaterQuality.Status.SEWAGE_AND_RAIN, both.getStatus());
        assertEquals(Severity.HIGH, EnvironmentHazards.waterSeverity(both));
    }

    // ── Air ──────────────────────────────────────────────────────────────

    /** Good and moderate air raise nothing at all. */
    @Test
    public void ordinaryAirRaisesNoHazard() {
        assertNull(EnvironmentHazards.airSeverity(new AirQuality(12, null, null, null)));
        assertNull(EnvironmentHazards.airSeverity(new AirQuality(50, null, null, null)));
        assertNull(EnvironmentHazards.airSeverity(new AirQuality(90, null, null, null)));
        assertNull(EnvironmentHazards.airSeverity(
            new AirQuality(AirQuality.MODERATE_MAX, null, null, null)));
    }

    /** No reading is not a clean reading. */
    @Test
    public void noAirReadingRaisesNoHazard() {
        assertNull(EnvironmentHazards.airSeverity(null));
    }

    /**
     * The threshold is the EPA's own "unhealthy for sensitive groups" line —
     * the first band where the official advice is to change what you do.
     */
    @Test
    public void airBecomesAHazardAtTheSensitiveBand() {
        assertEquals(Severity.MODERATE, EnvironmentHazards.airSeverity(
            new AirQuality(EnvironmentHazards.AIR_ALERT_FROM, null, null, null)));
    }

    /** Past "unhealthy" it goes high — that is wildfire-smoke territory. */
    @Test
    public void veryUnhealthyAirIsHigh() {
        assertEquals(Severity.HIGH, EnvironmentHazards.airSeverity(
            new AirQuality(AirQuality.UNHEALTHY_MAX + 1, null, null, null)));
        assertEquals(Severity.HIGH, EnvironmentHazards.airSeverity(
            new AirQuality(420, null, null, null)));
    }

    /** Unhealthy itself is still moderate: a reason to cut it short, not to flee. */
    @Test
    public void unhealthyAirStaysModerate() {
        assertEquals(Severity.MODERATE, EnvironmentHazards.airSeverity(
            new AirQuality(AirQuality.UNHEALTHY_MAX, null, null, null)));
    }

    // ── The scale itself ─────────────────────────────────────────────────

    /** The outdoor score is ordered and bounded, since the picnic weight rides on it. */
    @Test
    public void theOutdoorScoreIsOrderedAndBounded() {
        int previous = 101;
        for (int aqi = 0; aqi <= 500; aqi += 10) {
            int score = new AirQuality(aqi, null, null, null).outdoorScore();
            org.junit.Assert.assertTrue("out of range at " + aqi, score >= 0 && score <= 100);
            org.junit.Assert.assertTrue("worse air scored better at " + aqi, score <= previous);
            previous = score;
        }
        assertEquals(100, new AirQuality(0, null, null, null).outdoorScore());
        assertEquals(0, new AirQuality(300, null, null, null).outdoorScore());
    }
}
