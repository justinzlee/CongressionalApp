package com.example.myapplication.network;

import androidx.annotation.VisibleForTesting;

import com.example.myapplication.model.Tide;
import com.example.myapplication.model.TideForecast;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Fetches a beach's high and low tides from NOAA's tide predictions — the
 * figures every US tide table prints and the lifeguards work from.
 *
 * <p>The turns used to be picked out of Open-Meteo's modelled sea level.
 * Checked against NOAA at sixteen beaches, that model had every turn early: by
 * half an hour at La Jolla and over an hour at Ocean Beach in San Francisco,
 * and in Puget Sound it gave no sea level at all. A low tide shown forty
 * minutes before the real one is how someone gets cut off on a sandbar, so the
 * published predictions are what is read now.
 */
public class TideClient {

    private static final HttpUrl NOAA = HttpUrl.get("https://api.tidesandcurrents.noaa.gov/");

    /**
     * Two days always covers the next of each turn, whatever the hour: even
     * where the tide turns only once a day, the next one is under 25 hours off.
     */
    private static final int RANGE_HOURS = 48;

    /** A station's clock never changes, so it is only asked for once. */
    private static final Map<String, TimeZone> ZONES = new ConcurrentHashMap<>();

    public interface TideCallback {
        void onSuccess(TideForecast forecast);
        void onFailure(Exception e);
    }

    /** @param stationId the NOAA station the beach's tides are read from */
    public void getTideForecast(String stationId, TideCallback callback) {
        Http.IO.execute(() -> {
            try {
                TimeZone zone = zoneOf(stationId);
                HttpUrl url = NOAA.newBuilder()
                    .addPathSegments("api/prod/datagetter")
                    .addQueryParameter("product", "predictions")
                    .addQueryParameter("interval", "hilo")
                    .addQueryParameter("datum", "MLLW")
                    .addQueryParameter("units", "english")
                    .addQueryParameter("time_zone", "gmt")
                    .addQueryParameter("format", "json")
                    .addQueryParameter("application", "Flagline")
                    .addQueryParameter("begin_date", beginDate(System.currentTimeMillis()))
                    .addQueryParameter("range", String.valueOf(RANGE_HOURS))
                    .addQueryParameter("station", stationId)
                    .build();
                callback.onSuccess(parse(fetch(url), zone));
            } catch (IOException | RuntimeException e) {
                callback.onFailure(e);
            }
        });
    }

    /**
     * Reads one predictions body into the turns it lists. NOAA answers a bad
     * request with an error object and no predictions, which is a failure to
     * report rather than a day on which the tide never turns.
     */
    @VisibleForTesting
    static TideForecast parse(JsonObject json, TimeZone zone) throws IOException {
        JsonElement error = json.get("error");
        if (error != null && error.isJsonObject()) {
            JsonElement message = error.getAsJsonObject().get("message");
            throw new IOException(message == null
                ? "NOAA returned an error" : message.getAsString().trim());
        }
        JsonArray predictions = json.getAsJsonArray("predictions");
        if (predictions == null) {
            throw new IOException("NOAA returned no predictions");
        }

        // Asked for in GMT, so each time is an absolute instant as written.
        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        parser.setTimeZone(TimeZone.getTimeZone("UTC"));

        List<Tide> tides = new ArrayList<>(predictions.size());
        try {
            for (JsonElement element : predictions) {
                JsonObject prediction = element.getAsJsonObject();
                // "H" or "L". Read by the first letter so that "HH" and "LL",
                // NOAA's marks for the higher and lower of a day's pair, count too.
                Tide.Type type = prediction.get("type").getAsString().startsWith("H")
                    ? Tide.Type.HIGH : Tide.Type.LOW;
                long time = parser.parse(prediction.get("t").getAsString()).getTime();
                double height = Double.parseDouble(prediction.get("v").getAsString());
                tides.add(new Tide(type, time, height));
            }
        } catch (ParseException e) {
            throw new IOException("Unreadable tide timestamp", e);
        }
        return new TideForecast(tides, zone);
    }

    /**
     * The clock the station's tide table is printed on. If it cannot be
     * fetched the phone's clock stands in — every time is still the right
     * moment, only told on the reader's clock rather than the beach's — and
     * nothing is remembered, so the next look tries again.
     */
    private static TimeZone zoneOf(String stationId) {
        TimeZone known = ZONES.get(stationId);
        if (known != null) return known;
        try {
            HttpUrl url = NOAA.newBuilder()
                .addPathSegments("mdapi/prod/webapi/stations")
                .addPathSegment(stationId + ".json")
                .build();
            JsonObject station = fetch(url).getAsJsonArray("stations").get(0).getAsJsonObject();
            TimeZone zone = zoneFor(station.get("timezonecorr").getAsInt());
            ZONES.put(stationId, zone);
            return zone;
        } catch (IOException | RuntimeException e) {
            return TimeZone.getDefault();
        }
    }

    /**
     * The zone for a station's standard offset from UTC, which is what NOAA
     * publishes. On the coasts this app covers each offset is one zone, and
     * naming it brings the daylight-saving rules a bare offset would get wrong
     * for half the year. Anything else keeps the plain offset.
     */
    @VisibleForTesting
    static TimeZone zoneFor(int utcOffsetHours) {
        switch (utcOffsetHours) {
            case -4: return TimeZone.getTimeZone("America/Puerto_Rico");
            case -5: return TimeZone.getTimeZone("America/New_York");
            case -6: return TimeZone.getTimeZone("America/Chicago");
            case -8: return TimeZone.getTimeZone("America/Los_Angeles");
            case -10: return TimeZone.getTimeZone("Pacific/Honolulu");
            default: return new SimpleTimeZone(utcOffsetHours * 3_600_000, "Station");
        }
    }

    /** NOAA's begin_date, written in GMT to match the times asked for. */
    @VisibleForTesting
    static String beginDate(long nowMillis) {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd HH:mm", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(nowMillis));
    }

    private static JsonObject fetch(HttpUrl url) throws IOException {
        Request request = new Request.Builder().url(url).build();
        try (Response response = Http.CLIENT.newCall(request).execute()) {
            if (response.body() == null) {
                throw new IOException("Empty response from " + url.host());
            }
            // A bad station comes back as a 400 with an error object in the
            // body; parse() turns that into a failure that says why.
            JsonElement parsed = JsonParser.parseString(response.body().string());
            if (!parsed.isJsonObject()) {
                throw new IOException("Tide request failed: " + response.code());
            }
            return parsed.getAsJsonObject();
        }
    }
}
