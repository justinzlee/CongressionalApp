package com.example.myapplication.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * The UV bands are now the one place the app decides what a reading is
 * called, so the boundaries between them are pinned here. Getting one wrong
 * would rename the reading on the home screen and mislabel the lesson that
 * teaches the scale, in the same move.
 */
public class UvLevelTest {

    @Test
    public void readingsFallInTheirPublishedBand() {
        assertEquals(UvLevel.LOW, UvLevel.of(0));
        assertEquals(UvLevel.LOW, UvLevel.of(2));
        assertEquals(UvLevel.MODERATE, UvLevel.of(3));
        assertEquals(UvLevel.MODERATE, UvLevel.of(5));
        assertEquals(UvLevel.HIGH, UvLevel.of(6));
        assertEquals(UvLevel.HIGH, UvLevel.of(7));
        assertEquals(UvLevel.VERY_HIGH, UvLevel.of(8));
        assertEquals(UvLevel.VERY_HIGH, UvLevel.of(10));
        assertEquals(UvLevel.EXTREME, UvLevel.of(11));
    }

    /**
     * The scale stops publishing names at 11, but the sun does not stop at
     * 11 — a tropical reading of 15 still has to land somewhere.
     */
    @Test
    public void readingsAboveTheScaleReadAsExtreme() {
        assertEquals(UvLevel.EXTREME, UvLevel.of(15));
    }

    /** Nothing sensible sits below 0, but a bad feed must not crash a lesson. */
    @Test
    public void readingsBelowTheScaleReadAsLow() {
        assertEquals(UvLevel.LOW, UvLevel.of(-1));
    }

    /** Each band's ceiling is the next one's floor, less one, with no gaps. */
    @Test
    public void bandsMeetWithoutGaps() {
        UvLevel[] bands = UvLevel.values();
        for (int i = 0; i < bands.length - 1; i++) {
            assertEquals(Integer.valueOf(bands[i + 1].getMin() - 1), bands[i].getMax());
        }
    }

    /** The top band is open-ended, which is how the lesson knows to print "and up". */
    @Test
    public void topBandHasNoCeiling() {
        assertNull(UvLevel.EXTREME.getMax());
    }

    /** The verdict's "extreme UV" and the lesson's top band are the same reading. */
    @Test
    public void verdictThresholdIsTheTopBand() {
        assertEquals(UvLevel.EXTREME.getMin(), SafetyLevel.CAUTION_UV);
    }
}
