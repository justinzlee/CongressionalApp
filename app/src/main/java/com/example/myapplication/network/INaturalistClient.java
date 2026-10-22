package com.example.myapplication.network;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.example.myapplication.SettingsActivity;
import com.example.myapplication.model.SightingSpecies;
import com.example.myapplication.model.WildlifeTally;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import okhttp3.Request;
import okhttp3.Response;

/**
 * What wildlife people have recorded around a beach lately, from iNaturalist.
 *
 * <p>iNaturalist is a public record of photographed, community-identified
 * sightings with a free, keyless API. Each observation carries its taxon's
 * full ancestry, which is what lets one request be sorted into the same groups
 * the report form offers: anything under the gull subfamily is a seagull,
 * anything under the seal superfamily a seal, and so on down to "bird".
 * Observers can also mark an animal alive or dead, so dead seals are counted
 * from the same answer.
 *
 * <p>One request per beach, asking only for the two fields this needs, which
 * keeps a busy fortnight at La Jolla Cove to about 30 KB. Cached for
 * {@link #CACHE_HOURS} hours: the window is a fortnight, so a few hours' lag
 * changes nothing, and iNaturalist asks its API users to keep to about a
 * request a second across the whole app.
 */
public class INaturalistClient {

    @VisibleForTesting static final int RADIUS_KM = 1;
    @VisibleForTesting static final int CACHE_HOURS = 12;
    private static final int PAGE_SIZE = 200;

    /** iNaturalist's "Alive or Dead" annotation, and its "Dead" value. */
    private static final int ALIVE_OR_DEAD = 17;
    private static final int DEAD = 19;

    /**
     * Taxon ids, checked in this order against an observation's ancestry. The
     * gull subfamily comes before birds and the sharks and rays before fish,
     * so the specific group wins; whales, dolphins and sea turtles have no
     * card of their own and count as {@link SightingSpecies#OTHER}.
     */
    private static final int[][] GROUPS = {
        { 447155, SightingSpecies.SEAGULL.ordinal() },   // Larinae, gulls
        { 551307, SightingSpecies.SHARK.ordinal() },     // Selachii
        { 448201, SightingSpecies.RAY.ordinal() },       // Batoidea
        { 372843, SightingSpecies.SEAL.ordinal() },      // Phocoidea, seals and sea lions
        { 48332, SightingSpecies.JELLYFISH.ordinal() },  // Scyphozoa
        { 68095, SightingSpecies.JELLYFISH.ordinal() },  // Cubozoa
        { 48921, SightingSpecies.JELLYFISH.ordinal() },  // Hydrozoa: man o' war, by-the-wind sailors
        { 152871, SightingSpecies.OTHER.ordinal() },     // Cetacea
        { 372234, SightingSpecies.OTHER.ordinal() },     // Chelonioidea, sea turtles
        { 47178, SightingSpecies.FISH.ordinal() },       // Actinopterygii
        { 3, SightingSpecies.BIRD.ordinal() },           // Aves
    };

    private static final String KEY_PREFIX = "inat_tally_";
    private static final String KEY_AT_SUFFIX = "_at";
    private static final String DEAD_FIELD = "_dead";

    public interface Callback {
        /** @param tally what was seen, possibly nothing; null when iNaturalist could not be asked */
        void onResult(@Nullable WildlifeTally tally);
    }

    private final SharedPreferences prefs;

