package com.example.myapplication.data;

import com.example.myapplication.model.Beach;
import com.example.myapplication.model.HelpLocation;
import com.example.myapplication.model.HelpType;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Verifies the hardcoded beach dataset still contains correct, complete data
 * for the San Diego, Carlsbad, and East Coast regions, and that search works.
 */
public class BeachRepositoryTest {

    private static final double COORD_DELTA = 0.0001;

    /**
     * The size of the hardcoded dataset. Pinned so that a beach cannot be lost
     * to a bad merge unnoticed; bump it deliberately when the list grows.
     */
    private static final int EXPECTED_BEACH_COUNT = 114;

    /** The Great Lakes beaches: no tide to read, so no station to read it from. */
    private static final Set<String> LAKE_BEACHES = new HashSet<>(Arrays.asList(
        "MI1", "MI2", "IL1", "IN1", "WI1", "OH1", "MN1", "NY7"));

    /** NOAA's station ids: seven digits, or three letters and four digits. */
    private static final Pattern NOAA_STATION = Pattern.compile("\\d{7}|[A-Z]{3}\\d{4}");

    private Beach find(List<Beach> beaches, String name) {
        Optional<Beach> match = beaches.stream()
            .filter(b -> b.getName().equals(name))
            .findFirst();
        if (!match.isPresent()) {
            fail("Beach not found: " + name);
        }
        return match.get();
    }

    @Test
    public void repository_loadsAllExpectedBeaches() {
        List<Beach> beaches = BeachRepository.newForTesting().getBeaches();
        assertEquals(EXPECTED_BEACH_COUNT, beaches.size());
    }

    @Test
    public void sanDiegoBeaches_presentWithCorrectCoordinates() {
        List<Beach> beaches = BeachRepository.newForTesting().getBeaches();

        Beach laJolla = find(beaches, "La Jolla Shores");
        assertEquals("CA1", laJolla.getId());
        assertEquals(32.8589, laJolla.getLatitude(), COORD_DELTA);
        assertEquals(-117.2558, laJolla.getLongitude(), COORD_DELTA);
        assertFalse("La Jolla Shores should have a hazard entry", laJolla.getHazards().isEmpty());

        Beach coronado = find(beaches, "Coronado Beach");
        assertEquals("CA2", coronado.getId());
        assertEquals(32.6859, coronado.getLatitude(), COORD_DELTA);
        assertEquals(-117.1831, coronado.getLongitude(), COORD_DELTA);

        Beach oceanBeach = find(beaches, "Ocean Beach");
        assertEquals("CA3", oceanBeach.getId());
        assertEquals(32.7495, oceanBeach.getLatitude(), COORD_DELTA);
        assertEquals(-117.2553, oceanBeach.getLongitude(), COORD_DELTA);
    }

    @Test
    public void carlsbadBeaches_presentWithCorrectCoordinates() {
        List<Beach> beaches = BeachRepository.newForTesting().getBeaches();

        Beach carlsbad = find(beaches, "Carlsbad State Beach");
        assertEquals("CA4", carlsbad.getId());
        // On the sand at Tamarack. The point this replaced was half a
        // kilometre out to sea.
        assertEquals(33.1527, carlsbad.getLatitude(), COORD_DELTA);
        assertEquals(-117.3503, carlsbad.getLongitude(), COORD_DELTA);

        Beach moonlight = find(beaches, "Moonlight State Beach");
        assertEquals("CA5", moonlight.getId());
        assertEquals(33.0484, moonlight.getLatitude(), COORD_DELTA);
        assertEquals(-117.2981, moonlight.getLongitude(), COORD_DELTA);
    }

    @Test
    public void eastCoastBeaches_presentWithCorrectCoordinates() {
        List<Beach> beaches = BeachRepository.newForTesting().getBeaches();

        Beach southBeach = find(beaches, "South Beach");
        assertEquals("FL1", southBeach.getId());
        assertEquals(25.7826, southBeach.getLatitude(), COORD_DELTA);
        assertEquals(-80.1285, southBeach.getLongitude(), COORD_DELTA);
        // UV comes from the live reading. A "High UV today" written into the
        // list and never cleared held this beach at "Not safe to swim" all night.
        assertTrue("South Beach should carry no recorded hazard", southBeach.getHazards().isEmpty());
        assertEquals(HelpType.LIFEGUARD_TOWER, southBeach.getNearestHelp().get(0).getType());

        Beach haulover = find(beaches, "Haulover Beach");
        assertEquals("FL2", haulover.getId());

        Beach clearwater = find(beaches, "Clearwater Beach");
        assertEquals("FL4", clearwater.getId());

        Beach rockaway = find(beaches, "Rockaway Beach");
        assertEquals("NY1", rockaway.getId());
        assertEquals(40.5843, rockaway.getLatitude(), COORD_DELTA);

        Beach jones = find(beaches, "Jones Beach State Park");
        assertEquals("NY2", jones.getId());
    }

