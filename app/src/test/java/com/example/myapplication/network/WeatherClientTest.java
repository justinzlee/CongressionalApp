package com.example.myapplication.network;

import com.example.myapplication.model.BeachConditions;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Pins how a live response is turned into {@link BeachConditions}.
 *
 * <p>The distinction that matters is <em>missing</em> versus <em>zero</em>.
 * {@code BeachConditions} and {@code SafetyLevel.evaluate} are both built
 * around nullable readings — null means "not known" and shows as "--" — but a
 * zero is a real measurement, and 0ft of surf clears every threshold in the
 * verdict. Parsing that substitutes zero for an absent field turns a failed
 * fetch into "Good to swim", which is the one answer this app must never give
 * without cause.
 */
public class WeatherClientTest {

    private static JsonObject json(String raw) {
        return JsonParser.parseString(raw).getAsJsonObject();
    }

    private static JsonObject fullForecast() {
        return json("{\"temperature_2m\":71.4,\"wind_speed_10m\":8.2,"
            + "\"visibility\":16093.0,\"uv_index\":6.6}");
    }

    @Test
    public void readsEveryForecastField() {
        BeachConditions c = WeatherClient.buildConditions(
            fullForecast(), json("{\"wave_height\":1.5}"));

        assertEquals(71.4, c.getAirTemperature(), 0.001);
        assertEquals(8.2, c.getWindSpeed(), 0.001);
        // 16093 m is ten miles
        assertEquals(10.0, c.getVisibility(), 0.01);
        // 1.5 m of swell is a shade under five feet
        assertEquals(4.921, c.getWaveHeight(), 0.001);
    }

    @Test
    public void uvIndexRoundsToNearestWholeStep() {
        // 6.6 is "Higher", and must not truncate down to 6.
        assertEquals(Integer.valueOf(7),
            WeatherClient.buildConditions(fullForecast(), null).getUvIndex());
    }

    @Test
    public void absentMarinePayloadLeavesSurfUnknown() {
        // Plenty of coastal points have no wave model at all. That has to read
        // as "no surf reading", not as a flat-calm zero.
        assertNull(WeatherClient.buildConditions(fullForecast(), null).getWaveHeight());
    }

    @Test
    public void marinePayloadWithoutWaveHeightLeavesSurfUnknown() {
        assertNull(WeatherClient.buildConditions(fullForecast(), json("{}")).getWaveHeight());
    }

    @Test
    public void jsonNullWaveHeightIsUnknownRatherThanZero() {
        assertNull(WeatherClient.buildConditions(
            fullForecast(), json("{\"wave_height\":null}")).getWaveHeight());
    }

    @Test
    public void genuineFlatCalmIsStillReportedAsZero() {
        // The flip side: an actual zero reading must survive as zero, so the
        // null handling above cannot just be "treat falsy as missing".
        assertEquals(0.0, WeatherClient.buildConditions(
            fullForecast(), json("{\"wave_height\":0.0}")).getWaveHeight(), 0.001);
    }

    @Test
    public void missingForecastFieldsAreUnknownRatherThanZero() {
        BeachConditions c = WeatherClient.buildConditions(json("{}"), null);

        assertNull(c.getAirTemperature());
        assertNull(c.getWindSpeed());
        assertNull(c.getVisibility());
        assertNull(c.getUvIndex());
        assertNull(c.getWaveHeight());
    }

    @Test
    public void nonNumericFieldIsUnknownRatherThanThrowing() {
        // Open-Meteo has served a string in place of a number on error
        // payloads; that must degrade to "unknown", not take the request down.
        BeachConditions c = WeatherClient.buildConditions(
            json("{\"temperature_2m\":\"n/a\",\"wind_speed_10m\":8.2}"), null);

        assertNull(c.getAirTemperature());
        assertEquals(8.2, c.getWindSpeed(), 0.001);
    }
/**
     * Open-Meteo answers one coordinate with an object and several with an
     * array, and the whole beach list is now fetched a batch at a time. The
     * array has to be unpacked in request order, because the caller matches
     * each entry back to a beach by position — an off-by-one here would put
     * Maine’s surf report under a beach in Hawaii.
     */
    @Test
    public void readsBatchedResponseInRequestOrder() {
        List<JsonObject> currents = WeatherClient.readCurrents(
            "[{\"current\":{\"temperature_2m\":50.0}},"
                + "{\"current\":{\"temperature_2m\":80.0}},"
                + "{\"current\":{\"temperature_2m\":60.0}}]", 3);

        assertEquals(3, currents.size());
        assertEquals(50.0, WeatherClient.buildConditions(currents.get(0), null)
            .getAirTemperature(), 0.001);
        assertEquals(80.0, WeatherClient.buildConditions(currents.get(1), null)
            .getAirTemperature(), 0.001);
        assertEquals(60.0, WeatherClient.buildConditions(currents.get(2), null)
            .getAirTemperature(), 0.001);
    }

    @Test
    public void singleCoordinateStillAnswersAsOneObject() {
        List<JsonObject> currents = WeatherClient.readCurrents(
            "{\"current\":{\"temperature_2m\":71.4}}", 1);

        assertEquals(1, currents.size());
        assertNotNull(currents.get(0));
        assertEquals(71.4, WeatherClient.buildConditions(currents.get(0), null)
            .getAirTemperature(), 0.001);
    }

    /**
     * A short answer must leave the tail of the batch unknown rather than
     * shifting later beaches onto earlier readings.
     */
    @Test
    public void shortBatchLeavesTheRemainingPointsUnknown() {
        List<JsonObject> currents = WeatherClient.readCurrents(
            "[{\"current\":{\"temperature_2m\":50.0}}]", 3);

        assertEquals(3, currents.size());
        assertNotNull(currents.get(0));
        assertNull(currents.get(1));
        assertNull(currents.get(2));
    }

    @Test
    public void unparseableBatchYieldsNothingRatherThanThrowing() {
        assertTrue(WeatherClient.readCurrents("<html>502 Bad Gateway</html>", 3).isEmpty());
        assertTrue(WeatherClient.readCurrents("", 3).isEmpty());
    }

    /**
     * An entry Open-Meteo could not model has no {@code current} block. That
     * point is unknown; the rest of the batch is still good.
     */
    @Test
    public void entryWithoutCurrentIsUnknownButDoesNotSinkTheBatch() {
        List<JsonObject> currents = WeatherClient.readCurrents(
            "[{\"error\":true,\"reason\":\"No data\"},"
                + "{\"current\":{\"temperature_2m\":80.0}}]", 2);

        assertEquals(2, currents.size());
        assertNull(currents.get(0));
        assertEquals(80.0, WeatherClient.buildConditions(currents.get(1), null)
            .getAirTemperature(), 0.001);
    }
}
