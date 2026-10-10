package com.example.myapplication.model;

import com.example.myapplication.R;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The scoring rules behind "good for today".
 *
 * <p>These numbers decide which beach the app sends somebody to, so what is
 * pinned here is the behaviour a user would notice: that the four activities
 * genuinely disagree, that an unknown reading is not treated as a bad one, and
 * that no amount of easy parking can talk the app into recommending a beach
 * under a serious warning.
 */
public class ActivityScorerTest {

    private Beach beach() {
        return new Beach("CA1", "Test Beach", 32.86, -117.25,
            Collections.emptyList(), null, null, null);
    }

    private ActivityScorer.Inputs inputs(Double waveFt, Double windMph, Double tempF) {
        ActivityScorer.Inputs in = new ActivityScorer.Inputs();
        in.conditions = new BeachConditions(tempF, 5, windMph, waveFt, 10.0);
        return in;
    }

    private int scoreOf(Activity activity, ActivityScorer.Inputs in) {
        return ActivityScorer.score(activity, beach(), in).getScore();
    }

    // ── The band primitive ───────────────────────────────────────────────

    /**
     * The ideal range peaks at its centre and gives up only a little at its
     * edges. Both halves matter: the peak is what lets a coast of pleasant
     * beaches be ordered at all, and the shallowness is what stops the app
     * claiming two-foot surf is meaningfully worse than three.
     */
    @Test
    public void theIdealRangePeaksAtItsCentre() {
        assertEquals(100, ActivityScorer.band(2.0, 1.0, 3.0, 0.0, 8.0));

        int atEdge = ActivityScorer.band(1.0, 1.0, 3.0, 0.0, 8.0);
        assertEquals(atEdge, ActivityScorer.band(3.0, 1.0, 3.0, 0.0, 8.0));
        assertTrue("the ideal range must not be flat, or nothing can be ranked",
            atEdge < 100);
        assertTrue("but its edges are still very good, got " + atEdge, atEdge >= 85);
    }

    /**
     * The regression this shape exists for. Every reading landing inside its
     * ideal range must not produce the same score at every beach — that is what
     * happened on the first calm day it met real data, and eleven beaches all
     * came back 100/100.
     */
    @Test
    public void aCalmDayStillSeparatesBeaches() {
        int modest = scoreOf(Activity.SURF, inputs(3.2, 4.0, 74.0));
        int prime = scoreOf(Activity.SURF, inputs(5.5, 4.0, 74.0));
        assertTrue("five feet should out-rank three for surfing: "
            + prime + " vs " + modest, prime > modest);
    }

    /** And it falls to nothing at the outer bounds, from both directions. */
    @Test
    public void theBandFallsToZeroAtItsEdges() {
        assertEquals(0, ActivityScorer.band(0.0, 1.0, 3.0, 0.0, 8.0));
        assertEquals(0, ActivityScorer.band(8.0, 1.0, 3.0, 0.0, 8.0));
        assertEquals(0, ActivityScorer.band(-5.0, 1.0, 3.0, 0.0, 8.0));
        assertEquals(0, ActivityScorer.band(99.0, 1.0, 3.0, 0.0, 8.0));
    }

    /** Between the two it slides rather than stepping. */
    @Test
    public void theBandSlidesInBetween() {
        int half = ActivityScorer.band(5.5, 1.0, 3.0, 0.0, 8.0);
        assertTrue("expected a middling score, got " + half, half > 30 && half < 70);
        // And monotonically: further from ideal is never better.
        assertTrue(ActivityScorer.band(4.0, 1.0, 3.0, 0.0, 8.0)
            > ActivityScorer.band(6.0, 1.0, 3.0, 0.0, 8.0));
    }

    // ── The activities genuinely differ ──────────────────────────────────

    /**
     * The point of having four. Six feet of surf is what a surfer came for and
     * what a swimmer should avoid; if these two ever agree, the whole feature
     * is just one number wearing four hats.
     */
    @Test
    public void bigSurfSuitsSurfersAndNotSwimmers() {
        ActivityScorer.Inputs big = inputs(6.0, 6.0, 78.0);
        assertTrue("surfing should like six feet",
            scoreOf(Activity.SURF, big) > scoreOf(Activity.SWIM, big));
    }

    /** And the reverse: a flat warm day is for swimming, not surfing. */
    @Test
    public void flatWaterSuitsSwimmersAndNotSurfers() {
        ActivityScorer.Inputs flat = inputs(1.5, 4.0, 80.0);
        assertTrue("swimming should like a small clean day",
            scoreOf(Activity.SWIM, flat) > scoreOf(Activity.SURF, flat));
    }

    /** Wind is what ruins a picnic, and it should show up as the limit. */
    @Test
    public void windSinksAPicnic() {
        int calm = scoreOf(Activity.PICNIC, inputs(2.0, 3.0, 76.0));
        int blowing = scoreOf(Activity.PICNIC, inputs(2.0, 22.0, 76.0));
        assertTrue("wind should cost a picnic dearly: " + calm + " vs " + blowing,
            calm - blowing >= 25);
    }

