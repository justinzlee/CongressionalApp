package com.example.myapplication.network;

import com.example.myapplication.model.BeachConditions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.Locale;

import okhttp3.Request;
import okhttp3.Response;

public class WeatherClient {

    public interface WeatherCallback {
        void onSuccess(BeachConditions conditions);
        void onFailure(Exception e);
    }

    public void getBeachConditions(double lat, double lon, WeatherCallback callback) {
        Http.IO.execute(() -> {
            try {
                // Weather API: Temp (F), Wind (mph), Visibility (meters -> miles), UV Index
                String weatherUrl = String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,wind_speed_10m,visibility,uv_index&temperature_unit=fahrenheit&wind_speed_unit=mph",
                    lat, lon);

                // Marine API: Wave Height (meters -> feet)
                String marineUrl = String.format(Locale.US,
                    "https://marine-api.open-meteo.com/v1/marine?latitude=%.4f&longitude=%.4f&current=wave_height",
                    lat, lon);

                JsonObject current = fetchCurrent(weatherUrl);
                // The forecast call carries most of the tiles. If it did not
                // come back, report failure and let the caller keep whatever it
                // was already showing, rather than pushing up a conditions
                // object that is empty in every field.
                if (current == null) {
                    callback.onFailure(new IOException(
                        "Weather forecast unavailable for " + lat + ", " + lon));
                    return;
                }

                // Marine coverage is patchy — plenty of coastal points have no
                // wave model at all. That is a missing reading, not an error,
                // so the rest of the conditions still go through with a null
                // wave height and the surf tile shows "--".
                callback.onSuccess(buildConditions(current, fetchCurrent(marineUrl)));

            } catch (Exception e) {
                // Deliberately broad: Gson's parse and getAs* calls throw
                // unchecked (JsonSyntaxException, IllegalStateException,
                // NumberFormatException). Letting one escape the executor would
                // fire neither callback, leaving the screen stuck on its
                // placeholder forever.
                callback.onFailure(e);
            }
        });
    }

    /**
     * Assembles the readings from the two {@code current} payloads, converting
     * to the units the app displays in. Package-private so the unit tests can
     * pin the null handling without going near the network.
     *
     * <p>Every field stays null unless it was genuinely present. A fabricated
     * zero would read as a real measurement, and 0ft of surf clears every
     * threshold in {@code SafetyLevel.evaluate} — so a failed fetch would
     * render as "Good to swim" on a beach nothing is known about.
     *
     * @param current       the forecast payload; required
     * @param marineCurrent the wave payload, or {@code null} where there is no
     *                      marine model for the point
     */
    static BeachConditions buildConditions(JsonObject current, JsonObject marineCurrent) {
        Double temp = optDouble(current, "temperature_2m");
        Double wind = optDouble(current, "wind_speed_10m");

        Double visibility = null;
        Double visibilityMeters = optDouble(current, "visibility");
        if (visibilityMeters != null) {
            visibility = visibilityMeters * 0.000621371;
        }

        Integer uv = null;
        Double uvValue = optDouble(current, "uv_index");
        if (uvValue != null) {
            uv = (int) Math.round(uvValue);
        }

        Double waveHeight = null;
        if (marineCurrent != null) {
            Double waveMeters = optDouble(marineCurrent, "wave_height");
            if (waveMeters != null) {
                waveHeight = waveMeters * 3.28084;
            }
        }

        return new BeachConditions(temp, uv, wind, waveHeight, visibility);
    }

    /**
     * Runs a request and returns its {@code current} object, or {@code null} if
     * the call failed, the body was empty, or the payload had no such object.
     */
    private JsonObject fetchCurrent(String url) throws IOException {
        Request request = new Request.Builder().url(url).build();
        try (Response response = Http.CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }
            JsonElement parsed = JsonParser.parseString(response.body().string());
            if (!parsed.isJsonObject()) {
                return null;
            }
            JsonElement current = parsed.getAsJsonObject().get("current");
            return current != null && current.isJsonObject() ? current.getAsJsonObject() : null;
        }
    }

    /**
     * Reads a numeric field, or {@code null} when it is absent, JSON null, or
     * not a number. Open-Meteo omits fields it has no model data for, so a
     * missing key is routine rather than exceptional.
     */
    private static Double optDouble(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        try {
            return element.getAsDouble();
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
