package com.example.myapplication.data;

import com.example.myapplication.model.*;
import com.example.myapplication.network.PhotoClient;
import com.example.myapplication.network.WeatherClient;
import com.example.myapplication.util.UnitConverter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class BeachRepository {

    /**
     * How long fetched conditions are treated as current. Surf, UV and wind
     * forecasts are published hourly, so refetching them every time a tab is
     * opened costs two requests per beach and tells us nothing new.
     */
    private static final long REFRESH_INTERVAL_MS = 10 * 60 * 1000L;

    /**
     * The one beach list in the app.
     *
     * <p>Each screen used to build its own repository, which meant the live
     * conditions the home screen fetched were invisible everywhere else: the
     * Alerts tab and its badge read a list where every beach still had null
     * conditions, so anything derived from live readings could never appear
     * there. Sharing one instance also means opening a second tab reuses the
     * forecast already in hand rather than fetching all of it again.
     *
     * <p>Holds no Context, so keeping it for the life of the process is safe.
     */
    private static volatile BeachRepository instance;

    private final List<Beach> beaches;
    private final WeatherClient weatherClient = new WeatherClient();
    private final PhotoClient photoClient = new PhotoClient();
    private volatile long lastRefreshStartedAt;

    public interface OnDataRefreshListener {
        void onDataRefreshed();
    }

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
     * Fetches conditions and photos for every beach, unless that already
     * happened recently enough to still be current.
     *
     * <p>The freshness check needs conditions actually in hand, not just a
     * recent attempt: if the first fetch failed there is nothing to reuse, and
     * skipping the retry would leave the app on "Checking conditions" until the
     * interval elapsed.
     */
    public void refreshAllData(OnDataRefreshListener listener) {
        if (System.currentTimeMillis() - lastRefreshStartedAt < REFRESH_INTERVAL_MS
                && hasAnyConditions()) {
            // Already current — tell the caller so it draws with what we have.
            if (listener != null) listener.onDataRefreshed();
            return;
        }
        lastRefreshStartedAt = System.currentTimeMillis();

        for (int i = 0; i < beaches.size(); i++) {
            final int index = i;
            Beach beach = beaches.get(i);
            
            // Refresh Weather
            weatherClient.getBeachConditions(beach.getLatitude(), beach.getLongitude(), new WeatherClient.WeatherCallback() {
                @Override
                public void onSuccess(BeachConditions conditions) {
                    synchronized (beaches) {
                        beaches.set(index, beaches.get(index).withConditions(conditions));
                    }
                    if (listener != null) listener.onDataRefreshed();
                }

                @Override
                public void onFailure(Exception e) {
                }
            });

            // Replace the generic stock photo with a real photo of this specific beach.
            photoClient.getBeachPhoto(beach.getName(), new PhotoClient.PhotoCallback() {
                @Override
                public void onSuccess(String imageUrl) {
                    synchronized (beaches) {
                        beaches.set(index, beaches.get(index).withImageUrl(imageUrl));
                    }
                    if (listener != null) listener.onDataRefreshed();
                }

                @Override
                public void onFailure(Exception e) {
                    // Keep the stock-photo fallback already set on the beach.
                }
            });
        }
    }

    public List<Beach> getBeaches() {
        synchronized (beaches) {
            return new ArrayList<>(beaches);
        }
    }

    /**
     * Stores conditions fetched for a single beach, so a screen that refreshes
     * one beach on its own — the detail screen does — hands the reading back
     * to the rest of the app instead of keeping it to itself.
     */
    public void updateConditions(String beachId, BeachConditions conditions) {
        synchronized (beaches) {
            for (int i = 0; i < beaches.size(); i++) {
                if (Objects.equals(beaches.get(i).getId(), beachId)) {
                    beaches.set(i, beaches.get(i).withConditions(conditions));
                    return;
                }
            }
        }
    }

    /** Whether any beach has live conditions yet, i.e. a fetch has landed. */
    private boolean hasAnyConditions() {
        synchronized (beaches) {
            for (Beach beach : beaches) {
                if (beach.getConditions() != null) {
                    return true;
                }
            }
        }
        return false;
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
