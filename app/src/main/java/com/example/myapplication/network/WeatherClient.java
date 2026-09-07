package com.example.myapplication.network;

import com.example.myapplication.model.BeachConditions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import okhttp3.Request;
import okhttp3.Response;

public class WeatherClient {

    /**
     * How many beaches one batched request covers. Open-Meteo takes a
     * comma-separated list of coordinates and answers with one entry per
     * point, so a list of a hundred beaches costs a handful of calls rather
     * than a hundred. The chunk is kept modest so the URL stays short and the
     * list on screen fills in progressively instead of all at the end.
     */
    public static final int BATCH_SIZE = 25;

    public interface WeatherCallback {
        void onSuccess(BeachConditions conditions);
        void onFailure(Exception e);
    }

    /** Reports a batched fetch one point at a time, in request order. */
    public interface BatchCallback {
        /**
         * @param index      the point's position in the arrays that were passed in
         * @param conditions its readings, or {@code null} if no forecast came
         *                   back for that point
         */
        void onPoint(int index, BeachConditions conditions);

        /** Called once, after every point in the batch has been reported. */
        void onComplete();
    }

    /**
     * Fetches conditions for many points in a single pair of requests.
     *
     * <p>The callback hears about every index exactly once — with {@code null}
     * where that point has no forecast — and then {@code onComplete}, so a
     * caller can keep an accurate count of outstanding work even when the
     * whole batch fails.
     */
    public void getBeachConditions(double[] latitudes, double[] longitudes, BatchCallback callback) {
        int count = Math.min(latitudes.length, longitudes.length);
        Http.IO.execute(() -> {
            List<JsonObject> currents = Collections.emptyList();
            List<JsonObject> marineCurrents = Collections.emptyList();
            try {
                String points = "latitude=" + join(latitudes, count)
                    + "&longitude=" + join(longitudes, count);

                currents = fetchCurrents(
                    "https://api.open-meteo.com/v1/forecast?" + points
                        + "&current=temperature_2m,wind_speed_10m,visibility,uv_index"
                        + "&temperature_unit=fahrenheit&wind_speed_unit=mph",
                    count);

                // Marine coverage is patchy — the Great Lakes beaches and the
                // sheltered bays in this list have no wave model at all — so a
                // missing marine answer leaves the surf reading blank rather
                // than sinking the batch.
                marineCurrents = fetchCurrents(
                    "https://marine-api.open-meteo.com/v1/marine?" + points + "&current=wave_height",
                    count);
            } catch (Exception e) {
                // Deliberately broad, for the same reason as the single-point
                // fetch below: an escaped unchecked exception would fire no
                // callback at all, leaving every beach in the batch stuck on
                // its placeholder and the caller's count never settling.
            }

            for (int i = 0; i < count; i++) {
                JsonObject current = i < currents.size() ? currents.get(i) : null;
                JsonObject marine = i < marineCurrents.size() ? marineCurrents.get(i) : null;
                callback.onPoint(i, current == null ? null : buildConditions(current, marine));
            }
            callback.onComplete();
        });
    }

    /** Formats the first {@code count} coordinates as Open-Meteo's comma-separated list. */
    private static String join(double[] values, int count) {
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                joined.append(',');
            }
            joined.append(String.format(Locale.US, "%.4f", values[i]));
        }
        return joined.toString();
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
        List<JsonObject> currents = fetchCurrents(url, 1);
        return currents.isEmpty() ? null : currents.get(0);
    }

    /**
     * Runs a request and returns one {@code current} object per point asked
     * for, in request order, with {@code null} in any slot the response did not
     * cover. An empty list means the call itself failed.
     */
    private List<JsonObject> fetchCurrents(String url, int expected) throws IOException {
        Request request = new Request.Builder().url(url).build();
        try (Response response = Http.CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return Collections.emptyList();
            }
            return readCurrents(response.body().string(), expected);
        }
    }

    /**
     * Pulls the {@code current} objects out of a response body.
     *
     * <p>Open-Meteo answers a single coordinate with an object and several
     * coordinates with an array of them, so both shapes are unpacked here.
     * Package-private so the unit tests can pin that without the network.
     */
    static List<JsonObject> readCurrents(String body, int expected) {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(body);
        } catch (RuntimeException e) {
            return Collections.emptyList();
        }

        List<JsonElement> entries = new ArrayList<>();
        if (parsed != null && parsed.isJsonArray()) {
            parsed.getAsJsonArray().forEach(entries::add);
        } else if (parsed != null && parsed.isJsonObject()) {
            entries.add(parsed);
        } else {
            return Collections.emptyList();
        }

        List<JsonObject> currents = new ArrayList<>(expected);
        for (int i = 0; i < expected; i++) {
            JsonElement entry = i < entries.size() ? entries.get(i) : null;
            JsonObject current = null;
            if (entry != null && entry.isJsonObject()) {
                JsonElement value = entry.getAsJsonObject().get("current");
                if (value != null && value.isJsonObject()) {
                    current = value.getAsJsonObject();
                }
            }
            currents.add(current);
        }
        return currents;
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
