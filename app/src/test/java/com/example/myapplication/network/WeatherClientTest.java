package com.example.myapplication.network;

import com.example.myapplication.model.BeachConditions;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
}
