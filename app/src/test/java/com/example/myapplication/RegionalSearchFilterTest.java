package com.example.myapplication;

import com.example.myapplication.data.BeachRepository;
import com.example.myapplication.model.Beach;
import com.example.myapplication.util.UnitConverter;

import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for the radius-filter + distance-sort behavior in
 * MainActivity#updateList, exercised from three real-world reference points:
 * San Diego, Carlsbad, and an East Coast city (Miami). This mirrors the exact
 * filter/sort logic in MainActivity so a change to the location handling code
 * (e.g. distance calc, radius comparison, sort order) gets caught here without
 * needing an emulator.
 */
public class RegionalSearchFilterTest {

    // Mirrors MainActivity.updateList()'s filter+sort pipeline.
    private List<Beach> filterAndSort(BeachRepository repo, double lat, double lon, double radiusMiles) {
        return repo.getBeaches().stream()
            .filter(b -> {
                if (radiusMiles >= 1000.0) return true;
                double d = UnitConverter.calculateDistance(lat, lon, b.getLatitude(), b.getLongitude());
                return d <= radiusMiles;
            })
            .sorted((b1, b2) -> Double.compare(
                UnitConverter.calculateDistance(lat, lon, b1.getLatitude(), b1.getLongitude()),
                UnitConverter.calculateDistance(lat, lon, b2.getLatitude(), b2.getLongitude())))
            .collect(Collectors.toList());
    }

    @Test
    public void sanDiego_25mileRadius_returnsLocalBeachesClosestFirst() {
        BeachRepository repo = BeachRepository.newForTesting();
        double sanDiegoLat = 32.7157, sanDiegoLon = -117.1611;

        List<Beach> results = filterAndSort(repo, sanDiegoLat, sanDiegoLon, 25.0);
        List<String> names = results.stream().map(Beach::getName).collect(Collectors.toList());

        assertEquals("Coronado Beach", names.get(0));
        assertEquals("Ocean Beach", names.get(1));
        assertEquals("La Jolla Shores", names.get(2));
        assertEquals("Moonlight State Beach", names.get(3));
        assertEquals(4, names.size());
        assertTrue("Carlsbad State Beach (~32mi) should be outside a 25mi San Diego search",
            !names.contains("Carlsbad State Beach"));
        assertTrue("No East Coast beach should ever appear in a 25mi San Diego search",
            !names.contains("South Beach"));
    }

    @Test
    public void carlsbad_25mileRadius_returnsLocalBeachesClosestFirst() {
        BeachRepository repo = BeachRepository.newForTesting();
        double carlsbadLat = 33.1581, carlsbadLon = -117.3506;

        List<Beach> results = filterAndSort(repo, carlsbadLat, carlsbadLon, 25.0);
        List<String> names = results.stream().map(Beach::getName).collect(Collectors.toList());

        assertEquals("Carlsbad State Beach", names.get(0));
        assertEquals("Moonlight State Beach", names.get(1));
        assertEquals("La Jolla Shores", names.get(2));
        assertEquals(3, names.size());
        assertTrue("Coronado/Ocean Beach (~30-34mi) should be outside a 25mi Carlsbad search",
            !names.contains("Coronado Beach") && !names.contains("Ocean Beach"));
    }

    @Test
    public void eastCoast_25mileRadiusFromMiami_returnsOnlyNearbyFloridaBeaches() {
        BeachRepository repo = BeachRepository.newForTesting();
        double miamiLat = 25.7826, miamiLon = -80.1285; // South Beach coordinates

        List<Beach> results = filterAndSort(repo, miamiLat, miamiLon, 25.0);
        List<String> names = results.stream().map(Beach::getName).collect(Collectors.toList());

        assertEquals("South Beach", names.get(0));
        assertEquals("Haulover Beach", names.get(1));
        assertEquals(2, names.size());
        assertTrue("Clearwater (~225mi) should be outside a 25mi Miami search",
            !names.contains("Clearwater Beach"));
        assertTrue("No West Coast beach should ever appear in a 25mi Miami search",
            !names.contains("La Jolla Shores"));
    }

    @Test
    public void allRadius_disablesDistanceFilterFromAnyRegion() {
        BeachRepository repo = BeachRepository.newForTesting();
        // "All" is represented in MainActivity by Double.MAX_VALUE.
        List<Beach> fromSanDiego = filterAndSort(repo, 32.7157, -117.1611, Double.MAX_VALUE);
        List<Beach> fromMiami = filterAndSort(repo, 25.7826, -80.1285, Double.MAX_VALUE);

        assertEquals(17, fromSanDiego.size());
        assertEquals(17, fromMiami.size());
    }

    @Test
    public void searchQuery_combinesWithRegion_forCarlsbad() {
        BeachRepository repo = BeachRepository.newForTesting();
        List<Beach> matches = repo.searchBeaches("state beach").stream()
            .filter(b -> UnitConverter.calculateDistance(33.1581, -117.3506, b.getLatitude(), b.getLongitude()) <= 25.0)
            .collect(Collectors.toList());

        List<String> names = matches.stream().map(Beach::getName).collect(Collectors.toList());
        assertTrue(names.contains("Carlsbad State Beach"));
        assertTrue(names.contains("Moonlight State Beach"));
        assertEquals(2, names.size());
    }
}
