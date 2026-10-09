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

    private static final String MARINE = "https://marine-api.open-meteo.com/v1/marine?";

    /**
     * The wave model asked about any point the default one leaves blank.
     *
     * <p>Open-Meteo's default leans on a fine coastal grid, and at a few
     * beaches the cell nearest the sand is one that grid counts as land:
     * Daytona Beach, open Atlantic surf, came back with a forecast and no wave
     * height at all. ECMWF's coarser wave model still reaches those points.
     * GFS-Wave was the other candidate and cannot be used — it answers a point
     * it has no sea for with 0.00m rather than nothing, which reads as a flat
     * calm and clears every threshold in the verdict.
     */
    private static final String FALLBACK_WAVE_MODEL = "ecmwf_wam025";

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
     * Fetches conditions for many points in a single pair of requests, plus a
     * third for any point the default wave model has nothing for.
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

                // Marine coverage has gaps — the western tip of Lake Superior
                // has no wave model at all — so a missing marine answer leaves
                // the surf reading blank rather than sinking the batch. Points
                // the default model misses get one more try from another.
                marineCurrents = fetchCurrents(MARINE + points + "&current=wave_height,sea_surface_temperature", count);
                marineCurrents = withFallbackWaves(latitudes, longitudes, currents, marineCurrents, count);
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

    /**
     * Fills the points the default wave model left blank from
     * {@link #FALLBACK_WAVE_MODEL}, in one more request covering only those
     * points. Returns the marine payloads to use, one slot per point.
     */
    private List<JsonObject> withFallbackWaves(double[] latitudes, double[] longitudes,
            List<JsonObject> currents, List<JsonObject> marine, int count) throws IOException {
        List<Integer> missing = missingWaves(currents, marine, count);
        if (missing.isEmpty()) {
            return marine;
        }

        double[] missingLatitudes = new double[missing.size()];
        double[] missingLongitudes = new double[missing.size()];
        for (int j = 0; j < missing.size(); j++) {
            missingLatitudes[j] = latitudes[missing.get(j)];
            missingLongitudes[j] = longitudes[missing.get(j)];
        }
        List<JsonObject> fallback = fetchCurrents(MARINE
                + "latitude=" + join(missingLatitudes, missing.size())
                + "&longitude=" + join(missingLongitudes, missing.size())
                + "&current=wave_height,sea_surface_temperature&models=" + FALLBACK_WAVE_MODEL,
            missing.size());

        List<JsonObject> merged = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            merged.add(i < marine.size() ? marine.get(i) : null);
        }
        for (int j = 0; j < fallback.size(); j++) {
            if (hasWave(fallback.get(j))) {
                int point = missing.get(j);
                merged.set(point, withFallbackWave(merged.get(point), fallback.get(j)));
            }
        }
        return merged;
    }

    /**
     * The wave from the backup model, the water from the default one. The
     * backup has no sea temperature at all, so taking its payload whole threw
     * away Daytona's real reading of about 83°F. Package-private so the unit
     * tests can pin the merge.
     */
    static JsonObject withFallbackWave(JsonObject marine, JsonObject fallback) {
        JsonObject merged = new JsonObject();
        merged.add("wave_height", fallback.get("wave_height"));
        if (marine != null) {
            merged.add("sea_surface_temperature", marine.get("sea_surface_temperature"));
        }
        return merged;
    }

    /**
     * The points, among the first {@code count}, that have a forecast but no
     * wave reading — the ones worth asking a second wave model about. A point
     * with no forecast is left alone; it has nothing to show a wave beside.
     * Package-private so the unit tests can pin the selection.
     */
    static List<Integer> missingWaves(List<JsonObject> currents, List<JsonObject> marine, int count) {
        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            JsonObject current = i < currents.size() ? currents.get(i) : null;
            JsonObject wave = i < marine.size() ? marine.get(i) : null;
            if (current != null && !hasWave(wave)) {
                missing.add(i);
            }
        }
        return missing;
    }

    /** Whether a marine payload carries a wave height, a flat calm included. */
    static boolean hasWave(JsonObject marine) {
        return marine != null && optDouble(marine, "wave_height") != null;
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

                // Marine API: Wave Height (meters -> feet), Sea Surface Temp (Celsius -> Fahrenheit)
                String marineUrl = String.format(Locale.US,
                    MARINE + "latitude=%.4f&longitude=%.4f&current=wave_height,sea_surface_temperature",
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

                // Marine coverage has gaps — some coastal points have no wave
                // model at all. That is a missing reading, not an error, so the
                // rest of the conditions still go through with a null wave
                // height and the surf tile shows "--". A point the default
                // model misses is asked of the fallback one first.
                //
                // A marine call that fails outright - a timeout, a dropped
                // connection - is the same missing reading. Letting it reach
                // the catch below threw away the weather already in hand, so a
                // slow wave service blanked the whole beach page, where the
                // batched fetch for the list kept it.
                JsonObject marine = null;
                boolean marineAnswered = true;
                try {
                    marine = fetchCurrent(marineUrl);
                } catch (IOException e) {
                    marineAnswered = false;
                }
                // Not after a failed call: the fallback is the same host, and a
                // second timeout would only hold the weather back longer.
                if (marineAnswered && !hasWave(marine)) {
                    try {
                        JsonObject fallback = fetchCurrent(marineUrl + "&models=" + FALLBACK_WAVE_MODEL);
                        if (hasWave(fallback)) {
                            marine = withFallbackWave(marine, fallback);
                        }
                    } catch (IOException e) {
                        // Keep the blank reading; the weather still goes through.
                    }
                }
                callback.onSuccess(buildConditions(current, marine));

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
        Double waterTemp = null;
        if (marineCurrent != null) {
            Double waveMeters = optDouble(marineCurrent, "wave_height");
            if (waveMeters != null) {
                waveHeight = waveMeters * 3.28084;
            }
            Double sstC = optDouble(marineCurrent, "sea_surface_temperature");
            if (sstC != null) {
                waterTemp = (sstC * 9.0 / 5.0) + 32.0;
            }
        }
        // A missing sea temperature stays missing rather than being guessed
        // from the air: the marine model has none for the Great Lakes, and
        // "air minus 4" put Duluth's water 10°F colder than NOAA measured it.

        return new BeachConditions(temp, waterTemp, uv, wind, waveHeight, visibility);
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
        try (Response response = Http.openMeteo(request)) {
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
