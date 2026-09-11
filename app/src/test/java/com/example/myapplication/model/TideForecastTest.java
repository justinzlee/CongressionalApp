package com.example.myapplication.model;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * The turns come from NOAA ready-made; what is left to get right here is
 * picking the next of each from wherever the reader is standing in time.
 */
public class TideForecastTest {

    private static final long HOUR = 3_600_000L;
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    /** A clean day: highs at 3 and 15, lows at 9 and 21. */
    private static TideForecast day() {
        return new TideForecast(Arrays.asList(
            new Tide(Tide.Type.HIGH, 3 * HOUR, 5.0),
            new Tide(Tide.Type.LOW, 9 * HOUR, 0.4),
            new Tide(Tide.Type.HIGH, 15 * HOUR, 5.3),
            new Tide(Tide.Type.LOW, 21 * HOUR, -0.2)), UTC);
    }

    @Test
    public void nextTurnSkipsOnesAlreadyPast() {
        // Standing at hour 5, the high at hour 3 is behind us.
        assertEquals(15 * HOUR, day().getNextHigh(5 * HOUR).getTimeMillis());
        assertEquals(9 * HOUR, day().getNextLow(5 * HOUR).getTimeMillis());
    }

    @Test
    public void aTurnHappeningNowIsStillTheNextOne() {
        assertEquals(9 * HOUR, day().getNextLow(9 * HOUR).getTimeMillis());
    }

    @Test
    public void nextTurnPastTheEndOfTheList_isAbsent() {
        assertNull(day().getNextHigh(100 * HOUR));
        assertNull(day().getNextLow(100 * HOUR));
    }

    @Test
    public void zoneIsCarriedThrough() {
        TimeZone pacific = TimeZone.getTimeZone("America/Los_Angeles");
        assertEquals(pacific, new TideForecast(Collections.emptyList(), pacific).getZone());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void turnsCannotBeRewrittenFromOutside() {
        day().getTides().add(new Tide(Tide.Type.HIGH, 0, 1.0));
    }
}
