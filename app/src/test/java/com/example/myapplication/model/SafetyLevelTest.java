package com.example.myapplication.model;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * The swim verdict is the most safety-critical derivation in the app — it is
 * the single answer the home screen leads with — so each rule that can push a
 * beach up the scale is pinned here.
 */
public class SafetyLevelTest {

    private static final double LAT = 32.8589;
    private static final double LON = -117.2558;

    private Beach beach(BeachConditions conditions, List<Hazard> hazards) {
        return new Beach("t1", "Test Beach", LAT, LON, hazards, conditions, null, null);
    }

    /** Calm water, mild sun: nothing to warn about. */
    private BeachConditions calm() {
        return new BeachConditions(72.0, 3, 5.0, 0.5, 10.0);
    }

    private Hazard hazard(Severity severity) {
        return new Hazard(HazardType.RIP_CURRENT, severity, "Test hazard", null);
    }

    @Test
    public void nullBeach_hasNoVerdict() {
        assertNull(SafetyLevel.evaluate(null));
    }

    @Test
    public void noConditionsAndNoHazards_hasNoVerdict() {
        // Nothing to judge from must not read as "safe".
        assertNull(SafetyLevel.evaluate(beach(null, null)));
    }

    @Test
    public void calmConditions_areSafe() {
        assertEquals(SafetyLevel.SAFE, SafetyLevel.evaluate(beach(calm(), null)));
    }

    @Test
    public void moderateSurf_isCaution() {
        BeachConditions conditions = new BeachConditions(72.0, 3, 5.0, 3.5, 10.0);
        assertEquals(SafetyLevel.CAUTION, SafetyLevel.evaluate(beach(conditions, null)));
    }

    @Test
    public void surfJustUnderCautionThreshold_staysSafe() {
        BeachConditions conditions = new BeachConditions(72.0, 3, 5.0, 2.9, 10.0);
        assertEquals(SafetyLevel.SAFE, SafetyLevel.evaluate(beach(conditions, null)));
    }

    @Test
    public void heavySurf_isDanger() {
        BeachConditions conditions = new BeachConditions(72.0, 3, 5.0, 6.5, 10.0);
        assertEquals(SafetyLevel.DANGER, SafetyLevel.evaluate(beach(conditions, null)));
    }

    @Test
    public void veryHighUv_isCaution() {
        BeachConditions conditions = new BeachConditions(72.0, 9, 5.0, 0.5, 10.0);
        assertEquals(SafetyLevel.CAUTION, SafetyLevel.evaluate(beach(conditions, null)));
    }

    @Test
    public void moderateHazard_isCaution() {
        assertEquals(SafetyLevel.CAUTION,
            SafetyLevel.evaluate(beach(calm(), Collections.singletonList(hazard(Severity.MODERATE)))));
    }

    @Test
    public void lowHazardOverCalmWater_staysSafe() {
        assertEquals(SafetyLevel.SAFE,
            SafetyLevel.evaluate(beach(calm(), Collections.singletonList(hazard(Severity.LOW)))));
    }

    @Test
    public void highHazard_overridesCalmWater() {
        assertEquals(SafetyLevel.DANGER,
            SafetyLevel.evaluate(beach(calm(), Collections.singletonList(hazard(Severity.HIGH)))));
    }

    @Test
    public void extremeHazard_overridesCalmWater() {
        assertEquals(SafetyLevel.DANGER,
            SafetyLevel.evaluate(beach(calm(), Collections.singletonList(hazard(Severity.EXTREME)))));
    }

    @Test
    public void hazardsAloneStillProduceAVerdict() {
        // No live conditions yet, but a known hazard is enough to judge.
        assertEquals(SafetyLevel.DANGER,
            SafetyLevel.evaluate(beach(null, Collections.singletonList(hazard(Severity.HIGH)))));
    }

    @Test
    public void missingWaveReadingDoesNotCrash() {
        BeachConditions partial = new BeachConditions(72.0, null, null, null, null);
        assertEquals(SafetyLevel.SAFE, SafetyLevel.evaluate(beach(partial, null)));
    }

    @Test
    public void worstSignalWins() {
        // Moderate hazard plus heavy surf: the surf escalates it to danger.
        BeachConditions heavy = new BeachConditions(72.0, 3, 5.0, 8.0, 10.0);
        assertEquals(SafetyLevel.DANGER,
            SafetyLevel.evaluate(beach(heavy, Collections.singletonList(hazard(Severity.MODERATE)))));
    }
}
