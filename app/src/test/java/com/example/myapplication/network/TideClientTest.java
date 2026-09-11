package com.example.myapplication.network;

import com.example.myapplication.model.Tide;
import com.example.myapplication.model.TideForecast;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pins the reading of a NOAA predictions body. The times arrive in GMT and are
 * shown on the beach's own clock; getting either half wrong puts every tide
 * hours out, which is worse than showing none.
 */
public class TideClientTest {

    private static final TimeZone PACIFIC = TimeZone.getTimeZone("America/Los_Angeles");

    /**
     * NOAA's predictions for La Jolla (Scripps Pier) at the end of August 2026,
     * as served. The Open-Meteo sea-level series this app used to read put that
     * morning's low at 04:37 and its high at 11:00; NOAA has them at 05:13 and
     * 11:34.
     */
    private static final String LA_JOLLA = "{ \"predictions\" : [\n"
        + "{\"t\":\"2026-08-31 00:10\", \"v\":\"1.069\", \"type\":\"L\"},"
        + "{\"t\":\"2026-08-31 06:09\", \"v\":\"4.813\", \"type\":\"H\"},"
        + "{\"t\":\"2026-08-31 12:13\", \"v\":\"0.947\", \"type\":\"L\"},"
        + "{\"t\":\"2026-08-31 18:34\", \"v\":\"5.404\", \"type\":\"H\"},"
        + "{\"t\":\"2026-09-01 01:00\", \"v\":\"1.027\", \"type\":\"L\"}\n"
        + "]}";

    private static TideForecast parse(String json) throws IOException {
        return TideClient.parse(JsonParser.parseString(json).getAsJsonObject(), PACIFIC);
    }

    /** The tide's clock time as a reader standing at that beach would see it. */
    private static String localTime(Tide tide) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        format.setTimeZone(PACIFIC);
        return format.format(new Date(tide.getTimeMillis()));
    }

    private static long pacificInstant(int hour, int minute) {
        Calendar calendar = new GregorianCalendar(PACIFIC);
        calendar.clear();
        calendar.set(2026, Calendar.AUGUST, 31, hour, minute);
        return calendar.getTimeInMillis();
    }

    @Test
    public void readsEveryTurnInOrder() throws Exception {
        List<Tide> tides = parse(LA_JOLLA).getTides();

        Tide.Type[] expected = {
            Tide.Type.LOW, Tide.Type.HIGH, Tide.Type.LOW, Tide.Type.HIGH, Tide.Type.LOW };
        assertEquals(expected.length, tides.size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], tides.get(i).getType());
        }
    }

    @Test
    public void gmtTimesLandOnTheBeachesOwnClock() throws Exception {
        TideForecast forecast = parse(LA_JOLLA);

        // 12:13 GMT is 05:13 in La Jolla in summer — the time on NOAA's own
        // table for the station, and on the board at the lifeguard tower.
        assertEquals("2026-08-31 05:13", localTime(forecast.getTides().get(2)));
        assertEquals("2026-08-31 11:34", localTime(forecast.getTides().get(3)));
        assertEquals(PACIFIC, forecast.getZone());
    }

    @Test
    public void heightsAreFeetAsPrinted() throws Exception {
        assertEquals(5.404, parse(LA_JOLLA).getTides().get(3).getHeightFeet(), 0.0001);
    }

    @Test
    public void nextTurnsAreTheOnesStillAhead() throws Exception {
        TideForecast forecast = parse(LA_JOLLA);
        // Standing on the sand at 06:00: the morning low has been, the high is to come.
        long now = pacificInstant(6, 0);

        assertEquals("2026-08-31 11:34", localTime(forecast.getNextHigh(now)));
        assertEquals("2026-08-31 18:00", localTime(forecast.getNextLow(now)));
    }

    @Test
    public void higherHighsAndLowerLowsStillCount() throws Exception {
        List<Tide> tides = parse("{\"predictions\":["
            + "{\"t\":\"2026-08-31 06:09\",\"v\":\"4.8\",\"type\":\"HH\"},"
            + "{\"t\":\"2026-08-31 12:13\",\"v\":\"-0.3\",\"type\":\"LL\"}]}").getTides();

        assertEquals(Tide.Type.HIGH, tides.get(0).getType());
        assertEquals(Tide.Type.LOW, tides.get(1).getType());
    }

    /**
     * A station NOAA does not know comes back as an error object with no
     * predictions. That has to fail, not read as a day on which the tide never
     * turns.
     */
    @Test
    public void errorBodyIsAFailureNotAnEmptyTable() {
        try {
            parse("{\"error\": {\"message\":\" The station is not a valid station or there is system error.\"}}");
            fail("expected an IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("The station is not a valid station"));
        }
    }

    /**
     * NOAA publishes a station's standard offset, not its zone. Each offset on
     * these coasts has to name the zone that carries its daylight saving, or
     * every summer tide from Maine to Texas reads an hour early.
     */
    @Test
    public void stationOffsetsNameTheZoneWithItsDaylightSaving() {
        assertEquals("America/Los_Angeles", TideClient.zoneFor(-8).getID());
        assertEquals("America/Chicago", TideClient.zoneFor(-6).getID());
        assertEquals("America/New_York", TideClient.zoneFor(-5).getID());
        assertEquals("Pacific/Honolulu", TideClient.zoneFor(-10).getID());
        assertEquals("America/Puerto_Rico", TideClient.zoneFor(-4).getID());
    }

    @Test
    public void anUnlistedOffsetKeepsThePlainOffset() {
        assertEquals(9 * 3_600_000, TideClient.zoneFor(9).getRawOffset());
    }

    @Test
    public void beginDateIsWrittenInGmt() {
        // 06:00 in La Jolla in August is 13:00 GMT, which is what NOAA must be told.
        assertEquals("20260831 13:00", TideClient.beginDate(pacificInstant(6, 0)));
    }
}
