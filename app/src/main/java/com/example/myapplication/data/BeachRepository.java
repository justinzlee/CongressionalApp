package com.example.myapplication.data;

import androidx.annotation.VisibleForTesting;

import com.example.myapplication.model.*;
import com.example.myapplication.network.PhotoClient;
import com.example.myapplication.network.WeatherClient;
import com.example.myapplication.util.UnitConverter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class BeachRepository {

    /**
     * How long fetched conditions are treated as current. Every screen used to
     * build its own repository and refetch all seventeen beaches on entry, so
     * conditions reset to "--" each time you came back to a tab and the wait
     * started over. One shared instance keeps what it has already learned;
     * this window then stops rapid tab-switching from re-hitting the network
     * for readings that only move every fifteen minutes upstream anyway.
     */
    private static final long FRESH_FOR_MS = 5 * 60 * 1000L;

    private static volatile BeachRepository instance;

    /**
     * The one repository for the process. Conditions and resolved photos are
     * cached on it, so a screen opening second gets them immediately.
     */
    public static BeachRepository getInstance() {
        BeachRepository local = instance;
        if (local == null) {
            synchronized (BeachRepository.class) {
                local = instance;
                if (local == null) {
                    local = new BeachRepository();
                    instance = local;
                }
            }
        }
        return local;
    }

    /**
     * A standalone repository with its own copy of the beach list, for tests
     * that filter and search without touching — or being touched by — the
     * shared instance. Production code goes through {@link #getInstance()}, so
     * that conditions fetched on one screen are already there on the next.
     */
    @VisibleForTesting
    public static BeachRepository newForTesting() {
        return new BeachRepository();
    }

    private final List<Beach> beaches;
    private final WeatherClient weatherClient = new WeatherClient();
    private final PhotoClient photoClient = new PhotoClient();

    /** Guards against a second screen kicking off a duplicate fan-out. */
    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    /** Listeners waiting on the in-flight refresh; cleared when it finishes. */
    private final List<OnDataRefreshListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicInteger outstanding = new AtomicInteger();
    private volatile long refreshedAt;

    public interface OnDataRefreshListener {
        void onDataRefreshed();
    }

    private BeachRepository() {
        beaches = new ArrayList<>();
        
        // Initializing with null conditions to ensure real-time data is used.
        // The image URLs below are generic stock-photo fallbacks shown until
        // refreshAllData() resolves a real photo of each specific beach.
        beaches.add(new Beach("CA1", "La Jolla Shores", 32.8589, -117.2558,
            Collections.singletonList(new Hazard(HazardType.RIP_CURRENT, Severity.MODERATE, "Strong rips near the pier.", Collections.singletonList("Swim near lifeguard"))),
            null, 
            Collections.singletonList(new HelpLocation("La Jolla Lifeguard Station", HelpType.LIFEGUARD_TOWER, 32.8589, -117.2558)),
            "https://images.unsplash.com/photo-1519046904884-53103b34b206?auto=format&fit=crop&w=800&q=80"));
        
        beaches.add(new Beach("CA2", "Coronado Beach", 32.6859, -117.1831, null, null, null, 
            "https://images.unsplash.com/photo-1549488344-1f9b8d2bd1f3?auto=format&fit=crop&w=800&q=80"));
        beaches.add(new Beach("CA3", "Ocean Beach", 32.7495, -117.2553, null, null, null, 
            "https://images.unsplash.com/photo-1484666086787-6cd0ccd39860?auto=format&fit=crop&w=800&q=80"));
        beaches.add(new Beach("CA4", "Carlsbad State Beach", 33.1506, -117.3551, null, null, null, 
            "https://images.unsplash.com/photo-1624388300251-5d9c24097486?auto=format&fit=crop&w=800&q=80"));
        beaches.add(new Beach("CA5", "Moonlight State Beach", 33.0484, -117.2981, null, null, null, 
            "https://images.unsplash.com/photo-1439460127823-30ad2f7da420?auto=format&fit=crop&w=800&q=80"));

        beaches.add(new Beach("LA1", "Santa Monica Pier", 34.0099, -118.4960, null, null, null,
            "https://images.unsplash.com/photo-1505245208761-ba872912fac0?auto=format&fit=crop&w=800&q=80"));
        beaches.add(new Beach("LA2", "Huntington Beach", 33.6599, -117.9992, null, null, null,
            "https://images.unsplash.com/photo-1510414842594-a61c69b5ae57?auto=format&fit=crop&w=800&q=80"));
        beaches.add(new Beach("LA3", "Malibu Lagoon", 34.0322, -118.6775, null, null, null,
            "https://images.unsplash.com/photo-1588665798939-269e9e8f49a2?auto=format&fit=crop&w=800&q=80"));
        beaches.add(new Beach("LA4", "Venice Beach", 33.9850, -118.4695, null, null, null,
            "https://images.unsplash.com/photo-1502920917128-1aa500764cbd?auto=format&fit=crop&w=800&q=80"));
        
        beaches.add(new Beach("FL1", "South Beach", 25.7826, -80.1285, 
            Collections.singletonList(new Hazard(HazardType.UV_INDEX, Severity.EXTREME, "High UV today.", Arrays.asList("Apply SPF 50", "Reapply every 2h"))),
            null,
            Collections.singletonList(new HelpLocation("Miami Beach Rescue", HelpType.HOSPITAL, 25.7826, -80.1285)),
            "https://images.unsplash.com/photo-1533105079780-92b9be482077?auto=format&fit=crop&w=800&q=80"));
        
        beaches.add(new Beach("FL2", "Haulover Beach", 25.9035, -80.1213, null, null, null, 
            "https://images.unsplash.com/photo-1739097746183-514d8236d246?auto=format&fit=crop&w=800&q=80"));
        beaches.add(new Beach("FL4", "Clearwater Beach", 27.9765, -82.8318, null, null, null, 
            "https://images.unsplash.com/photo-1626189913063-441366b96e6a?auto=format&fit=crop&w=800&q=80"));
        beaches.add(new Beach("NY1", "Rockaway Beach", 40.5843, -73.8197, null, null, null, 
            "https://images.unsplash.com/photo-1596752765363-255018659d81?auto=format&fit=crop&w=800&q=80"));
        beaches.add(new Beach("NY2", "Jones Beach State Park", 40.5909, -73.5121, null, null, null, 
            "https://images.unsplash.com/photo-1663435136894-3a521e10ed50?auto=format&fit=crop&w=800&q=80"));
        beaches.add(new Beach("TX1", "Galveston Island", 29.2991, -94.7946, null, null, null, 
            "https://images.unsplash.com/photo-1701968531518-e7d698e57608?auto=format&fit=crop&w=800&q=80"));
        beaches.add(new Beach("TX2", "South Padre Island", 26.1118, -97.1681, null, null, null, 
            "https://images.unsplash.com/photo-1544196036-3f1e110d9af5?auto=format&fit=crop&w=800&q=80"));
        beaches.add(new Beach("OR1", "Cannon Beach", 45.8918, -123.9615, null, null, null, 
            "https://images.unsplash.com/photo-1534067783941-51c9c23eccfd?auto=format&fit=crop&w=800&q=80"));
    }

    /**
     * Brings every beach's conditions and photo up to date, then reports back
     * as each one lands so the list fills in progressively.
     *
     * <p>Cheap to call from anywhere: if the data is still fresh the listener
     * is told immediately and nothing touches the network, and if a refresh is
     * already running the listener simply joins it rather than starting a
     * second copy.
     */
    public void refreshAllData(OnDataRefreshListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }

        boolean fresh = System.currentTimeMillis() - refreshedAt < FRESH_FOR_MS;
        if (fresh || !refreshing.compareAndSet(false, true)) {
            // Already current, or someone else's fan-out is in flight. Hand
            // back what is cached now; an in-flight refresh will call again as
            // its results arrive.
            if (listener != null) {
                listener.onDataRefreshed();
                if (fresh) listeners.remove(listener);
            }
            return;
        }

        List<Beach> snapshot = getBeaches();
        // Two requests per beach, and the refresh is done when the last of
        // them reports one way or the other.
        outstanding.set(snapshot.size() * 2);

        for (int i = 0; i < snapshot.size(); i++) {
            final int index = i;
            Beach beach = snapshot.get(i);

            weatherClient.getBeachConditions(beach.getLatitude(), beach.getLongitude(),
                new WeatherClient.WeatherCallback() {
                    @Override
                    public void onSuccess(BeachConditions conditions) {
                        synchronized (beaches) {
                            beaches.set(index, beaches.get(index).withConditions(conditions));
                        }
                        settle(true);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        // Leave the beach without conditions: SafetyLevel reads
                        // that as "no verdict yet" rather than inventing one.
                        settle(false);
                    }
                });

            // Replace the generic stock photo with a real photo of this specific beach.
            photoClient.getBeachPhoto(beach.getName(), new PhotoClient.PhotoCallback() {
                @Override
                public void onSuccess(String imageUrl) {
                    synchronized (beaches) {
                        beaches.set(index, beaches.get(index).withImageUrl(imageUrl));
                    }
                    settle(true);
                }

                @Override
                public void onFailure(Exception e) {
                    // Keep the stock-photo fallback already set on the beach.
                    settle(false);
                }
            });
        }
    }

    /**
     * Records one finished request: tells the waiting screens when it changed
     * something, and closes the refresh out once the last one is in.
     */
    private void settle(boolean changed) {
        if (changed) {
            for (OnDataRefreshListener waiting : listeners) {
                waiting.onDataRefreshed();
            }
        }
        if (outstanding.decrementAndGet() <= 0) {
            refreshedAt = System.currentTimeMillis();
            refreshing.set(false);
            // Screens that have since been left must not be retained until the
            // next refresh happens to come along.
            listeners.clear();
        }
    }

    /** Drops the freshness window so the next call refetches. */
    public void invalidate() {
        refreshedAt = 0L;
    }

    public List<Beach> getBeaches() {
        synchronized (beaches) {
            return new ArrayList<>(beaches);
        }
    }

    public List<Beach> searchBeaches(String query) {
        synchronized (beaches) {
            if (query == null || query.isEmpty()) return new ArrayList<>(beaches);
            return beaches.stream()
                .filter(b -> b.getName().toLowerCase().contains(query.toLowerCase()))
                .collect(Collectors.toList());
        }
    }

    /**
     * Beaches within radiusMiles of (lat, lon), closest first. If none fall within
     * the radius (e.g. the user is nowhere near a covered coastline), falls back to
     * the closest fallbackCount beaches so a location-based report is never empty.
     */
    public List<Beach> findNearbyBeaches(double lat, double lon, double radiusMiles, int fallbackCount) {
        Comparator<Beach> byDistance = Comparator.comparingDouble(
            b -> UnitConverter.calculateDistance(lat, lon, b.getLatitude(), b.getLongitude()));

        List<Beach> withinRadius = getBeaches().stream()
            .filter(b -> UnitConverter.calculateDistance(lat, lon, b.getLatitude(), b.getLongitude()) <= radiusMiles)
            .sorted(byDistance)
            .collect(Collectors.toList());

        if (!withinRadius.isEmpty()) return withinRadius;

        return getBeaches().stream()
            .sorted(byDistance)
            .limit(fallbackCount)
            .collect(Collectors.toList());
    }
}
