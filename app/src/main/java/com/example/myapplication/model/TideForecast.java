package com.example.myapplication.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

/**
 * The upcoming turns of the tide at a beach, as NOAA predicts them for the
 * station the beach's tides are read from.
 */
public class TideForecast {
    private final List<Tide> tides;
    private final TimeZone zone;

    public TideForecast(List<Tide> tides, TimeZone zone) {
        this.tides = Collections.unmodifiableList(new ArrayList<>(tides));
        this.zone = zone;
    }

    /** Every turn listed, in time order. */
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
}
