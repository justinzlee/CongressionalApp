package com.example.myapplication.data;

import com.example.myapplication.model.*;
import com.example.myapplication.network.WeatherClient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
        String defaultImg = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=800&q=80";
        
        // Initializing with null conditions to ensure real-time data is used
        beaches.add(new Beach("CA1", "La Jolla Shores", 32.8589, -117.2558, 
            Collections.singletonList(new Hazard(HazardType.RIP_CURRENT, Severity.MODERATE, "Strong rips near the pier.", Collections.singletonList("Swim near lifeguard"))),
            null, 
            Collections.singletonList(new HelpLocation("La Jolla Lifeguard Station", HelpType.LIFEGUARD_TOWER, 32.8589, -117.2558)),
            "https://images.unsplash.com/photo-1519046904884-53103b34b206?auto=format&fit=crop&w=800&q=80"));
        
        beaches.add(new Beach("CA2", "Coronado Beach", 32.6859, -117.1831, null, null, null, defaultImg));
        beaches.add(new Beach("CA3", "Ocean Beach", 32.7495, -117.2553, null, null, null, defaultImg));
        beaches.add(new Beach("CA4", "Carlsbad State Beach", 33.1506, -117.3551, null, null, null, defaultImg));
        beaches.add(new Beach("CA5", "Moonlight State Beach", 33.0484, -117.2981, null, null, null, defaultImg));
        
        beaches.add(new Beach("FL1", "South Beach", 25.7826, -80.1285, 
            Collections.singletonList(new Hazard(HazardType.UV_INDEX, Severity.EXTREME, "High UV today.", Arrays.asList("Apply SPF 50", "Reapply every 2h"))),
            null,
            Collections.singletonList(new HelpLocation("Miami Beach Rescue", HelpType.HOSPITAL, 25.7826, -80.1285)),
            "https://images.unsplash.com/photo-1533105079780-92b9be482077?auto=format&fit=crop&w=800&q=80"));
        
        beaches.add(new Beach("FL2", "Haulover Beach", 25.9035, -80.1213, null, null, null, defaultImg));
        beaches.add(new Beach("FL4", "Clearwater Beach", 27.9765, -82.8318, null, null, null, defaultImg));
        beaches.add(new Beach("NY1", "Rockaway Beach", 40.5843, -73.8197, null, null, null, defaultImg));
        beaches.add(new Beach("NY2", "Jones Beach State Park", 40.5909, -73.5121, null, null, null, defaultImg));
        beaches.add(new Beach("TX1", "Galveston Island", 29.2991, -94.7946, null, null, null, defaultImg));
        beaches.add(new Beach("TX2", "South Padre Island", 26.1118, -97.1681, null, null, null, defaultImg));
        beaches.add(new Beach("OR1", "Cannon Beach", 45.8918, -123.9615, null, null, null, defaultImg));
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
}