    /**
     * The rocks rating is read one way for swimming and the opposite way for
     * fishing — shingle is a problem to walk in over and structure to fish
     * over. Same number, inverted.
     */
    @Test
    public void rocksHelpFishingAndHurtSwimming() {
        ActivityScorer.Inputs rocky = inputs(2.0, 6.0, 76.0);
        rocky.rockRating = 9;
        ActivityScorer.Inputs sandy = inputs(2.0, 6.0, 76.0);
        sandy.rockRating = 0;

        assertTrue("fishing should prefer the rocky beach",
            scoreOf(Activity.FISH, rocky) > scoreOf(Activity.FISH, sandy));
        assertTrue("swimming should prefer the sandy beach",
            scoreOf(Activity.SWIM, sandy) > scoreOf(Activity.SWIM, rocky));
    }

    // ── Unknown is not the same as bad ───────────────────────────────────

    /**
     * A beach with no wave model must not rank below one with genuinely awful
     * surf. One is unknown, the other is bad, and scoring a missing reading as
     * zero would quietly bury every beach the forecast does not cover.
     */
    @Test
    public void aMissingReadingIsNotScoredAsZero() {
        ActivityScorer.Inputs unknown = new ActivityScorer.Inputs();
        unknown.conditions = new BeachConditions(78.0, 5, 6.0, null, 10.0);

        ActivityScorer.Inputs terrible = inputs(20.0, 6.0, 78.0);

        assertTrue("unknown surf should beat known-terrible surf",
            scoreOf(Activity.SURF, unknown) > scoreOf(Activity.SURF, terrible));
    }

    /** With nothing known at all, the fit says so rather than inventing a number. */
    @Test
    public void nothingKnownIsNotConfident() {
        ActivityScorer.Inputs empty = new ActivityScorer.Inputs();
        ActivityFit fit = ActivityScorer.score(Activity.SWIM, beach(), empty);
        assertTrue("a fit built on nothing must not claim confidence",
            !fit.isConfident());
        assertTrue("and it should have found no factors at all",
            fit.getFactors().isEmpty());
    }

    // ── Safety outranks everything ───────────────────────────────────────

    /**
     * The rule that matters most. A weighted average lets good parking and warm
     * air cancel out a high-surf warning, which is the arithmetic that would
     * put "Great for swimming" over a beach flying a red flag. The ceiling
     * cannot be averaged away.
     */
    @Test
    public void aSeriousHazardCapsTheScore() {
        ActivityScorer.Inputs lovely = inputs(2.0, 2.0, 80.0);
        lovely.guarded = Boolean.TRUE;
        lovely.air = new AirQuality(10, 2.0, 4.0, 30.0);
        lovely.parking = new ParkingSummary(9, 9, 0, 800, 5);

        int unwarned = scoreOf(Activity.SWIM, lovely);
        lovely.worstHazard = Severity.HIGH;
        int warned = scoreOf(Activity.SWIM, lovely);

        assertTrue("a perfect day should score well unwarned", unwarned >= 80);
        // Exactly the ceiling, not merely near it. A cap the weighted average
        // can step over is not a cap - an earlier version used a heavy factor
        // instead of a minimum and let a moderate warning score 77 against a
        // ceiling of 75.
        assertEquals(ActivityScorer.hazardCeiling(Activity.SWIM, Severity.HIGH), warned);
    }

    /**
     * And the screen says so. A capped score must name the hazard as what held
     * it back — not go on to congratulate the beach on its water quality, which
     * is what it did while a moderate ceiling of exactly 75 was being swallowed
     * by the "still decent" filter.
     */
    @Test
    public void aCappedScoreBlamesTheHazard() {
        ActivityScorer.Inputs lovely = inputs(2.0, 2.0, 80.0);
        lovely.guarded = Boolean.TRUE;
        lovely.water = WaterQuality.evaluate(beach(), new double[] {0, 0, 0, 0, 0});
        lovely.worstHazard = Severity.MODERATE;

        ActivityFit fit = ActivityScorer.score(Activity.SWIM, beach(), lovely);
        ActivityFit.Factor limiting = fit.getLimitingFactor();
        assertTrue("a capped fit must name a limiting factor", limiting != null);
        assertEquals(R.string.factor_hazards, limiting.getLabelRes());
    }

    /** The ceiling holds for every severity and every activity. */
    @Test
    public void noActivityEverExceedsItsHazardCeiling() {
        for (Severity severity : Severity.values()) {
            for (Activity activity : Activity.values()) {
                ActivityScorer.Inputs in = inputs(3.0, 3.0, 78.0);
                in.guarded = Boolean.TRUE;
                in.air = new AirQuality(5, 1.0, 2.0, 20.0);
                in.parking = new ParkingSummary(9, 9, 0, 900, 6);
                in.worstHazard = severity;
                int ceiling = ActivityScorer.hazardCeiling(activity, severity);
                assertTrue(activity + " under " + severity + " scored past its ceiling",
                    scoreOf(activity, in) <= ceiling);
            }
        }
    }