    public INaturalistClient(Context context) {
        this.prefs = context.getApplicationContext()
            .getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** The cached tally while it is fresh, else asks; the callback may run on a worker thread. */
    public void getTally(String beachId, double lat, double lon, Callback callback) {
        WildlifeTally cached = cachedTally(beachId);
        if (cached != null) {
            callback.onResult(cached);
            return;
        }
        Http.IO.execute(() -> {
            WildlifeTally tally = fetch(lat, lon);
            if (tally != null) writeCache(beachId, tally);
            callback.onResult(tally);
        });
    }

    /** What the cache holds while it is fresh, without asking anyone; null otherwise. */
    @Nullable
    public WildlifeTally cachedTally(String beachId) {
        long at = prefs.getLong(KEY_PREFIX + beachId + KEY_AT_SUFFIX, 0);
        if (at == 0 || System.currentTimeMillis() - at > TimeUnit.HOURS.toMillis(CACHE_HOURS)) {
            return null;
        }
        return decodeCache(prefs.getString(KEY_PREFIX + beachId, null));
    }

    @Nullable
    private static WildlifeTally fetch(double lat, double lon) {
        Request request = new Request.Builder()
            .url(url(lat, lon, System.currentTimeMillis()))
            .header("User-Agent", Http.USER_AGENT)
            .build();
        try (Response response = Http.CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return null;
            return parse(response.body().string());
        } catch (IOException e) {
            return null;
        }
    }

    @VisibleForTesting
    static String url(double lat, double lon, long now) {
        SimpleDateFormat day = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        day.setTimeZone(TimeZone.getTimeZone("UTC"));
        String since = day.format(new Date(now - TimeUnit.DAYS.toMillis(WildlifeTally.WINDOW_DAYS)));
        StringBuilder taxa = new StringBuilder();
        for (int[] group : GROUPS) {
            if (taxa.length() > 0) taxa.append(',');
            taxa.append(group[0]);
        }
        return String.format(Locale.US,
            "https://api.inaturalist.org/v2/observations?lat=%.5f&lng=%.5f&radius=%d"
                + "&d1=%s&taxon_id=%s&per_page=%d&fields=(taxon:(ancestor_ids:!t),"
                + "annotations:(controlled_attribute_id:!t,controlled_value_id:!t))",
            lat, lon, RADIUS_KM, since, taxa, PAGE_SIZE);
    }

    /**
     * Sorts one page of observations into groups. Observations with no taxon,
     * or one under none of the groups, are skipped. A page that will not parse
     * is null, so it is not cached as a fortnight of nothing.
     *
     * <p>Only the first page is read. At {@value #PAGE_SIZE} sightings a
     * fortnight within a kilometre the beach is busy, and a count that stops
     * there still says so; a second request per beach is not worth that.
     */
    @VisibleForTesting
    @Nullable
    static WildlifeTally parse(String body) {
        JsonElement results;
        try {
            results = JsonParser.parseString(body).getAsJsonObject().get("results");
        } catch (RuntimeException e) {
            return null;
        }
        if (results == null || !results.isJsonArray()) return null;

        Map<SightingSpecies, Integer> counts = new EnumMap<>(SightingSpecies.class);
        int dead = 0;
        for (JsonElement element : results.getAsJsonArray()) {
            try {
                JsonObject observation = element.getAsJsonObject();
                SightingSpecies group = groupOf(observation.getAsJsonObject("taxon"));
                if (group == null) continue;
                counts.merge(group, 1, Integer::sum);
                if (group == SightingSpecies.SEAL && isDead(observation)) dead++;
            } catch (RuntimeException e) {
                // One malformed observation costs that observation.
            }
        }
        return new WildlifeTally(counts, dead);
    }

    @Nullable
    private static SightingSpecies groupOf(@Nullable JsonObject taxon) {
        if (taxon == null || !taxon.has("ancestor_ids")) return null;
        Set<Integer> ancestry = new HashSet<>();
        for (JsonElement id : taxon.getAsJsonArray("ancestor_ids")) {
            ancestry.add(id.getAsInt());
        }
        for (int[] group : GROUPS) {
            if (ancestry.contains(group[0])) return SightingSpecies.values()[group[1]];
        }
        return null;
    }

    private static boolean isDead(JsonObject observation) {
        JsonElement annotations = observation.get("annotations");
        if (annotations == null || !annotations.isJsonArray()) return false;
        for (JsonElement element : annotations.getAsJsonArray()) {
            JsonObject annotation = element.getAsJsonObject();
            if (intOf(annotation, "controlled_attribute_id") == ALIVE_OR_DEAD
                && intOf(annotation, "controlled_value_id") == DEAD) {
                return true;
            }
        }
        return false;
    }

    private static int intOf(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || !value.isJsonPrimitive() ? -1 : value.getAsInt();
    }

    // ── Cache: {"SEAL":36,"FISH":48,"_dead":0}, by group name ──────────────

    private void writeCache(String beachId, WildlifeTally tally) {
        JsonObject stored = new JsonObject();
        for (SightingSpecies group : SightingSpecies.values()) {
            if (tally.count(group) > 0) stored.addProperty(group.name(), tally.count(group));
        }
        stored.addProperty(DEAD_FIELD, tally.getDeadSeals());
        prefs.edit()
            .putString(KEY_PREFIX + beachId, stored.toString())
            .putLong(KEY_PREFIX + beachId + KEY_AT_SUFFIX, System.currentTimeMillis())
            .apply();
    }

    @VisibleForTesting
    @Nullable
    static WildlifeTally decodeCache(@Nullable String stored) {
        if (stored == null) return null;
        try {
            JsonObject json = JsonParser.parseString(stored).getAsJsonObject();
            Map<SightingSpecies, Integer> counts = new EnumMap<>(SightingSpecies.class);
            for (SightingSpecies group : SightingSpecies.values()) {
                if (json.has(group.name())) counts.put(group, json.get(group.name()).getAsInt());
            }
            int dead = json.has(DEAD_FIELD) ? json.get(DEAD_FIELD).getAsInt() : 0;
            return new WildlifeTally(counts, dead);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