    @Test
    public void allBeachIdsAreUnique() {
        List<Beach> beaches = BeachRepository.newForTesting().getBeaches();
        long distinctIds = beaches.stream().map(Beach::getId).distinct().count();
        assertEquals(beaches.size(), distinctIds);
    }

    @Test
    public void allBeachNamesAreUnique() {
        List<Beach> beaches = BeachRepository.newForTesting().getBeaches();
        long distinctNames = beaches.stream().map(Beach::getName).distinct().count();
        assertEquals("Two beaches sharing a name are indistinguishable in the list and in "
            + "search - where two real places share one, the name has to say which is which",
            beaches.size(), distinctNames);
    }

    /**
     * A photo belongs to the one beach it was given to. The same stock pictures
     * used to be dealt round the whole list, so a dozen beaches on three coasts
     * showed the same shoreline; a beach with no photo of its own shows the
     * drawn one instead. Compared without the query string, which only sizes
     * the image.
     */
    @Test
    public void noTwoBeachesShareAPhoto() {
        Set<String> seen = new HashSet<>();
        for (Beach beach : BeachRepository.newForTesting().getBeaches()) {
            String url = beach.getImageUrl();
            if (url == null) continue;
            String photo = url.split("\\?")[0];
            assertTrue(beach.getName() + " repeats a photo another beach already shows: " + photo,
                seen.add(photo));
        }
    }

    /**
     * The beaches whose photo has been checked to be of that beach: geotagged
     * on the sand on Wikimedia Commons, or showing a landmark nowhere else has
     * (Scripps Pier, the Hotel del Coronado, Haystack Rock). Every other beach
     * shows the drawn shoreline. The stock photos that used to fill the list
     * turned out to be the Maldives, Yosemite and a mosque in Malaysia, so a
     * photo added without that check fails here until it has had one.
     */
    private static final Set<String> CHECKED_PHOTOS = new HashSet<>(Arrays.asList(
        "CA1", "CA2", "CA3", "CA4", "CA5", "LA2", "LA3", "FL1", "FL2", "FL4", "NY1", "TX1", "TX2", "OR1",
        "CA6", "CA8", "CA9", "CA10", "CA11", "LA5", "LA6", "LA7", "LA8", "LA9", "LA10",
        "CC1", "CC2", "CC3", "CC5", "SF1", "SF2", "SF3", "OR2", "OR3", "OR4",
        "WA1", "WA2", "WA3", "WA4", "HI1", "HI2", "HI3", "HI4", "HI5", "HI6",
        "LOU1", "MS1", "FL3", "FL8", "FL9", "FL10", "FL11", "FL5", "FL6", "FL7", "FL12", "FL13",
        "SC1", "SC2", "NC1", "NC3", "MD1", "DE1", "DE2", "NJ1", "NJ4", "NJ5", "NY3", "NY4",
        "RI1", "RI2", "MA1", "ME1", "ME2", "ME3", "MI1", "MI2", "WI1", "OH1", "PR1", "PR2"));

    @Test
    public void onlyCheckedPhotosAreShown() {
        for (Beach beach : BeachRepository.newForTesting().getBeaches()) {
            boolean hasPhoto = beach.getImageUrl() != null;
            assertEquals(beach.getName() + (hasPhoto
                    ? " has a photo nobody has checked is of it: " + beach.getImageUrl()
                    : " is listed as checked but has no photo"),
                CHECKED_PHOTOS.contains(beach.getId()), hasPhoto);
        }
    }