    /** Extreme leaves nothing standing, for any activity including surfing. */
    @Test
    public void anExtremeHazardSinksEveryActivity() {
        for (Activity activity : Activity.values()) {
            ActivityScorer.Inputs in = inputs(5.0, 5.0, 80.0);
            in.worstHazard = Severity.EXTREME;
            assertTrue(activity + " must not score well under an extreme warning",
                scoreOf(activity, in) <= 25);
        }
    }

    /**
     * Surfing is the one exception, and only to the high-surf band: big waves
     * are what a surfer is reading the screen for. It is still capped - just
     * not as hard as swimming.
     */
    @Test
    public void surfingIsLessPunishedByAHighHazardThanSwimming() {
        assertTrue(ActivityScorer.hazardCeiling(Activity.SURF, Severity.HIGH)
            > ActivityScorer.hazardCeiling(Activity.SWIM, Severity.HIGH));
        // But never exempt.
        assertTrue(ActivityScorer.hazardCeiling(Activity.SURF, Severity.HIGH) < 100);
    }

    /** No hazard, no ceiling. */
    @Test
    public void noHazardMeansNoCap() {
        for (Activity activity : Activity.values()) {
            assertEquals(100, ActivityScorer.hazardCeiling(activity, null));
        }
    }

    // ── Every activity produces something usable ─────────────────────────

    /** All four score, and all four stay on the scale, for a plausible day. */
    @Test
    public void everyActivityScoresOnTheScale() {
        ActivityScorer.Inputs in = inputs(3.0, 8.0, 75.0);
        in.air = new AirQuality(42, 8.0, 14.0, 50.0);
        in.water = WaterQuality.evaluate(beach(), new double[] {0, 0, 0, 0, 0});
        in.parking = new ParkingSummary(4, 3, 1, 220, 2);
        in.rockRating = 4;

        for (ActivityFit fit : ActivityScorer.scoreAll(beach(), in)) {
            assertTrue(fit.getActivity() + " off the scale: " + fit.getScore(),
                fit.getScore() >= 0 && fit.getScore() <= 100);
            assertTrue(fit.getActivity() + " should be confident with this much known",
                fit.isConfident());
        }
        assertEquals(Arrays.asList(Activity.values()).size(),
            ActivityScorer.scoreAll(beach(), in).size());
    }

    // ── Shared inputs ────────────────────────────────────────────────────

    /**
     * A rainfall fetch that failed is left out of the blend rather than
     * scored as spotless water: the beach and the ranking would otherwise
     * both credit a dry three days nobody checked.
     */
    @Test
    public void uncheckedWaterIsLeftOutOfTheBlend() {
        ActivityScorer.Inputs without = inputs(2.0, 6.0, 80.0);
        ActivityScorer.Inputs unchecked = inputs(2.0, 6.0, 80.0);
        unchecked.water = WaterQuality.evaluate(beach(), new double[0]);

        ActivityFit a = ActivityScorer.score(Activity.SWIM, beach(), without);
        ActivityFit b = ActivityScorer.score(Activity.SWIM, beach(), unchecked);
        assertEquals(a.getFactors().size(), b.getFactors().size());
        assertEquals(a.getScore(), b.getScore());
    }

    /** One patrol rule for every screen that scores; see guardedState. */
    @Test
    public void guardedStateCountsRecordedAndMappedTowers() {
        Beach unknown = beach();
        assertNull("no help on record and none mapped: unknown, not unguarded",
            ActivityScorer.guardedState(unknown, null));
        assertNull(ActivityScorer.guardedState(unknown, Collections.emptyList()));

        HelpLocation tower = new HelpLocation("7", HelpType.LIFEGUARD_TOWER, 32.86, -117.25);
        assertEquals(Boolean.TRUE,
            ActivityScorer.guardedState(unknown, Collections.singletonList(tower)));

        Beach recorded = new Beach("CA1", "Test Beach", 32.86, -117.25,
            Collections.emptyList(), null, Collections.singletonList(tower), null);
        assertEquals(Boolean.TRUE, ActivityScorer.guardedState(recorded, null));

        HelpLocation hospital = new HelpLocation("Hospital", HelpType.HOSPITAL, 32.9, -117.2);
        Beach hospitalOnly = new Beach("CA1", "Test Beach", 32.86, -117.25,
            Collections.emptyList(), null, Collections.singletonList(hospital), null);
        assertEquals(Boolean.FALSE, ActivityScorer.guardedState(hospitalOnly, null));
    }

    @Test
    public void worstOfPicksTheMostSevereHazard() {
        assertNull(ActivityScorer.worstOf(Collections.emptyList()));
        assertEquals(Severity.HIGH, ActivityScorer.worstOf(Arrays.asList(
            new Hazard(HazardType.UV_INDEX, Severity.MODERATE, "uv", null),
            new Hazard(HazardType.HIGH_SURF, Severity.HIGH, "surf", null),
            new Hazard(HazardType.RIP_CURRENT, Severity.LOW, "rip", null))));
    }
}
