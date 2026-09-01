package com.example.myapplication.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

/**
 * The upcoming turns of the tide, read off an hourly sea-level series rather
 * than a published tide table - the same forecast that feeds the rest of the
 * conditions already carries sea level, so no second source is needed.
 */
public class TideForecast {
    /** Widest a fitted turn may sit from its sample, in samples. */
    private static final double MAX_SHIFT = 0.5;

    private final List<Tide> tides;
    private final TimeZone zone;

    public TideForecast(List<Tide> tides, TimeZone zone) {
        this.tides = Collections.unmodifiableList(new ArrayList<>(tides));
        this.zone = zone;
    }

    /** Every turn found, in time order. */
    public List<Tide> getTides() { return tides; }

    /** The beach's own clock, which is not always the phone's. */
    public TimeZone getZone() { return zone; }

    public Tide getNextHigh(long nowMillis) { return next(Tide.Type.HIGH, nowMillis); }

    public Tide getNextLow(long nowMillis) { return next(Tide.Type.LOW, nowMillis); }

    private Tide next(Tide.Type type, long nowMillis) {
        for (Tide tide : tides) {
            if (tide.getType() == type && tide.getTimeMillis() >= nowMillis) return tide;
        }
        return null;
    }

    /**
     * Picks the highs and lows out of an evenly spaced sea-level series. Heights
     * may be in any unit; the turns come back in the same one.
     */
    public static TideForecast fromHourlySeries(long[] timesMillis, double[] heights, TimeZone zone) {
        List<Tide> turns = new ArrayList<>();
        int count = Math.min(timesMillis.length, heights.length);

        for (int i = 1; i < count - 1; i++) {
            double previous = heights[i - 1], current = heights[i], next = heights[i + 1];
            boolean high = current > previous && current >= next;
            boolean low = current < previous && current <= next;
            if (!high && !low) continue;

            // An hourly series almost never samples the turn itself, so fit a
            // parabola through the three points and take its vertex. That pulls
            // the time to within a few minutes of real slack water instead of
            // rounding it to the hour.
            double curvature = previous - 2 * current + next;
            double shift = 0;
            if (Math.abs(curvature) > 1e-9) {
                shift = 0.5 * (previous - next) / curvature;
                // A true extremum's vertex lies between its neighbours; anything
                // further out means the curve is too flat to fit, so sit on the
                // sample itself.
                if (shift < -MAX_SHIFT || shift > MAX_SHIFT) shift = 0;
            }

            long step = timesMillis[i + 1] - timesMillis[i];
            long time = timesMillis[i] + Math.round(shift * step);
            double height = current - 0.25 * (previous - next) * shift;
            turns.add(new Tide(high ? Tide.Type.HIGH : Tide.Type.LOW, time, height));
        }

        return new TideForecast(turns, zone);
    }
}
