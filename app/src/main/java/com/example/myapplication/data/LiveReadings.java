package com.example.myapplication.data;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.example.myapplication.model.AirQuality;
import com.example.myapplication.model.WaterQuality;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The latest air and water readings for each beach, shared across screens.
 *
 * <p>These arrive asynchronously and are wanted synchronously. A sewage
 * advisory has to become a {@link com.example.myapplication.model.Hazard} so it
 * reaches the Alerts tab and the unread badge, and hazards are assembled on the
 * main thread by whichever screen happens to be drawing — which cannot wait on
 * a network call. So whoever fetches a reading leaves it here, and the hazard
 * builders read whatever has landed. Exactly the role {@link
 * BeachRepository#updateConditions} already plays for the forecast.
 *
 * <p>Process-scoped and deliberately not persisted. A water advisory is a
 * statement about today; one restored from disk next week would be a
 * confident-looking claim about weather that has since changed. Everything here
 * is refetched within seconds of a beach screen opening.
 */
public final class LiveReadings {

    private static final Map<String, AirQuality> AIR = new ConcurrentHashMap<>();
    private static final Map<String, WaterQuality> WATER = new ConcurrentHashMap<>();

    private LiveReadings() {
    }

    public static void putAir(String beachId, @Nullable AirQuality air) {
        if (beachId == null) return;
        if (air == null) {
            AIR.remove(beachId);
        } else {
            AIR.put(beachId, air);
        }
    }

    public static void putWater(String beachId, @Nullable WaterQuality water) {
        if (beachId == null) return;
        if (water == null) {
            WATER.remove(beachId);
        } else {
            WATER.put(beachId, water);
        }
    }

    @Nullable
    public static AirQuality airFor(String beachId) {
        return beachId == null ? null : AIR.get(beachId);
    }

    @Nullable
    public static WaterQuality waterFor(String beachId) {
        return beachId == null ? null : WATER.get(beachId);
    }

    /** Empties both, so one test cannot leave a reading behind for the next. */
    @VisibleForTesting
    public static void clear() {
        AIR.clear();
        WATER.clear();
    }
}
