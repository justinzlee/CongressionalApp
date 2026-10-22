package com.example.myapplication.network;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.example.myapplication.BuildConfig;
import com.example.myapplication.SettingsActivity;
import com.example.myapplication.model.SeagullRating;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.Request;
import okhttp3.Response;

/**
 * How many gulls birders have counted near a beach lately, from eBird.
 *
 * <p>eBird is the Cornell Lab of Ornithology's record of birdwatchers'
 * checklists, and unlike a photo database it records how many birds were
 * there, not just that one was. That is the number the seagull rating needs:
 * iNaturalist logs far more gull photographs at La Jolla Cove than at
 * Clearwater because far more people point cameras at La Jolla, not because
 * it has more gulls.
 *
 * <p>The recent-observations call returns the latest sighting of each species
 * within {@link #RADIUS_KM} km over the last {@link #BACK_DAYS} days. Summing
 * the counts of every gull species among them gives roughly how many gulls the
 * last birders there saw, which {@link SeagullRating#levelForCount} turns into
 * a step on the scale.
 *
 * <p>eBird needs a free API key. It is read from {@code ebird.apiKey} in
 * local.properties at build time, so it never reaches the repository; a build
 * without one simply has no gull starting point, and the card shows this
 * phone's rating alone.
 */
public class EBirdClient {

    @VisibleForTesting static final int RADIUS_KM = 2;
    @VisibleForTesting static final int BACK_DAYS = 14;
    @VisibleForTesting static final int CACHE_HOURS = 24;

    /** Stored when eBird answered but nobody has listed any bird nearby. */
    private static final int NO_DATA = -2;

    /**
     * The gull genera. Matched on the scientific name rather than the common
     * one, so "Gull-billed Tern" is not a gull and "Western x Glaucous-winged
     * Gull (hybrid)" and "gull sp." are.
     */
    private static final Set<String> GULL_GENERA = new HashSet<>(Arrays.asList(
        "Larus", "Chroicocephalus", "Leucophaeus", "Ichthyaetus", "Rissa",
        "Xema", "Hydrocoloeus", "Pagophila", "Rhodostethia", "Creagrus", "Saundersilarus",
        // eBird files "gull sp." under the subfamily: a gull nobody named, still a gull.
        "Larinae"));

    private static final String KEY_PREFIX = "ebird_gulls_";
    private static final String KEY_AT_SUFFIX = "_at";

    public interface Callback {
        /** @param level a {@link SeagullRating} step, or {@link SeagullRating#UNRATED} for no data */
        void onResult(int level);
    }

    private final SharedPreferences prefs;

    public EBirdClient(Context context) {
        this.prefs = context.getApplicationContext()
            .getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isConfigured() {
        return !BuildConfig.EBIRD_API_KEY.isEmpty();
    }

    /** The cached step while it is fresh, else asks; the callback may run on a worker thread. */
    public void getGullLevel(String beachId, double lat, double lon, Callback callback) {
        if (!isConfigured()) {
            callback.onResult(SeagullRating.UNRATED);
            return;
        }
        Integer cached = readCache(beachId);
        if (cached != null) {
            callback.onResult(toLevel(cached));
            return;
        }
        Http.IO.execute(() -> {
            Integer gulls = fetch(lat, lon);
            if (gulls != null) writeCache(beachId, gulls);
            callback.onResult(gulls == null ? SeagullRating.UNRATED : toLevel(gulls));
        });
    }

    /** The cached step without asking anyone, or {@link SeagullRating#UNRATED}. */
    public int cachedGullLevel(String beachId) {
        Integer cached = isConfigured() ? readCache(beachId) : null;
        return cached == null ? SeagullRating.UNRATED : toLevel(cached);
    }

    private static int toLevel(int gulls) {
        return gulls == NO_DATA ? SeagullRating.UNRATED : SeagullRating.levelForCount(gulls);
    }

    @Nullable
    private static Integer fetch(double lat, double lon) {
        Request request = new Request.Builder()
            .url(String.format(Locale.US,
                "https://api.ebird.org/v2/data/obs/geo/recent?lat=%.4f&lng=%.4f&dist=%d&back=%d",
                lat, lon, RADIUS_KM, BACK_DAYS))
            .header("X-eBirdApiToken", BuildConfig.EBIRD_API_KEY)
            .header("User-Agent", Http.USER_AGENT)
            .build();
        try (Response response = Http.CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return null;
            return parse(response.body().string());
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * The gulls in one answer, summed: {@link #NO_DATA} when no bird at all
     * was listed nearby, 0 when birds were and gulls were not, null when the
     * body is not an eBird answer. A species marked present without a number
     * ("X" on the checklist) counts as one.
     */
    @VisibleForTesting
    @Nullable
    static Integer parse(String body) {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(body);
        } catch (RuntimeException e) {
            return null;
        }
        if (parsed == null || !parsed.isJsonArray()) return null;
        if (parsed.getAsJsonArray().size() == 0) return NO_DATA;

        int gulls = 0;
        for (JsonElement element : parsed.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject sighting = element.getAsJsonObject();
            JsonElement sciName = sighting.get("sciName");
            if (sciName == null || !sciName.isJsonPrimitive()) continue;
            String genus = sciName.getAsString().trim().split("[\\s/]+")[0];
            if (!GULL_GENERA.contains(genus)) continue;
            JsonElement howMany = sighting.get("howMany");
            gulls += howMany != null && howMany.isJsonPrimitive()
                && howMany.getAsJsonPrimitive().isNumber()
                ? Math.max(1, howMany.getAsInt()) : 1;
        }
        return gulls;
    }

    @Nullable
    private Integer readCache(String beachId) {
        long at = prefs.getLong(KEY_PREFIX + beachId + KEY_AT_SUFFIX, 0);
        if (at == 0 || System.currentTimeMillis() - at > TimeUnit.HOURS.toMillis(CACHE_HOURS)) {
            return null;
        }
        return prefs.getInt(KEY_PREFIX + beachId, NO_DATA);
    }

    private void writeCache(String beachId, int gulls) {
        prefs.edit()
            .putInt(KEY_PREFIX + beachId, gulls)
            .putLong(KEY_PREFIX + beachId + KEY_AT_SUFFIX, System.currentTimeMillis())
            .apply();
    }
}
