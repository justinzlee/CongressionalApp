package com.example.myapplication.network;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.example.myapplication.model.AirQuality;
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

/**
 * Air quality over a beach, from Open-Meteo's air-quality service.
 *
 * <p>Same provider as the forecast the app already reads, and free without a
 * key for the same reason — which is why this is one more call rather than a
 * new account and a secret to keep out of the repository. It answers a list of
 * coordinates the same way the forecast does, so the ranking screen costs a
 * handful of requests rather than one per beach.
 *
 * <p>The US AQI is asked for directly rather than computed here from the raw
 * pollutants. The EPA's breakpoint arithmetic is fiddly and gets revised, and
 * getting it subtly wrong would put a beach in the wrong band on exactly the
 * smoky days the number matters.
 */
public class AirQualityClient {

    /** Points per request, matching the forecast client's batching. */
    public static final int BATCH_SIZE = 25;

    private static final String ENDPOINT = "https://air-quality-api.open-meteo.com/v1/air-quality?";
    private static final String FIELDS = "&current=us_aqi,pm2_5,pm10,ozone";

    public interface Callback {
        void onResult(@Nullable AirQuality air);
    }

    /** Reports a batched fetch one point at a time, in request order. */
    public interface BatchCallback {
        void onPoint(int index, @Nullable AirQuality air);
        void onComplete();
    }

    public void getAirQuality(double lat, double lon, Callback callback) {
        Http.IO.execute(() -> {
            AirQuality air = null;
            try {
                String url = ENDPOINT + String.format(Locale.US,
                    "latitude=%.4f&longitude=%.4f", lat, lon) + FIELDS;
                List<JsonObject> currents = fetchCurrents(url, 1);
                if (!currents.isEmpty()) {
                    air = parse(currents.get(0));
                }
            } catch (Exception e) {
                // Broad for the same reason as WeatherClient: an escaped
                // unchecked exception would fire no callback at all, and the
                // caller would wait on a reading that is never coming.
            }
            callback.onResult(air);
        });
    }

    public void getAirQuality(double[] latitudes, double[] longitudes, BatchCallback callback) {
        int count = Math.min(latitudes.length, longitudes.length);
        Http.IO.execute(() -> {
            List<JsonObject> currents = Collections.emptyList();
            try {
                currents = fetchCurrents(ENDPOINT
                    + "latitude=" + join(latitudes, count)
                    + "&longitude=" + join(longitudes, count) + FIELDS, count);
            } catch (Exception e) {
                // Every point reports null below; air quality is optional to
                // every score that reads it.
            }
            for (int i = 0; i < count; i++) {
                callback.onPoint(i, i < currents.size() ? parse(currents.get(i)) : null);
            }
            callback.onComplete();
        });
    }

    /**
     * Reads one point's payload. Null unless an AQI was genuinely present — a
     * fabricated zero would read as pristine air, which is the one wrong answer
     * that would actively mislead somebody with asthma.
     */
    @VisibleForTesting
    @Nullable
    static AirQuality parse(@Nullable JsonObject current) {
        if (current == null) return null;
        Double aqi = optDouble(current, "us_aqi");
        if (aqi == null) return null;
        return new AirQuality(
            (int) Math.round(aqi),
            optDouble(current, "pm2_5"),
            optDouble(current, "pm10"),
            optDouble(current, "ozone"));
    }

    private static String join(double[] values, int count) {
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) joined.append(',');
            joined.append(String.format(Locale.US, "%.4f", values[i]));
        }
        return joined.toString();
    }

    private List<JsonObject> fetchCurrents(String url, int expected) throws IOException {
        Request request = new Request.Builder().url(url).build();
        try (Response response = Http.CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return Collections.emptyList();
            }
            return readCurrents(response.body().string(), expected);
        }
    }

    /** Open-Meteo answers one coordinate with an object and several with an array. */
    @VisibleForTesting
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

    @Nullable
    private static Double optDouble(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) return null;
        try {
            return element.getAsDouble();
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
