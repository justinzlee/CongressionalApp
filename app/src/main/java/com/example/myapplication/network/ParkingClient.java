package com.example.myapplication.network;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.example.myapplication.SettingsActivity;
import com.example.myapplication.model.ParkingSummary;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * What parking exists around a beach, from OpenStreetMap via the Overpass API.
 *
 * <p>Chosen over Google's Places API for one reason that decides it: Places
 * needs a billing account and an API key, and an Android app has nowhere safe
 * to keep a key — anything shipped in the APK can be pulled out of it and spent
 * by somebody else. Overpass needs neither, and OpenStreetMap's parking tagging
 * carries the two things actually being asked about here: whether a car park is
 * public, and whether it charges.
 *
 * <p>What neither service would give is live occupancy. No free feed anywhere
 * says whether the lot at La Jolla Shores is full right now, so this measures
 * supply and cost and {@link ParkingSummary} is careful to describe itself that
 * way rather than promising a space.
 *
 * <p>Results are cached for {@link #CACHE_DAYS} days. Overpass is a free
 * volunteer-run service with published rate limits, car parks do not move, and
 * the alternative — a request per beach per screen — would be both slow for the
 * user and rude to them.
 */
public class ParkingClient {

    /** How long a beach's parking answer is reused before asking again. */
    @VisibleForTesting static final int CACHE_DAYS = 30;

    private static final String KEY_PREFIX = "parking_";
    private static final String KEY_AT_SUFFIX = "_at";

    /** What every query starts with, one beach or several. */
    private static final String HEADER = "[out:json][timeout:25];";

    public interface Callback {
        void onResult(@Nullable ParkingSummary parking);
    }

    /** Reports a batched lookup one beach at a time, then once when it is done. */
    public interface BatchCallback {
        /**
         * @param index   the beach's position in the list that was passed in
         * @param parking its parking, or null where it could not be found out
         */
        void onPoint(int index, @Nullable ParkingSummary parking);

        /** Called once, after every beach has been reported. */
        void onComplete();
    }

    private final SharedPreferences prefs;

    public ParkingClient(Context context) {
        this.prefs = context.getApplicationContext()
            .getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * The parking around this beach, from cache if it is still fresh.
     *
     * <p>The callback runs on a background thread — every caller here is
     * already hopping to the main thread to touch views.
     */
    public void getParking(String beachId, double lat, double lon, Callback callback) {
        ParkingSummary cached = readCache(beachId);
        if (cached != null) {
            callback.onResult(cached);
            return;
        }

        Http.IO.execute(() -> {
            ParkingSummary parking = null;
            try {
                String body = Overpass.fetch(query(lat, lon));
                if (body != null) {
                    parking = parse(body);
                    if (parking != null) writeCache(beachId, parking);
                }
            } catch (Exception e) {
                // Overpass is a shared free service and does time out under
                // load. Parking is optional to every score that reads it, so a
                // failure leaves it out of the blend rather than sinking the
                // screen.
            }
            callback.onResult(parking);
        });
    }

    /**
     * The parking around several beaches at once, for the ranking screen:
     * whatever the cache holds, plus one Overpass query covering every beach
     * it does not.
     *
     * <p>The ranking has to score a beach on the parking its own screen shows,
     * or the two disagree — which is what happened while it only read the
     * cache, and a beach nobody had opened was ranked with no parking at all.
     * One query rather than one per beach, because a dozen queries to a free
     * volunteer-run service is slow for the user and rude to the service. It
     * asks exactly what {@link #query} asks, beach after beach, with a marker
     * before each, so every beach's share of the answer is the answer it would
     * have had alone.
     *
     * <p>Cached beaches are reported straight away on the calling thread; the
     * rest, and the completion, on a background thread.
     */
    public void getParking(List<String> beachIds, double[] lats, double[] lons,
                           BatchCallback callback) {
        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < beachIds.size(); i++) {
            ParkingSummary cached = readCache(beachIds.get(i));
            if (cached != null) {
                callback.onPoint(i, cached);
            } else {
                missing.add(i);
            }
        }
        if (missing.isEmpty()) {
            callback.onComplete();
            return;
        }

        Http.IO.execute(() -> {
            Map<String, ParkingSummary> found = Collections.emptyMap();
            try {
                StringBuilder query = new StringBuilder(HEADER);
                for (int i : missing) {
                    query.append(marker(beachIds.get(i))).append(around(lats[i], lons[i]));
                }
                String body = Overpass.fetch(query.toString());
                if (body != null) found = parseBatch(body);
            } catch (Exception e) {
                // As for a single beach: parking is optional to every score,
                // so a failure leaves it out rather than holding up the list.
            }
            for (int i : missing) {
                ParkingSummary parking = found.get(beachIds.get(i));
                if (parking != null) writeCache(beachIds.get(i), parking);
                callback.onPoint(i, parking);
            }
            callback.onComplete();
        });
    }

    @VisibleForTesting
    static String query(double lat, double lon) {
        return HEADER + around(lat, lon);
    }

    /**
     * Asks for public parking around the point: car parks as nodes and as
     * areas, plus street-side parking, which is what actually fills up first at
     * a beach and is tagged separately.
     */
    private static String around(double lat, double lon) {
        return String.format(Locale.US,
            "(node[\"amenity\"=\"parking\"](around:%d,%.5f,%.5f);"
                + "way[\"amenity\"=\"parking\"](around:%d,%.5f,%.5f);"
                + ");out tags center 80;",
            ParkingSummary.SEARCH_RADIUS_M, lat, lon,
            ParkingSummary.SEARCH_RADIUS_M, lat, lon);
    }

    /** A made-up element announcing whose parking follows, in a batched query. */
    private static String marker(String beachId) {
        return "make marker beach=\"" + beachId + "\";out;";
    }

    @VisibleForTesting
    @Nullable
    static ParkingSummary parse(String body) {
        JsonArray elements = elementsOf(rootOf(body));
        return elements == null ? null : summarize(elements);
    }

    /**
     * Splits a batched answer at its markers into one summary per beach id.
     *
     * <p>A beach whose marker is missing is left out rather than reported as
     * having no parking, and so is every beach when Overpass says it ran out of
     * time or memory part way: an answer cut short would count too few car
     * parks, and be cached for a month.
     */
    @VisibleForTesting
    static Map<String, ParkingSummary> parseBatch(String body) {
        Map<String, ParkingSummary> found = new HashMap<>();
        JsonObject root = rootOf(body);
        JsonArray elements = elementsOf(root);
        if (elements == null || isCutShort(root)) return found;

        String beach = null;
        JsonArray share = null;
        for (JsonElement element : elements) {
            String marked = markedBeach(element);
            if (marked != null) {
                if (beach != null) found.put(beach, summarize(share));
                beach = marked;
                share = new JsonArray();
            } else if (share != null) {
                share.add(element);
            }
        }
        if (beach != null) found.put(beach, summarize(share));
        return found;
    }

    @Nullable
    private static JsonObject rootOf(String body) {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(body);
        } catch (RuntimeException e) {
            return null;
        }
        return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
    }

    @Nullable
    private static JsonArray elementsOf(@Nullable JsonObject root) {
        if (root == null) return null;
        JsonElement elements = root.get("elements");
        return elements != null && elements.isJsonArray() ? elements.getAsJsonArray() : null;
    }

    /** Overpass reports a query it had to abandon in a "remark", not a status code. */
    private static boolean isCutShort(JsonObject root) {
        JsonElement remark = root.get("remark");
        return remark != null && remark.isJsonPrimitive()
            && remark.getAsString().contains("runtime error");
    }

    /** The beach id a marker element announces, or null for anything else. */
    @Nullable
    private static String markedBeach(JsonElement element) {
        if (element == null || !element.isJsonObject()) return null;
        JsonObject object = element.getAsJsonObject();
        JsonElement type = object.get("type");
        if (type == null || !type.isJsonPrimitive() || !"marker".equals(type.getAsString())) {
            return null;
        }
        JsonElement tags = object.get("tags");
        return tags != null && tags.isJsonObject() ? string(tags.getAsJsonObject(), "beach") : null;
    }

    /**
     * Counts the features into a summary.
     *
     * <p>Private and customer-only car parks are skipped: a hotel's own lot is
     * not parking a beachgoer can use, and counting it would flatter a beach
     * that has nowhere to leave a car. Anything with no access tag is assumed
     * usable, which is how OpenStreetMap's own consumers read an absent
     * {@code access} — most public lots simply never get tagged.
     */
    private static ParkingSummary summarize(JsonArray elements) {
        int lots = 0, free = 0, paid = 0, capacity = 0, street = 0;

        for (JsonElement element : elements) {
            if (element == null || !element.isJsonObject()) continue;
            JsonElement tagsElement = element.getAsJsonObject().get("tags");
            JsonObject tags = tagsElement != null && tagsElement.isJsonObject()
                ? tagsElement.getAsJsonObject() : new JsonObject();

            String access = string(tags, "access");
            if ("private".equals(access) || "customers".equals(access)
                || "permit".equals(access) || "no".equals(access)) {
                continue;
            }

            if ("street_side".equals(string(tags, "parking"))
                || "lane".equals(string(tags, "parking"))) {
                street++;
            } else {
                lots++;
            }

            String fee = string(tags, "fee");
            if ("no".equals(fee) || "free".equals(fee)) {
                free++;
            } else if (fee != null && !"unknown".equals(fee)) {
                // "yes", or an interval string like "Mo-Fr 08:00-18:00".
                paid++;
            }

            Integer spaces = integer(tags, "capacity");
            if (spaces != null && spaces > 0) capacity += spaces;
        }

        return new ParkingSummary(lots, free, paid, capacity, street);
    }

    @Nullable
    private static String string(JsonObject tags, String key) {
        JsonElement value = tags.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    @Nullable
    private static Integer integer(JsonObject tags, String key) {
        String raw = string(tags, key);
        if (raw == null) return null;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            // Capacity is free text often enough ("approx 50") that this is
            // routine rather than exceptional.
            return null;
        }
    }

    // ── Cache ────────────────────────────────────────────────────────────
    //
    // Five integers per beach in the preferences file the rest of the app
    // already uses. A database for something this small, that changes this
    // rarely, would be more moving parts than the thing it stores.

    @Nullable
    private ParkingSummary readCache(String beachId) {
        long at = prefs.getLong(KEY_PREFIX + beachId + KEY_AT_SUFFIX, 0);
        if (at == 0 || System.currentTimeMillis() - at > TimeUnit.DAYS.toMillis(CACHE_DAYS)) {
            return null;
        }
        String stored = prefs.getString(KEY_PREFIX + beachId, null);
        if (stored == null) return null;
        String[] parts = stored.split(",");
        if (parts.length != 5) return null;
        try {
            return new ParkingSummary(
                Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]), Integer.parseInt(parts[3]),
                Integer.parseInt(parts[4]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void writeCache(String beachId, ParkingSummary parking) {
        prefs.edit()
            .putString(KEY_PREFIX + beachId, String.format(Locale.US, "%d,%d,%d,%d,%d",
                parking.getLots(), parking.getFreeLots(), parking.getPaidLots(),
                parking.getKnownCapacity(), parking.getStreetSide()))
            .putLong(KEY_PREFIX + beachId + KEY_AT_SUFFIX, System.currentTimeMillis())
            .apply();
    }
}
