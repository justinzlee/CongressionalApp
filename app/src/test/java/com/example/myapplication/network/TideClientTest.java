package com.example.myapplication.network;

import com.example.myapplication.model.Tide;
import com.example.myapplication.model.TideForecast;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Pins the reading of a marine-forecast body, above all the timestamp maths:
 * the series arrives on the beach's wall clock with its offset alongside, and
 * shifting by that offset the wrong way would put every tide hours out.
 */
public class TideClientTest {

    private static final TimeZone PACIFIC = TimeZone.getTimeZone("America/Los_Angeles");

    /** A morning off La Jolla, trimmed from a live response. */
    private static final String LA_JOLLA = "{"
        + "\"utc_offset_seconds\":-25200,"
        + "\"timezone\":\"America/Los_Angeles\","
        + "\"hourly\":{"
        + "  \"time\":[\"2026-08-31T00:00\",\"2026-08-31T01:00\",\"2026-08-31T02:00\","
        + "            \"2026-08-31T03:00\",\"2026-08-31T04:00\",\"2026-08-31T05:00\","
        + "            \"2026-08-31T06:00\",\"2026-08-31T07:00\",\"2026-08-31T08:00\","
        + "            \"2026-08-31T09:00\",\"2026-08-31T10:00\",\"2026-08-31T11:00\","
        + "            \"2026-08-31T12:00\",\"2026-08-31T13:00\"],"
        + "  \"sea_level_height_msl\":[0.71,0.46,0.17,-0.11,-0.29,-0.31,-0.17,"
        + "                            0.09,0.41,0.73,0.97,1.05,0.97,0.75]"
        + "}}";

    private static TideForecast parse(String json) throws Exception {
        return TideClient.parse(JsonParser.parseString(json).getAsJsonObject());
    }

    /** The tide's clock time as a reader standing at that beach would see it. */
    private static String localTime(Tide tide, TimeZone zone) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        format.setTimeZone(zone);
        return format.format(new Date(tide.getTimeMillis()));
    }

    private static long pacificInstant(int hour, int minute) {
        Calendar calendar = new GregorianCalendar(PACIFIC);
        calendar.clear();
        calendar.set(2026, Calendar.AUGUST, 31, hour, minute);
        return calendar.getTimeInMillis();
    }

    @Test
    public void readsBothTurnsOnTheBeachesOwnClock() throws Exception {
        TideForecast forecast = parse(LA_JOLLA);

        assertEquals(PACIFIC, forecast.getZone());
        assertEquals(2, forecast.getTides().size());

        // The water is still falling into the 05:00 sample, so the fitted turn
        // sits before it - and it must read as morning Pacific time, not as the
        // 12:00 UTC instant underneath.
        Tide low = forecast.getTides().get(0);
        assertEquals(Tide.Type.LOW, low.getType());
        assertEquals("2026-08-31 04:37", localTime(low, forecast.getZone()));

        Tide high = forecast.getTides().get(1);
        assertEquals(Tide.Type.HIGH, high.getType());
        assertEquals("2026-08-31 11:00", localTime(high, forecast.getZone()));
    }

    @Test
    public void heightsComeBackInFeet() throws Exception {
        Tide high = parse(LA_JOLLA).getTides().get(1);
        // 1.05 m of sea level is about 3.4 ft, not 1.05 of anything.
        assertEquals(3.44, high.getHeightFeet(), 0.05);
    }

    @Test
    public void nextTurnsAreTheOnesStillAhead() throws Exception {
        TideForecast forecast = parse(LA_JOLLA);
        // Standing on the sand at 06:00 Pacific: the low is behind, the high ahead.
        long now = pacificInstant(6, 0);

        assertEquals(null, forecast.getNextLow(now));
        Tide high = forecast.getNextHigh(now);
        assertNotNull(high);
        assertEquals("2026-08-31 11:00", localTime(high, forecast.getZone()));
    }

    @Test
    public void aGapInTheSeriesStopsTheRead() throws Exception {
        String withGap = LA_JOLLA.replace("0.09,0.41", "null,0.41");
        TideForecast forecast = parse(withGap);

        // Only the run before the gap keeps its even hourly spacing, so the low
        // survives and the high beyond the gap is not guessed at.
        assertEquals(1, forecast.getTides().size());
        assertEquals(Tide.Type.LOW, forecast.getTides().get(0).getType());
    }

    @Test
    public void unknownZoneFallsBackToTheReportedOffset() throws Exception {
        String oddZone = LA_JOLLA.replace("America/Los_Angeles", "Nowhere/Atlantis");
        TideForecast forecast = parse(oddZone);

        assertEquals(-25200 * 1000, forecast.getZone().getRawOffset());
        assertTrue(forecast.getTides().size() >= 1);
    }
}
