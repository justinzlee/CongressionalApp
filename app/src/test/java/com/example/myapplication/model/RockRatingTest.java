package com.example.myapplication.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * The rocks scale is the one number in this app that comes from a person rather
 * than an instrument, so what each value means has to stay fixed. These pin the
 * decisions a reader of the scale depends on: which numbers count as rated,
 * which raise a hazard, and which reference picture anchors which value.
 */
public class RockRatingTest {

    /** Every value on the scale, and nothing off it, counts as a rating. */
    @Test
    public void onlyTheScaleCountsAsRated() {
        for (int rating = RockRating.MIN; rating <= RockRating.MAX; rating++) {
            assertTrue(rating + " is on the scale", RockRating.isRated(rating));
        }
        assertFalse(RockRating.isRated(RockRating.UNRATED));
        assertFalse(RockRating.isRated(-2));
        assertFalse(RockRating.isRated(RockRating.MAX + 1));
        assertFalse(RockRating.isRated(Integer.MAX_VALUE));
    }

    /**
     * Zero is a real answer meaning clean sand, and must never be confused with
     * nobody having rated the beach. Getting this wrong would make every
     * unrated beach claim to be the softest sand in the app.
     */
    @Test
    public void zeroIsARatingAndUnratedIsNot() {
        assertTrue(RockRating.isRated(0));
        assertFalse(RockRating.isRated(RockRating.UNRATED));
        assertNotEquals(0, RockRating.UNRATED);
    }

    /** Stored values from a restored backup can be anything at all. */
    @Test
    public void clampKeepsEverythingOnTheScale() {
        int[] inputs = {Integer.MIN_VALUE, -5, -1, 0, 3, 10, 11, 500, Integer.MAX_VALUE};
        for (int input : inputs) {
            int clamped = RockRating.clamp(input);
            assertTrue(input + " clamped to " + clamped,
                clamped >= RockRating.MIN && clamped <= RockRating.MAX);
        }
        assertEquals(RockRating.MIN, RockRating.clamp(-1));
        assertEquals(RockRating.MAX, RockRating.clamp(11));
        assertEquals(7, RockRating.clamp(7));
    }

    /**
     * The hazard threshold is where the scale stops being an inconvenience and
     * starts being ground you cannot cross barefoot. Below it nothing reaches
     * the Alerts tab, or shingle complaints would bury the surf warnings.
     */
    @Test
    public void onlyHighRatingsRaiseAHazard() {
        for (int rating = RockRating.MIN; rating < RockRating.HAZARD_FROM; rating++) {
            assertFalse(rating + " must not raise a hazard", RockRating.isHazard(rating));
        }
        for (int rating = RockRating.HAZARD_FROM; rating <= RockRating.MAX; rating++) {
            assertTrue(rating + " must raise a hazard", RockRating.isHazard(rating));
        }
    }

    /** An unrated beach has no rocks hazard, whatever the sentinel value is. */
    @Test
    public void unratedRaisesNoHazard() {
        assertFalse(RockRating.isHazard(RockRating.UNRATED));
        assertFalse(RockRating.isHazard(-99));
    }

    /** Every value maps to one of exactly the three drawn pictures. */
    @Test
    public void everyRatingAnchorsToADrawnReference() {
        for (int rating = RockRating.MIN; rating <= RockRating.MAX; rating++) {
            int reference = RockRating.nearestReference(rating);
            assertTrue("rating " + rating + " anchored to " + reference,
                reference == RockRating.REFERENCE_LOW
                    || reference == RockRating.REFERENCE_MID
                    || reference == RockRating.REFERENCE_HIGH);
        }
    }

    /** Each reference anchors to itself, or the highlight would sit on the wrong picture. */
    @Test
    public void referencesAnchorToThemselves() {
        assertEquals(RockRating.REFERENCE_LOW,
            RockRating.nearestReference(RockRating.REFERENCE_LOW));
        assertEquals(RockRating.REFERENCE_MID,
            RockRating.nearestReference(RockRating.REFERENCE_MID));
        assertEquals(RockRating.REFERENCE_HIGH,
            RockRating.nearestReference(RockRating.REFERENCE_HIGH));
    }

    /**
     * The anchor never moves backwards as the rating climbs. A slider dragged
     * from 0 to 10 must walk the highlight along the row in one direction.
     */
    @Test
    public void anchorsAdvanceMonotonically() {
        int previous = RockRating.nearestReference(RockRating.MIN);
        for (int rating = RockRating.MIN + 1; rating <= RockRating.MAX; rating++) {
            int reference = RockRating.nearestReference(rating);
            assertTrue("anchor went backwards at " + rating, reference >= previous);
            previous = reference;
        }
    }

    /** Every value has words for both halves of what it means. */
    @Test
    public void everyRatingHasALabelAndAdvice() {
        for (int rating = RockRating.MIN; rating <= RockRating.MAX; rating++) {
            assertNotEquals("no label for " + rating, 0, RockRating.labelRes(rating));
            assertNotEquals("no advice for " + rating, 0, RockRating.adviceRes(rating));
        }
    }

    /** The bands are ordered: the ends of the scale must not read the same. */
    @Test
    public void theEndsOfTheScaleReadDifferently() {
        assertNotEquals(RockRating.labelRes(RockRating.MIN), RockRating.labelRes(RockRating.MAX));
        assertNotEquals(RockRating.adviceRes(RockRating.MIN), RockRating.adviceRes(RockRating.MAX));
        assertNotEquals(RockRating.labelRes(RockRating.REFERENCE_LOW),
            RockRating.labelRes(RockRating.REFERENCE_HIGH));
    }

    /** Each drawn reference has a picture and a caption behind it. */
    @Test
    public void everyReferenceHasAPictureAndCaption() {
        int[] references = {
            RockRating.REFERENCE_LOW, RockRating.REFERENCE_MID, RockRating.REFERENCE_HIGH };
        for (int reference : references) {
            assertNotEquals("no picture for " + reference,
                0, RockRating.referenceImageRes(reference));
            assertNotEquals("no caption for " + reference,
                0, RockRating.referenceCaptionRes(reference));
        }
        // And the three are genuinely three, not one picture shown three times.
        assertNotEquals(RockRating.referenceImageRes(RockRating.REFERENCE_LOW),
            RockRating.referenceImageRes(RockRating.REFERENCE_MID));
        assertNotEquals(RockRating.referenceImageRes(RockRating.REFERENCE_MID),
            RockRating.referenceImageRes(RockRating.REFERENCE_HIGH));
    }
}
