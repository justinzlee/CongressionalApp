package com.example.myapplication.data;

import com.example.myapplication.model.Beach;

import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
        assertEquals(33.1506, carlsbad.getLatitude(), COORD_DELTA);
        assertEquals(-117.3551, carlsbad.getLongitude(), COORD_DELTA);

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
        assertFalse("South Beach should have a UV hazard entry", southBeach.getHazards().isEmpty());

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

        // No marine model covers the Great Lakes, so this one carries no surf
        // reading. It belongs here all the same - people drown in Lake
        // Michigan every summer.
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
