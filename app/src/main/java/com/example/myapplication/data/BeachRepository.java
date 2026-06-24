package com.example.myapplication.data;

import com.example.myapplication.model.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BeachRepository {
    private final List<Beach> beaches;
    private final String DEFAULT_IMG = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=800&q=80";

    public BeachRepository() {
        beaches = new ArrayList<>();
        
        // CALIFORNIA
        beaches.add(new Beach("CA1", "La Jolla Shores", 32.8589, -117.2558, 
            Arrays.asList(new Hazard(HazardType.RIP_CURRENT, Severity.MODERATE, "Strong rips near the pier.", Arrays.asList("Swim near lifeguard"))),
            new BeachConditions(18.5, 22.0, 7, 10.0, 0.9), null, "https://images.unsplash.com/photo-1519046904884-53103b34b206?auto=format&fit=crop&w=800&q=80"));
        beaches.add(new Beach("CA2", "Coronado Beach", 32.6859, -117.1831, null, null, null, DEFAULT_IMG));
        beaches.add(new Beach("CA3", "Ocean Beach", 32.7495, -117.2553, null, null, null, DEFAULT_IMG));
        beaches.add(new Beach("CA4", "Carlsbad State Beach", 33.1506, -117.3551, null, null, null, DEFAULT_IMG));
        beaches.add(new Beach("CA5", "Moonlight State Beach", 33.0484, -117.2981, null, null, null, DEFAULT_IMG));
        
        // FLORIDA
        beaches.add(new Beach("FL1", "South Beach", 25.7826, -80.1285, 
            Arrays.asList(new Hazard(HazardType.UV_INDEX, Severity.EXTREME, "High UV today.", Arrays.asList("Apply SPF 50", "Reapply every 2h"))),
            new BeachConditions(27.0, 31.0, 11, 8.0, 0.4), null, "https://images.unsplash.com/photo-1533105079780-92b9be482077?auto=format&fit=crop&w=800&q=80"));
        beaches.add(new Beach("FL2", "Haulover Beach", 25.9035, -80.1213, null, null, null, DEFAULT_IMG));
        beaches.add(new Beach("FL4", "Clearwater Beach", 27.9765, -82.8318, null, 
            new BeachConditions(28.0, 32.0, 10, 5.0, 0.2), null, DEFAULT_IMG));

        // NEW YORK
        beaches.add(new Beach("NY1", "Rockaway Beach", 40.5843, -73.8197, null, 
            new BeachConditions(19.0, 26.0, 6, 15.0, 1.1), null, DEFAULT_IMG));
        beaches.add(new Beach("NY2", "Jones Beach State Park", 40.5909, -73.5121, null, null, null, DEFAULT_IMG));

        // TEXAS
        beaches.add(new Beach("TX1", "Galveston Island", 29.2991, -94.7946, null, 
            new BeachConditions(26.5, 30.0, 9, 12.0, 0.6), null, DEFAULT_IMG));
        beaches.add(new Beach("TX2", "South Padre Island", 26.1118, -97.1681, null, null, null, DEFAULT_IMG));

        // OREGON
        beaches.add(new Beach("OR1", "Cannon Beach", 45.8918, -123.9615, null, 
            new BeachConditions(13.0, 18.0, 4, 18.0, 2.5), null, DEFAULT_IMG));
    }

    public List<Beach> getBeaches() {
        return beaches;
    }

    public List<Beach> searchBeaches(String query) {
        if (query == null || query.isEmpty()) return beaches;
        return beaches.stream()
            .filter(b -> b.getName().toLowerCase().contains(query.toLowerCase()))
            .collect(Collectors.toList());
    }
}
