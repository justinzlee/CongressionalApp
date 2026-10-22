package com.example.myapplication.network;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.VisibleForTesting;

import com.example.myapplication.SettingsActivity;
import com.example.myapplication.model.RockRating;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * What OpenStreetMap says a beach is made of — sand, pebbles, rock — as a
 * starting point for the rocks rating.
 *
 * <p>Mappers tag a drawn beach with {@code surface}, and where they have, it is
 * the only public record of what is underfoot. Plenty of beaches are drawn
 * without one (Ocean Beach in San Francisco, Boomer Beach beside La Jolla
 * Cove), and those simply have no starting point: an untagged beach is an
 * unknown beach, not a sandy one.
 *
 * <p>The nearest tagged beach within {@link #SEARCH_RADIUS_M} metres is used,
 * so the pin standing a few metres off the drawn outline still finds it and
 * the cove next door does not stand in for it. Cached for {@link #CACHE_DAYS}
 * days like the towers, untagged results included.
 */
public class ShoreSurfaceClient {

    @VisibleForTesting static final int SEARCH_RADIUS_M = 300;
    @VisibleForTesting static final int CACHE_DAYS = 30;

    private static final String KEY_PREFIX = "shore_surface_";
    private static final String KEY_AT_SUFFIX = "_at";

    public interface Callback {
        /** @param rating a rocks starting point, or {@link RockRating#UNRATED} */
        void onResult(int rating);
    }

    private final SharedPreferences prefs;

    public ShoreSurfaceClient(Context context) {
        this.prefs = context.getApplicationContext()
            .getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void getRating(String beachId, double lat, double lon, Callback callback) {
        if (isCached(beachId)) {
            callback.onResult(cachedRating(beachId));
            return;
        }
        Http.IO.execute(() -> {
            String body = Overpass.fetch(query(lat, lon));
            String surface = body == null ? null : nearestSurface(body, lat, lon);
            if (body != null && surface != null) writeCache(beachId, surface);
            callback.onResult(RockRating.forSurface(surface));
        });
    }

    /** The cached starting point without asking anyone, or {@link RockRating#UNRATED}. */
    public int cachedRating(String beachId) {
        if (!isCached(beachId)) return RockRating.UNRATED;
        return RockRating.forSurface(prefs.getString(KEY_PREFIX + beachId, ""));
    }

    private boolean isCached(String beachId) {
        long at = prefs.getLong(KEY_PREFIX + beachId + KEY_AT_SUFFIX, 0);
        return at != 0 && System.currentTimeMillis() - at <= TimeUnit.DAYS.toMillis(CACHE_DAYS);
    }

    @VisibleForTesting
    static String query(double lat, double lon) {
        return String.format(Locale.US,
            "[out:json][timeout:25];("
                + "way[\"natural\"=\"beach\"](around:%d,%.5f,%.5f);"
                + "relation[\"natural\"=\"beach\"](around:%d,%.5f,%.5f);"
                + ");out tags center;",
            SEARCH_RADIUS_M, lat, lon, SEARCH_RADIUS_M, lat, lon);
    }

    /**
     * The surface of the nearest drawn beach that has one: "" when beaches were
     * found but none is tagged, so the answer is cached as known-untagged, and
     * null when the body is not an Overpass answer or Overpass gave up partway.
     */
    @VisibleForTesting
    static String nearestSurface(String body, double lat, double lon) {
        JsonObject root;
        try {
            root = JsonParser.parseString(body).getAsJsonObject();
        } catch (RuntimeException e) {
            return null;
        }
        JsonElement remark = root.get("remark");
        if (remark != null && remark.getAsString().contains("error")) return null;
        JsonElement elements = root.get("elements");
        if (elements == null || !elements.isJsonArray()) return null;

        String best = "";
        double bestDistance = Double.MAX_VALUE;
        for (JsonElement element : elements.getAsJsonArray()) {
            try {
                JsonObject feature = element.getAsJsonObject();
                JsonObject tags = feature.getAsJsonObject("tags");
                if (tags == null || !tags.has("surface")) continue;
                JsonObject centre = feature.getAsJsonObject("center");
                double distance = centre == null ? Double.MAX_VALUE / 2
                    : squaredDistance(lat, lon,
                        centre.get("lat").getAsDouble(), centre.get("lon").getAsDouble());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = tags.get("surface").getAsString();
                }
            } catch (RuntimeException e) {
                // A malformed feature costs that feature.
            }
        }
        return best;
    }

    /** Good enough to compare features a few hundred metres apart. */
    private static double squaredDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = lat2 - lat1;
        double dLon = (lon2 - lon1) * Math.cos(Math.toRadians(lat1));
        return dLat * dLat + dLon * dLon;
    }

    private void writeCache(String beachId, String surface) {
        prefs.edit()
            .putString(KEY_PREFIX + beachId, surface)
            .putLong(KEY_PREFIX + beachId + KEY_AT_SUFFIX, System.currentTimeMillis())
            .apply();
    }
}
