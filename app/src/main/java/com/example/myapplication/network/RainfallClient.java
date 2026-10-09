package com.example.myapplication.network;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.example.myapplication.model.WaterQuality;
import com.google.gson.JsonArray;
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
 * Recent rainfall at a beach — the input behind every runoff advisory in the
 * app.
 *
 * <p>This is the closest thing to a live water-quality feed that exists for
 * free at national scale. The counties that actually culture beach water post
 * results days after sampling, by which time the sea has changed, and most
 * publish them as a web page rather than an API. Rainfall is available
 * everywhere, immediately, and is what the public-health rule is written
 * against anyway: stay out of the water for about three days after it rains,
 * because that is how long the storm drains keep discharging.
 *
 * <p>{@link WaterQuality} turns these millimetres into the advisory. This class
 * only fetches them.
 */
public class RainfallClient {

    /** Points per request, matching the other Open-Meteo clients. */
    public static final int BATCH_SIZE = 25;

    /**
     * Days of history asked for. One more than the advisory window so that
     * "it rained three days ago" can be distinguished from "the record starts
     * three days ago", and so a timezone rolling over cannot drop the day the
     * advisory hangs on.
     */
    private static final int PAST_DAYS = WaterQuality.RAIN_ADVISORY_DAYS + 2;

    private static final String ENDPOINT = "https://api.open-meteo.com/v1/forecast?";
    private static final String FIELDS =
        "&daily=precipitation_sum&forecast_days=1&timezone=auto&past_days=" + PAST_DAYS;

    public interface Callback {
        /** Millimetres per day, oldest first, ending with today. Empty on failure. */
        void onResult(double[] dailyRainMm);
    }

    public interface BatchCallback {
        void onPoint(int index, double[] dailyRainMm);
        void onComplete();
    }

    public void getRecentRain(double lat, double lon, Callback callback) {
        Http.IO.execute(() -> {
            double[] rain = new double[0];
            try {
                List<double[]> series = fetchSeries(ENDPOINT + String.format(Locale.US,
                    "latitude=%.4f&longitude=%.4f", lat, lon) + FIELDS, 1);
                if (!series.isEmpty()) rain = series.get(0);
            } catch (Exception e) {
                // An empty series reads as "no rain on record", which
                // WaterQuality treats as no advisory. That is the right way to
                // fail: the app already warns about the water in other ways,
                // and inventing an advisory from a network error would train
                // people to ignore the real ones.
            }
            callback.onResult(rain);
        });
    }

    public void getRecentRain(double[] latitudes, double[] longitudes, BatchCallback callback) {
        int count = Math.min(latitudes.length, longitudes.length);
        Http.IO.execute(() -> {
            List<double[]> series = Collections.emptyList();
            try {
                series = fetchSeries(ENDPOINT
                    + "latitude=" + join(latitudes, count)
                    + "&longitude=" + join(longitudes, count) + FIELDS, count);
            } catch (Exception e) {
                // Every point reports an empty series below.
            }
            for (int i = 0; i < count; i++) {
                callback.onPoint(i, i < series.size() ? series.get(i) : new double[0]);
            }
            callback.onComplete();
        });
    }

    private List<double[]> fetchSeries(String url, int expected) throws IOException {
        Request request = new Request.Builder().url(url).build();
        try (Response response = Http.openMeteo(request)) {
            if (!response.isSuccessful() || response.body() == null) {
                return Collections.emptyList();
            }
            return readSeries(response.body().string(), expected);
        }
    }

    /**
     * Pulls {@code daily.precipitation_sum} out of a response, one array per
     * point asked for.
     *
     * <p>A null inside the array becomes 0. Open-Meteo writes null for a day it
     * has no figure for, and treating that as "no rain" is the safe direction
     * here only because the alternative — skipping the day — would silently
     * shorten the advisory window.
     */
    @VisibleForTesting
    static List<double[]> readSeries(String body, int expected) {
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

        List<double[]> series = new ArrayList<>(expected);
        for (int i = 0; i < expected; i++) {
            JsonElement entry = i < entries.size() ? entries.get(i) : null;
            series.add(readOne(entry));
        }
        return series;
    }

    private static double[] readOne(@Nullable JsonElement entry) {
        if (entry == null || !entry.isJsonObject()) return new double[0];
        JsonElement daily = entry.getAsJsonObject().get("daily");
        if (daily == null || !daily.isJsonObject()) return new double[0];
        JsonElement sums = daily.getAsJsonObject().get("precipitation_sum");
        if (sums == null || !sums.isJsonArray()) return new double[0];

        JsonArray array = sums.getAsJsonArray();
        double[] rain = new double[array.size()];
        for (int i = 0; i < array.size(); i++) {
            JsonElement value = array.get(i);
            if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
                rain[i] = 0;
                continue;
            }
            try {
                rain[i] = value.getAsDouble();
            } catch (NumberFormatException e) {
                rain[i] = 0;
            }
        }
        return rain;
    }

    private static String join(double[] values, int count) {
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) joined.append(',');
            joined.append(String.format(Locale.US, "%.4f", values[i]));
        }
        return joined.toString();
    }
}
