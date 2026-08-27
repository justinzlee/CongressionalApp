package com.example.myapplication.data;

import com.example.myapplication.model.*;
import com.example.myapplication.network.WeatherClient;
import com.example.myapplication.util.UnitConverter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class BeachRepository {
    private final List<Beach> beaches;
    private final WeatherClient weatherClient = new WeatherClient();

    public interface OnDataRefreshListener {
        void onDataRefreshed();
    }

    public BeachRepository() {
        beaches = new ArrayList<>();
        
        // Initializing with null conditions to ensure real-time data is used
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

    public void refreshAllData(OnDataRefreshListener listener) {
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
        }
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
