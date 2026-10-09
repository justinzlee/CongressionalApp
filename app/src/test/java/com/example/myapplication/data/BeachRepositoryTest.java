package com.example.myapplication.data;

import com.example.myapplication.model.Beach;
import com.example.myapplication.model.BeachConditions;
import com.example.myapplication.model.HelpLocation;
import com.example.myapplication.model.HelpType;
import com.example.myapplication.util.UnitConverter;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    private static final int EXPECTED_BEACH_COUNT = 175;

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
     * Two pins a few hundred metres apart are one beach entered twice: the
     * list shows them as separate places, the same conditions fill both, and
     * the ranking screen puts what is really one stretch of sand in the top
     * three twice over. Redondo Beach's pin sat on Torrance County Beach until
     * Torrance was added and the two collided here.
     *
     * <p>A quarter of a mile is the loosest line that still catches that.
     * Neighbours this app does list separately - Pacific Beach and Tourmaline,
     * La Jolla Shores and the Cove - are all further apart than that.
     */
    @Test
    public void noTwoBeachesArePinnedToTheSameSand() {
        List<Beach> beaches = BeachRepository.newForTesting().getBeaches();
        for (int i = 0; i < beaches.size(); i++) {
            for (int j = i + 1; j < beaches.size(); j++) {
                Beach a = beaches.get(i);
                Beach b = beaches.get(j);
                double miles = UnitConverter.calculateDistance(
                    a.getLatitude(), a.getLongitude(), b.getLatitude(), b.getLongitude());
                assertTrue(a.getName() + " and " + b.getName() + " are pinned "
                        + Math.round(miles * 1760) + " yards apart - the same sand under two names",
                    miles >= 0.25);
            }
        }
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
     * Search still finds a beach on a phone whose locale lowercases letters
     * differently from English. Turkish is the case that bites: its rules
     * lowercase I to a dotless ı, so folding "Imperial Beach" with the default
     * locale and the typed "imperial" with nothing produced two strings that
     * no longer matched, and the beach could not be found at all.
     */
    @Test
    public void searchIgnoresCaseWhateverThePhonesLocaleIs() {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            BeachRepository repository = BeachRepository.newForTesting();
            assertFalse("Imperial Beach is not findable by lower-case search",
                repository.searchBeaches("imperial").isEmpty());
            assertFalse("Imperial Beach is not findable by upper-case search",
                repository.searchBeaches("IMPERIAL").isEmpty());
        } finally {
            Locale.setDefault(before);
        }
    }

    /**
     * Every photo comes from Wikimedia Commons. That is not a preference for
     * one host: the check that a picture is of the beach it is filed under
     * rests on the geotag and the licence Commons publishes beside the file,
     * and a URL from anywhere else can be neither checked that way nor relied
     * on to still be there next month. Coronado spent a while hotlinked to a
     * travel publisher's image CDN, which was both of those problems at once.
     */
    @Test
    public void everyPhotoComesFromWikimediaCommons() {
        for (Beach beach : BeachRepository.newForTesting().getBeaches()) {
            String url = beach.getImageUrl();
            if (url == null) continue;
            assertTrue(beach.getName() + " shows a photo from outside Wikimedia Commons, "
                    + "where its geotag and licence cannot be checked: " + url,
                url.startsWith("https://upload.wikimedia.org/wikipedia/commons/"));
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
        "RI1", "RI2", "MA1", "ME1", "ME2", "ME3", "MI1", "MI2", "WI1", "OH1", "PR1", "PR2",
        // Sourced from Wikimedia Commons by geotag, then looked at one by one:
        // a geotag on its own is not enough. Three Brück & Sohn postcards of
        // Santa Catalina are tagged on the sand at San Onofre, fifty miles up
        // the coast, and the beaches on Catalina itself still have no picture
        // for want of one that shows the sand rather than the town.
        "CA13", "CA17", "CA19", "CA23", "LA11", "LA13", "LA14", "LA15", "LA16", "LA18",
        "LA23", "LA24", "LA25", "LA26", "LA27", "CC6", "CC8", "CC10", "CC11", "CC12",
        "CC16", "CC17", "CC19", "CC20", "SF4", "SF5", "SF6", "SF7", "SF8", "SF9",
        "NCA1", "NCA2", "OR5", "WA5", "HI7", "NC5",
        // A second pass over the beaches still showing the drawn shoreline.
        // Every candidate within 700m was looked at; most were rejected —
        // a street in Mission Beach, a bike sign at Avila, the Coast Guard
        // station rather than the beach at Nauset, a salt marsh at
        // Hammonasset — and these are the ones that were actually of the
        // beach they are filed under.
        "LA1", "CA7", "CA12", "CA14", "CA15", "CA16", "CA18", "CA21", "LA17", "LA19",
        "LA20", "LA21", "CC14", "CC15", "FL14", "FL15", "NC2", "NC4", "VA1", "MA3",
        "TX3", "PR3"));

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
        find(beaches, "Refugio State Beach");           // Santa Barbara County
        find(beaches, "Morro Rock Beach");              // San Luis Obispo County
        find(beaches, "Asilomar State Beach");          // The Monterey Peninsula
        find(beaches, "Crescent Beach, Crescent City"); // The Oregon border
        find(beaches, "Avalon Beach, Catalina Island"); // The Channel Islands
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
        assertEquals(2, results.size());
        assertEquals("Carlsbad State Beach", results.get(0).getName());
        assertEquals("South Carlsbad State Beach", results.get(1).getName());

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

    /** A reading taken at a given moment, with nothing in it worth reading. */
    private static BeachConditions readingAt(long fetchedAtMillis) {
        return new BeachConditions(70.0, 3, 5.0, 1.0, 10.0, fetchedAtMillis);
    }

    private static Set<String> dueIds(BeachRepository repo, long now) {
        return repo.beachesDue(now).stream().map(Beach::getId).collect(Collectors.toSet());
    }

    /**
     * Every beach starts with nothing to show, so every beach is worth asking
     * about. Freshness used to be one window over the whole list, which meant a
     * request that came back with nothing was indistinguishable from one that
     * had just succeeded: the beaches it covered kept the "Checking conditions"
     * they started with, and nothing asked about them again for five minutes.
     * Opening one was the only thing that still fetched it.
     */
    @Test
    public void everyBeachWithoutAReadingIsDue() {
        BeachRepository repo = BeachRepository.newForTesting();
        assertEquals(EXPECTED_BEACH_COUNT, repo.beachesDue(System.currentTimeMillis()).size());
    }

    /** The beach that did land a reading is the one that is left alone. */
    @Test
    public void aBeachWithAFreshReadingIsNotAskedAboutAgain() {
        BeachRepository repo = BeachRepository.newForTesting();
        long now = System.currentTimeMillis();
        String fetched = repo.getBeaches().get(0).getId();
        repo.updateConditions(fetched, readingAt(now));

        Set<String> due = dueIds(repo, now);
        assertFalse("a beach whose reading just landed is asked about again", due.contains(fetched));
        assertEquals("the beaches that came back with nothing are still due",
            EXPECTED_BEACH_COUNT - 1, due.size());
    }

    /** And it is due again as soon as that reading ages out. */
    @Test
    public void aReadingPastTheFreshnessWindowIsDueAgain() {
        BeachRepository repo = BeachRepository.newForTesting();
        long now = System.currentTimeMillis();
        String stale = repo.getBeaches().get(0).getId();
        repo.updateConditions(stale, readingAt(now - BeachRepository.FRESH_FOR_MS));

        assertTrue(dueIds(repo, now).contains(stale));
    }

    @Test
    public void getBeaches_returnsDefensiveCopy() {
        BeachRepository repo = BeachRepository.newForTesting();
        List<Beach> beaches = repo.getBeaches();
        beaches.clear();
        assertEquals(EXPECTED_BEACH_COUNT, repo.getBeaches().size());
    }

    private static double miles(double lat, double lon, Beach b) {
        return UnitConverter.calculateDistance(lat, lon, b.getLatitude(), b.getLongitude());
    }

    /**
     * The beaches nearest the person are asked about first, so the verdict
     * card and the top of the list fill in before beaches nobody has scrolled
     * to. Every beach in the first batch is at least as near as every beach
     * left waiting.
     */
    @Test
    public void theNearestBeachesAreFetchedFirst() {
        BeachRepository repo = BeachRepository.newForTesting();
        Beach laJolla = repo.getBeaches().stream()
            .filter(b -> b.getId().equals("CA1")).findFirst().get();
        double lat = laJolla.getLatitude();
        double lon = laJolla.getLongitude();
        repo.setFocus(lat, lon);
        repo.queueForTesting(repo.getBeaches());

        List<Beach> first = repo.takeNextBatch();
        assertEquals("the first batch is small, so it lands quickly", 8, first.size());
        assertEquals("the beach you are standing at goes first", "CA1", first.get(0).getId());

        double farthestFetched = first.stream().mapToDouble(b -> miles(lat, lon, b)).max().getAsDouble();
        List<Beach> rest = repo.takeNextBatch();
        double nearestLeft = rest.stream().mapToDouble(b -> miles(lat, lon, b)).min().getAsDouble();
        assertTrue("a farther beach was fetched before a nearer one",
            farthestFetched <= nearestLeft);
    }

    /**
     * The location usually lands a second or two after the screen opens, once
     * a pass is under way. What is still waiting is then taken nearest the new
     * focus, rather than in the order the pass started with.
     */
    @Test
    public void aFocusThatArrivesMidPassReordersWhatIsLeft() {
        BeachRepository repo = BeachRepository.newForTesting();
        repo.queueForTesting(repo.getBeaches());
        Set<String> taken = repo.takeNextBatch().stream().map(Beach::getId).collect(Collectors.toSet());

        Beach elsewhere = repo.getBeaches().get(repo.getBeaches().size() - 1);
        repo.setFocus(elsewhere.getLatitude(), elsewhere.getLongitude());
        List<Beach> next = repo.takeNextBatch();

        double lat = elsewhere.getLatitude();
        double lon = elsewhere.getLongitude();
        List<Beach> expected = repo.getBeaches().stream()
            .filter(b -> !taken.contains(b.getId()))
            .sorted((a, b) -> Double.compare(miles(lat, lon, a), miles(lat, lon, b)))
            .limit(next.size())
            .collect(Collectors.toList());
        assertEquals(expected.stream().map(Beach::getId).collect(Collectors.toList()),
            next.stream().map(Beach::getId).collect(Collectors.toList()));
    }

    /** Two small batches to start, then full ones, until nothing is left. */
    @Test
    public void batchesStartSmallThenGoFull() {
        BeachRepository repo = BeachRepository.newForTesting();
        repo.queueForTesting(repo.getBeaches());
        assertEquals(8, repo.takeNextBatch().size());
        assertEquals(8, repo.takeNextBatch().size());
        assertEquals(25, repo.takeNextBatch().size());

        int left = EXPECTED_BEACH_COUNT - 8 - 8 - 25;
        while (left > 0) {
            int size = repo.takeNextBatch().size();
            assertEquals(Math.min(25, left), size);
            left -= size;
        }
        assertTrue("every beach is handed out exactly once", repo.takeNextBatch().isEmpty());
    }
}