    /**
     * The dataset used to be Southern California with three other states for
     * company. It reaches every coast now, so this pins one beach in each
     * region that had none: if a region is dropped, the app quietly stops
     * covering that coastline and nothing else would say so.
     */
    @Test
    public void everyCoastlineIsRepresented() {
        List<Beach> beaches = BeachRepository.newForTesting().getBeaches();

        Beach waikiki = find(beaches, "Waikiki Beach");
        assertEquals("HI1", waikiki.getId());
        assertEquals(21.2760, waikiki.getLatitude(), COORD_DELTA);
        assertEquals(-157.8270, waikiki.getLongitude(), COORD_DELTA);

        Beach flamenco = find(beaches, "Flamenco Beach");
        assertEquals("PR1", flamenco.getId());

        // A lake, so no tide station and no tides - but people drown in Lake
        // Michigan every summer, so it belongs here all the same.
        Beach northAvenue = find(beaches, "North Avenue Beach");
        assertEquals("IL1", northAvenue.getId());

        find(beaches, "Ruby Beach");                  // Washington
        find(beaches, "Ocean Beach, San Francisco");  // Northern California
        find(beaches, "Grand Isle State Park");       // Louisiana
        find(beaches, "Myrtle Beach");                // South Carolina
        find(beaches, "Cape Hatteras");               // North Carolina
        find(beaches, "Rehoboth Beach");              // Delaware
        find(beaches, "Cape May Beach");              // New Jersey
        find(beaches, "Nauset Beach");                // Massachusetts
        find(beaches, "Popham Beach");                // Maine
    }

    /**
     * A sea beach without a station shows no tides, and nothing else would
     * say anything was missing. This catches a beach added without one.
     */
    @Test
    public void everySeaBeachNamesATideStation() {
        for (Beach beach : BeachRepository.newForTesting().getBeaches()) {
            if (LAKE_BEACHES.contains(beach.getId())) continue;
            String station = beach.getTideStation();
            assertNotNull(beach.getName() + " names no tide station", station);
            assertTrue(beach.getName() + " has a malformed station id: " + station,
                NOAA_STATION.matcher(station).matches());
        }
    }

    @Test
    public void lakeBeachesNameNoTideStation() {
        int lakes = 0;
        for (Beach beach : BeachRepository.newForTesting().getBeaches()) {
            if (!LAKE_BEACHES.contains(beach.getId())) continue;
            assertNull(beach.getName() + " is on a lake and has no tide", beach.getTideStation());
            lakes++;
        }
        assertEquals(LAKE_BEACHES.size(), lakes);
    }

    /**
     * A help point at the beach's own coordinates is a placeholder, not a
     * place — and it is where the SOS sheet sends someone for directions.
     */
    @Test
    public void helpPointsAreTheirOwnPlaceNotTheBeachPinAgain() {
        for (Beach beach : BeachRepository.newForTesting().getBeaches()) {
            for (HelpLocation help : beach.getNearestHelp()) {
                assertFalse(beach.getName() + ": " + help.getName() + " is pinned to the beach itself",
                    help.getLatitude() == beach.getLatitude()
                        && help.getLongitude() == beach.getLongitude());
            }
        }
    }

    @Test
    public void search_isCaseInsensitiveSubstringMatch() {
        BeachRepository repo = BeachRepository.newForTesting();
        List<Beach> results = repo.searchBeaches("carlsbad");
        assertEquals(1, results.size());
        assertEquals("Carlsbad State Beach", results.get(0).getName());

        List<Beach> mixedCase = repo.searchBeaches("CoRoNaDo");
        assertEquals(1, mixedCase.size());
        assertEquals("Coronado Beach", mixedCase.get(0).getName());
    }

    @Test
    public void search_emptyOrNullQuery_returnsAllBeaches() {
        BeachRepository repo = BeachRepository.newForTesting();
        assertEquals(EXPECTED_BEACH_COUNT, repo.searchBeaches("").size());
        assertEquals(EXPECTED_BEACH_COUNT, repo.searchBeaches(null).size());
    }

    @Test
    public void search_noMatch_returnsEmptyList() {
        BeachRepository repo = BeachRepository.newForTesting();
        assertTrue(repo.searchBeaches("NoSuchBeachXYZ").isEmpty());
    }

    @Test
    public void getBeaches_returnsDefensiveCopy() {
        BeachRepository repo = BeachRepository.newForTesting();
        List<Beach> beaches = repo.getBeaches();
        beaches.clear();
        assertEquals(EXPECTED_BEACH_COUNT, repo.getBeaches().size());
    }
}
