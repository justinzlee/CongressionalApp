package com.example.myapplication.data;

import com.example.myapplication.model.Beach;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pins which beaches the Alerts tab is allowed to speak for.
 *
 * <p>Two failures matter here and neither is visible from the screen. Letting a
 * beach through that is nowhere near the user is how the tab filled with
 * warnings about the other side of the country, which is what taught people to
 * ignore it. Dropping a saved beach is worse: the user said they cared about
 * that one, and a trip next week is exactly when a warning is worth having.
 */
public class AlertScopeTest {

    /** Half Moon Bay, the anchor these distances are measured from. */
    private static final double HERE_LAT = 37.4636;
    private static final double HERE_LON = -122.4457;

    private static Beach at(String id, double latitude, double longitude) {
        return new Beach(id, id, latitude, longitude, null, null, null, null);
    }

    /** Roughly 10 miles north: comfortably inside the radius. */
    private static final Beach NEAR = at("NEAR", 37.6086, -122.4457);
    /** Roughly 80 miles north: outside it, with room to spare. */
    private static final Beach FAR = at("FAR", 38.6230, -122.4457);
    /** A different coast entirely. */
    private static final Beach ACROSS_THE_COUNTRY = at("MIAMI", 25.7907, -80.1300);

    private static final List<Beach> ALL =
        Arrays.asList(NEAR, FAR, ACROSS_THE_COUNTRY);

    private static Set<String> saved(String... ids) {
        return new HashSet<>(Arrays.asList(ids));
    }

    private static List<String> idsOf(List<Beach> beaches) {
        List<String> ids = new ArrayList<>();
        for (Beach beach : beaches) ids.add(beach.getId());
        return ids;
    }

    @Test
    public void keepsBeachesWithinTheRadiusAndDropsTheRest() {
        List<Beach> scoped = AlertScope.filter(
            ALL, Collections.emptySet(), false, HERE_LAT, HERE_LON);
        assertEquals(Collections.singletonList("NEAR"), idsOf(scoped));
    }

    /** A saved beach is in scope however far away it is. */
    @Test
    public void keepsSavedBeachesAtAnyDistance() {
        List<Beach> scoped = AlertScope.filter(
            ALL, saved("MIAMI"), false, HERE_LAT, HERE_LON);
        assertEquals(Arrays.asList("NEAR", "MIAMI"), idsOf(scoped));
    }

    /** The two rules are a union, and a beach that satisfies both appears once. */
    @Test
    public void aNearbySavedBeachIsNotListedTwice() {
        List<Beach> scoped = AlertScope.filter(
            ALL, saved("NEAR"), false, HERE_LAT, HERE_LON);
        assertEquals(Collections.singletonList("NEAR"), idsOf(scoped));
    }

    /** With the setting on, being nearby stops counting for anything. */
    @Test
    public void savedOnlyDropsNearbyBeachesThatAreNotSaved() {
        List<Beach> scoped = AlertScope.filter(
            ALL, saved("MIAMI"), true, HERE_LAT, HERE_LON);
        assertEquals(Collections.singletonList("MIAMI"), idsOf(scoped));
    }

    @Test
    public void savedOnlyWithNothingSavedIsEmpty() {
        assertTrue(AlertScope.filter(ALL, Collections.emptySet(), true, HERE_LAT, HERE_LON)
            .isEmpty());
    }

    /**
     * With no fix, nothing is near anything. Falling back to every beach would
     * put the whole country's hazards back on the tab for exactly the users the
     * app knows least about.
     */
    @Test
    public void unknownLocationFallsBackToSavedBeachesAlone() {
        List<Beach> scoped = AlertScope.filter(ALL, saved("FAR"), false, null, null);
        assertEquals(Collections.singletonList("FAR"), idsOf(scoped));

        assertTrue(AlertScope.filter(ALL, Collections.emptySet(), false, null, null).isEmpty());
    }

    /** A half-known position is no position; neither half is trusted alone. */
    @Test
    public void oneMissingCoordinateCountsAsUnknown() {
        assertTrue(AlertScope.filter(ALL, Collections.emptySet(), false, HERE_LAT, null)
            .isEmpty());
        assertTrue(AlertScope.filter(ALL, Collections.emptySet(), false, null, HERE_LON)
            .isEmpty());
    }

    /**
     * The boundary is inclusive, and it is the documented 50 miles rather than
     * whatever the last edit left behind.
     */
    @Test
    public void theRadiusIsFiftyMilesAndItsEdgeIsIncluded() {
        assertEquals(50.0, AlertScope.NEARBY_MILES, 0.0);

        // A degree of latitude is ~69 miles, so these straddle 50.
        Beach justInside = at("IN", HERE_LAT + (49.0 / 69.0), HERE_LON);
        Beach justOutside = at("OUT", HERE_LAT + (51.0 / 69.0), HERE_LON);
        List<Beach> scoped = AlertScope.filter(
            Arrays.asList(justInside, justOutside), Collections.emptySet(),
            false, HERE_LAT, HERE_LON);
        assertEquals(Collections.singletonList("IN"), idsOf(scoped));
    }

    @Test
    public void handlesNoBeachesAndNoSavedSetAtAll() {
        assertTrue(AlertScope.filter(null, null, false, HERE_LAT, HERE_LON).isEmpty());
        assertTrue(AlertScope.filter(
            Collections.emptyList(), null, false, HERE_LAT, HERE_LON).isEmpty());
        // A null saved set must not throw when it is the only thing in scope.
        assertTrue(AlertScope.filter(ALL, null, true, HERE_LAT, HERE_LON).isEmpty());
    }
}
