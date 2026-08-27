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
        List<Beach> beaches = new BeachRepository().getBeaches();
        assertEquals(17, beaches.size());
    }

    @Test
    public void sanDiegoBeaches_presentWithCorrectCoordinates() {
        List<Beach> beaches = new BeachRepository().getBeaches();

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
        List<Beach> beaches = new BeachRepository().getBeaches();

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
        List<Beach> beaches = new BeachRepository().getBeaches();

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
        List<Beach> beaches = new BeachRepository().getBeaches();
        long distinctIds = beaches.stream().map(Beach::getId).distinct().count();
        assertEquals(beaches.size(), distinctIds);
    }

    @Test
    public void search_isCaseInsensitiveSubstringMatch() {
        BeachRepository repo = new BeachRepository();
        List<Beach> results = repo.searchBeaches("carlsbad");
        assertEquals(1, results.size());
        assertEquals("Carlsbad State Beach", results.get(0).getName());

        List<Beach> mixedCase = repo.searchBeaches("CoRoNaDo");
        assertEquals(1, mixedCase.size());
        assertEquals("Coronado Beach", mixedCase.get(0).getName());
    }

    @Test
    public void search_emptyOrNullQuery_returnsAllBeaches() {
        BeachRepository repo = new BeachRepository();
        assertEquals(17, repo.searchBeaches("").size());
        assertEquals(17, repo.searchBeaches(null).size());
    }

    @Test
    public void search_noMatch_returnsEmptyList() {
        BeachRepository repo = new BeachRepository();
        assertTrue(repo.searchBeaches("NoSuchBeachXYZ").isEmpty());
    }

    @Test
    public void getBeaches_returnsDefensiveCopy() {
        BeachRepository repo = new BeachRepository();
        List<Beach> beaches = repo.getBeaches();
        beaches.clear();
        assertEquals(17, repo.getBeaches().size());
    }
}
