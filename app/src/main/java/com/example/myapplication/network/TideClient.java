package com.example.myapplication.network;

import com.example.myapplication.model.TideForecast;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Fetches the sea-level series a beach's high and low tides are read from. */
public class TideClient {
    private static final double METERS_TO_FEET = 3.28084;
    /** Two days always covers the next of each turn, whatever the hour. */
    private static final int FORECAST_DAYS = 2;

    private final OkHttpClient client = new OkHttpClient();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface TideCallback {
        void onSuccess(TideForecast forecast);
        void onFailure(Exception e);
    }

    public void getTideForecast(double lat, double lon, TideCallback callback) {
        executor.execute(() -> {
            String url = String.format(Locale.US,
                "https://marine-api.open-meteo.com/v1/marine?latitude=%.4f&longitude=%.4f"
                    + "&hourly=sea_level_height_msl&timezone=auto&forecast_days=%d",
                lat, lon, FORECAST_DAYS);

            try (Response response = client.newCall(new Request.Builder().url(url).build()).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onFailure(new IOException("Tide request failed: " + response.code()));
                    return;
                }
                JsonObject json = JsonParser.parseString(response.body().string()).getAsJsonObject();
                callback.onSuccess(parse(json));
            } catch (IOException | RuntimeException e) {
                callback.onFailure(e);
            }
        });
    }

    /** Turns one marine-forecast body into the tides it implies. */
    static TideForecast parse(JsonObject json) throws IOException {
        JsonObject hourly = json.getAsJsonObject("hourly");
        JsonArray times = hourly.getAsJsonArray("time");
        JsonArray heights = hourly.getAsJsonArray("sea_level_height_msl");
        int count = Math.min(times.size(), heights.size());

        // Timestamps come back on the beach's own clock, with its offset
        // alongside; shifting by that offset gives the absolute instant.
        long offsetMillis = json.get("utc_offset_seconds").getAsLong() * 1000L;
        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
        parser.setTimeZone(TimeZone.getTimeZone("UTC"));

        long[] parsedTimes = new long[count];
        double[] parsedHeights = new double[count];
        int kept = 0;

        try {
            for (int i = 0; i < count; i++) {
                JsonElement height = heights.get(i);
                // A gap would throw the even spacing the fit relies on, so stop
                // at the first one and work with the run before it.
                if (height.isJsonNull()) break;
                parsedTimes[i] = parser.parse(times.get(i).getAsString()).getTime() - offsetMillis;
                parsedHeights[i] = height.getAsDouble() * METERS_TO_FEET;
                kept++;
            }
        } catch (ParseException e) {
            throw new IOException("Unreadable tide timestamp", e);
        }

        long[] finalTimes = new long[kept];
        double[] finalHeights = new double[kept];
        System.arraycopy(parsedTimes, 0, finalTimes, 0, kept);
        System.arraycopy(parsedHeights, 0, finalHeights, 0, kept);

        return TideForecast.fromHourlySeries(finalTimes, finalHeights, zoneOf(json, offsetMillis));
    }

    /**
     * The named zone if the platform knows it, else a fixed-offset stand-in -
     * right for the next two days barring a daylight-saving change.
     */
    private static TimeZone zoneOf(JsonObject json, long offsetMillis) {
        if (json.has("timezone") && !json.get("timezone").isJsonNull()) {
            String name = json.get("timezone").getAsString();
            TimeZone zone = TimeZone.getTimeZone(name);
            if (zone.getID().equals(name)) return zone;
        }
        return new SimpleTimeZone((int) offsetMillis, "Beach");
    }
}
