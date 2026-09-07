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
        assertEquals("Mission Beach", names.get(2));
        assertEquals("Pacific Beach", names.get(3));
        assertEquals("Imperial Beach", names.get(4));
        assertEquals("La Jolla Shores", names.get(5));
        assertEquals("Torrey Pines State Beach", names.get(6));
        assertEquals("Del Mar City Beach", names.get(7));
        assertEquals("Moonlight State Beach", names.get(8));
        assertEquals(9, names.size());
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
        assertEquals("Oceanside Beach", names.get(1));
        assertEquals("Moonlight State Beach", names.get(2));
        assertEquals("Del Mar City Beach", names.get(3));
        assertEquals("Torrey Pines State Beach", names.get(4));
        assertEquals("La Jolla Shores", names.get(5));
        assertEquals(6, names.size());
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
        assertEquals("Fort Lauderdale Beach", names.get(2));
        assertEquals(3, names.size());
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

        assertEquals(repo.getBeaches().size(), fromSanDiego.size());
        assertEquals(repo.getBeaches().size(), fromMiami.size());
    }

    /**
     * The dataset reaches beyond the two ocean coasts now, so the radius
     * filter has to hold up in places it never used to see: an inland city on
     * a lake, and an island two thousand miles from anywhere else.
     */
    @Test
    public void chicago_50mileRadius_returnsLakeMichiganBeachesOnly() {
        BeachRepository repo = BeachRepository.newForTesting();

        List<Beach> results = filterAndSort(repo, 41.8781, -87.6298, 50.0);
        List<String> names = results.stream().map(Beach::getName).collect(Collectors.toList());

        assertEquals("North Avenue Beach", names.get(0));
        assertEquals("West Beach, Indiana Dunes", names.get(1));
        assertEquals("The lakefront is all there is within 50mi of Chicago", 2, names.size());
        assertTrue("Milwaukee is a further 40mi up the same shore",
            !names.contains("Bradford Beach"));
        assertTrue("No ocean beach is anywhere near Chicago",
            !names.contains("La Jolla Shores") && !names.contains("South Beach"));
    }

    @Test
    public void honolulu_25mileRadius_returnsOahuBeachesOnly() {
        BeachRepository repo = BeachRepository.newForTesting();

        List<Beach> results = filterAndSort(repo, 21.3069, -157.8583, 25.0);
        List<String> names = results.stream().map(Beach::getName).collect(Collectors.toList());

        assertEquals("Waikiki Beach", names.get(0));
        assertTrue("Hanauma Bay is on the same island", names.contains("Hanauma Bay"));
        assertTrue("The other islands are a flight away",
            !names.contains("Poipu Beach") && !names.contains("Hapuna Beach"));
        assertTrue("So is the mainland", !names.contains("Ocean Beach, San Francisco"));
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
        assertTrue(names.contains("Torrey Pines State Beach"));
        assertEquals(3, names.size());
    }
}
