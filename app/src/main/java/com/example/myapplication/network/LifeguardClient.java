package com.example.myapplication.network;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.example.myapplication.SettingsActivity;
import com.example.myapplication.model.HelpLocation;
import com.example.myapplication.model.HelpType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Where the lifeguard towers stand around a beach, from OpenStreetMap via the
 * Overpass API, for the beach map.
 *
 * <p>The app's own records name one lifeguard station each for two beaches.
 * OpenStreetMap has the towers themselves along nearly every patrolled beach in
 * the list, most of them carrying the number painted on their side: twenty-six
 * along Huntington, seventeen on South Beach. A map titled for lifeguard towers
 * is only worth opening with those on it.
 *
 * <p>Same service and the same reasoning as {@link ParkingClient}: free and
 * keyless, so there is nothing to hide in the APK, and cached for
 * {@link #CACHE_DAYS} days because a tower moves about as often as a car park.
 * A beach with nothing mapped is cached too, as an empty list, so it is not
 * asked about again every time it is opened.
 */
public class LifeguardClient {

    /** How far along the sand to look, either way from the beach's pin. */
    @VisibleForTesting static final int SEARCH_RADIUS_M = 2500;

    /** How long a beach's towers are reused before asking again. */
    @VisibleForTesting static final int CACHE_DAYS = 30;

    private static final String KEY_PREFIX = "lifeguards_";
    private static final String KEY_AT_SUFFIX = "_at";

    public interface Callback {
        /** @param towers what is mapped, possibly nothing; null when Overpass could not be asked */
        void onResult(@Nullable List<HelpLocation> towers);
    }

    private final SharedPreferences prefs;

    public LifeguardClient(Context context) {
        this.prefs = context.getApplicationContext()
            .getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * The towers around this beach, from cache if it is still fresh.
     *
     * <p>The callback runs on a background thread when Overpass is asked, and
     * straight away on the caller's when the cache answers.
     */
    public void getTowers(String beachId, double lat, double lon, Callback callback) {
        List<HelpLocation> cached = readCache(beachId);
        if (cached != null) {
            callback.onResult(cached);
            return;
        }

        Http.IO.execute(() -> {
            List<HelpLocation> towers = null;
            String body = Overpass.fetch(query(lat, lon));
            if (body != null) {
                towers = parse(body);
                if (towers != null) writeCache(beachId, towers);
            }
            // Null when no instance answered. The map still has the beach, you,
            // and whatever towers the app's own records hold.
            callback.onResult(towers);
        });
    }

    /**
     * Anything tagged as a lifeguard post: {@code emergency=lifeguard}, as
     * OpenStreetMap tags one now, and the older {@code lifeguard_tower},
     * {@code lifeguard_base} and {@code lifeguard_platform} values that plenty
     * of towers were mapped with and still carry — Cannon Beach's only tower is
     * one of those.
     */
    @VisibleForTesting
    static String query(double lat, double lon) {
        return String.format(Locale.US,
            "[out:json][timeout:25];("
                + "node[\"emergency\"~\"^lifeguard\"](around:%d,%.5f,%.5f);"
                + "way[\"emergency\"~\"^lifeguard\"](around:%d,%.5f,%.5f);"
                + ");out tags center 80;",
            SEARCH_RADIUS_M, lat, lon,
            SEARCH_RADIUS_M, lat, lon);
    }

    /**
     * Reads the towers out of an Overpass body. A tower drawn as a building
     * arrives as a way, with Overpass having worked out its centre; one drawn
     * as a point arrives with its own coordinates. Each keeps its reference —
     * the tower's number — or failing that its name, for the marker to label
     * itself with.
     */
    @VisibleForTesting
    @Nullable
    static List<HelpLocation> parse(String body) {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(body);
        } catch (RuntimeException e) {
            return null;
        }
        if (parsed == null || !parsed.isJsonObject()) return null;
        JsonObject root = parsed.getAsJsonObject();

        // A query Overpass gave up on still comes back as a success, with a
        // remark saying so and whatever had been found by then. Taken at its
        // word, that would file a patrolled beach as towerless for a month.
        String remark = string(root, "remark");
        if (remark != null && remark.contains("error")) return null;

        JsonElement elements = root.get("elements");
        if (elements == null || !elements.isJsonArray()) return null;

        List<HelpLocation> towers = new ArrayList<>();
        for (JsonElement element : elements.getAsJsonArray()) {
            if (element == null || !element.isJsonObject()) continue;
            JsonObject feature = element.getAsJsonObject();

            JsonElement centre = feature.get("center");
            JsonObject where = centre != null && centre.isJsonObject()
                ? centre.getAsJsonObject() : feature;
            Double lat = number(where, "lat");
            Double lon = number(where, "lon");
            if (lat == null || lon == null) continue;

            JsonElement tagsElement = feature.get("tags");
            JsonObject tags = tagsElement != null && tagsElement.isJsonObject()
                ? tagsElement.getAsJsonObject() : new JsonObject();
            String label = string(tags, "ref");
            if (label == null) label = string(tags, "name");

            towers.add(new HelpLocation(label == null ? "" : label,
                HelpType.LIFEGUARD_TOWER, lat, lon));
        }
        return towers;
    }

    @Nullable
    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()) return null;
        String text = value.getAsString().trim();
        return text.isEmpty() ? null : text;
    }

    @Nullable
    private static Double number(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()
            || !value.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        return value.getAsDouble();
    }

    // ── Cache ────────────────────────────────────────────────────────────
    //
    // A small JSON array per beach — [lat, lon, label] for each tower — in the
    // preferences file the rest of the app already uses. JSON rather than the
    // comma-joined numbers parking gets away with, because a label is free
    // text mapped by volunteers and can hold any character at all.

    @Nullable
    private List<HelpLocation> readCache(String beachId) {
        long at = prefs.getLong(KEY_PREFIX + beachId + KEY_AT_SUFFIX, 0);
        if (at == 0 || System.currentTimeMillis() - at > TimeUnit.DAYS.toMillis(CACHE_DAYS)) {
            return null;
        }
        String stored = prefs.getString(KEY_PREFIX + beachId, null);
        if (stored == null) return null;
        try {
            List<HelpLocation> towers = new ArrayList<>();
            for (JsonElement entry : JsonParser.parseString(stored).getAsJsonArray()) {
                JsonArray tower = entry.getAsJsonArray();
                towers.add(new HelpLocation(tower.get(2).getAsString(),
                    HelpType.LIFEGUARD_TOWER,
                    tower.get(0).getAsDouble(), tower.get(1).getAsDouble()));
            }
            return towers;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void writeCache(String beachId, List<HelpLocation> towers) {
        JsonArray stored = new JsonArray();
        for (HelpLocation tower : towers) {
            JsonArray entry = new JsonArray();
            entry.add(tower.getLatitude());
            entry.add(tower.getLongitude());
            entry.add(tower.getName());
            stored.add(entry);
        }
        prefs.edit()
            .putString(KEY_PREFIX + beachId, stored.toString())
            .putLong(KEY_PREFIX + beachId + KEY_AT_SUFFIX, System.currentTimeMillis())
            .apply();
    }
}
