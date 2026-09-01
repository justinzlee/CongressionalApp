package com.example.myapplication.model;

import org.junit.Test;

import java.util.List;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Checks that the highs and lows come out of an hourly sea-level series where
 * the water actually turns - including between two samples, which is where a
 * real tide almost always turns.
 */
public class TideForecastTest {

    private static final long HOUR = 3_600_000L;
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static final long MINUTE_TOLERANCE = 6 * 60_000L;

    /** An hourly clock starting at zero, one entry per height. */
    private static long[] hourlyTimes(int count) {
        long[] times = new long[count];
        for (int i = 0; i < count; i++) times[i] = i * HOUR;
        return times;
    }

    private static TideForecast forecastOf(double... heights) {
        return TideForecast.fromHourlySeries(hourlyTimes(heights.length), heights, UTC);
    }

    @Test
    public void findsAlternatingHighsAndLows() {
        // A clean 12-hour tidal cycle, first peaking at hour 3.
        double[] heights = new double[24];
        for (int i = 0; i < heights.length; i++) {
            heights[i] = Math.cos(2 * Math.PI * (i - 3) / 12.0);
        }

        List<Tide> tides = forecastOf(heights).getTides();

        // Two full cycles fit in 24 hours: highs at 3 and 15, lows at 9 and 21.
        assertEquals(4, tides.size());
        assertEquals(Tide.Type.HIGH, tides.get(0).getType());
        assertEquals(Tide.Type.LOW, tides.get(1).getType());
        assertEquals(Tide.Type.HIGH, tides.get(2).getType());
        assertEquals(3 * HOUR, tides.get(0).getTimeMillis(), MINUTE_TOLERANCE);
        assertEquals(9 * HOUR, tides.get(1).getTimeMillis(), MINUTE_TOLERANCE);
        assertEquals(15 * HOUR, tides.get(2).getTimeMillis(), MINUTE_TOLERANCE);
        assertEquals(Tide.Type.LOW, tides.get(3).getType());
        assertEquals(21 * HOUR, tides.get(3).getTimeMillis(), MINUTE_TOLERANCE);
    }

    @Test
    public void turnBetweenTwoSamples_landsBetweenThem() {
        // Symmetric shoulders around a peak the sampling missed: the real high
        // is midway between hours 2 and 3, not on either of them.
        List<Tide> tides = forecastOf(0.0, 0.5, 1.0, 1.0, 0.5, 0.0).getTides();

        assertEquals(1, tides.size());
        assertEquals(Tide.Type.HIGH, tides.get(0).getType());
        assertEquals(HOUR * 5 / 2, tides.get(0).getTimeMillis());
    }

    @Test
    public void fittedHeightReachesPastTheHighestSample() {
        // The water keeps rising between samples, so the fitted peak has to sit
        // above the tallest reading rather than equal it.
        List<Tide> tides = forecastOf(0.0, 0.8, 1.0, 0.9, 0.2).getTides();

        assertEquals(1, tides.size());
        assertTrue(tides.get(0).getHeightFeet() > 1.0);
    }

    @Test
    public void steadyWater_hasNoTurns() {
        assertTrue(forecastOf(1.0, 1.0, 1.0, 1.0, 1.0).getTides().isEmpty());
    }

    @Test
    public void risingWater_hasNoTurnsInsideTheWindow() {
        assertTrue(forecastOf(0.0, 1.0, 2.0, 3.0, 4.0).getTides().isEmpty());
    }

    @Test
    public void shortSeries_hasNoTurns() {
        // Fewer than three samples leaves nothing with neighbours on both sides.
        assertTrue(forecastOf(0.0, 1.0).getTides().isEmpty());
    }

    @Test
    public void nextTurnSkipsOnesAlreadyPast() {
        double[] heights = new double[24];
        for (int i = 0; i < heights.length; i++) {
            heights[i] = Math.cos(2 * Math.PI * (i - 3) / 12.0);
        }
        TideForecast forecast = forecastOf(heights);

        // Standing at hour 5, the high at hour 3 is behind us.
        Tide high = forecast.getNextHigh(5 * HOUR);
        assertNotNull(high);
        assertEquals(15 * HOUR, high.getTimeMillis(), MINUTE_TOLERANCE);

        Tide low = forecast.getNextLow(5 * HOUR);
        assertNotNull(low);
        assertEquals(9 * HOUR, low.getTimeMillis(), MINUTE_TOLERANCE);
    }

    @Test
    public void nextTurnPastTheEndOfTheSeries_isAbsent() {
        TideForecast forecast = forecastOf(0.0, 1.0, 0.0, -1.0, 0.0, 1.0, 0.0);
        assertNull(forecast.getNextHigh(100 * HOUR));
        assertNull(forecast.getNextLow(100 * HOUR));
    }

    @Test
    public void realForecastSeries_turnsWhereTheWaterDoes() {
        // A day of Open-Meteo sea level off La Jolla: a trough around 05:00 and
        // a peak around 11:00, both plainly visible in the numbers.
        double[] heights = {
            0.71, 0.46, 0.17, -0.11, -0.29, -0.31, -0.17, 0.09,
            0.41, 0.73, 0.97, 1.05, 0.97, 0.75, 0.45, 0.13
        };
        List<Tide> tides = forecastOf(heights).getTides();

        assertEquals(2, tides.size());
        assertEquals(Tide.Type.LOW, tides.get(0).getType());
        assertTrue(tides.get(0).getTimeMillis() > 4 * HOUR);
        assertTrue(tides.get(0).getTimeMillis() < 6 * HOUR);
        assertEquals(Tide.Type.HIGH, tides.get(1).getType());
        assertTrue(tides.get(1).getTimeMillis() > 10 * HOUR);
        assertTrue(tides.get(1).getTimeMillis() < 12 * HOUR);
    }

    @Test
    public void zoneIsCarriedThrough() {
        TimeZone pacific = TimeZone.getTimeZone("America/Los_Angeles");
        TideForecast forecast = TideForecast.fromHourlySeries(
            hourlyTimes(3), new double[]{0.0, 1.0, 0.0}, pacific);
        assertEquals(pacific, forecast.getZone());
    }
}
