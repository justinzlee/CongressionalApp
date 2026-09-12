package com.example.myapplication.network;

import com.example.myapplication.model.HelpLocation;
import com.example.myapplication.model.HelpType;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Pins the reading of an Overpass body for the beach map's towers. The shapes
 * below are the ones Overpass actually returns for the app's beaches: numbered
 * towers drawn as buildings at La Jolla, a point with its number in the name
 * at Huntington, and Cannon Beach's one tower under the old tag.
 */
public class LifeguardClientTest {

    private static final String BODY = "{\"elements\":["
        // A tower drawn as a building: a way, with Overpass's centre for it.
        + "{\"type\":\"way\",\"id\":1,\"center\":{\"lat\":32.8571,\"lon\":-117.2573},"
        + "\"tags\":{\"emergency\":\"lifeguard\",\"lifeguard\":\"tower\",\"ref\":\"33\"}},"
        // A point whose number was typed into the name instead of the ref.
        + "{\"type\":\"node\",\"id\":2,\"lat\":33.6551,\"lon\":-118.0038,"
        + "\"tags\":{\"emergency\":\"lifeguard\",\"lifeguard\":\"tower\",\"name\":\"12\"}},"
        // A headquarters with a long name and no number.
        + "{\"type\":\"way\",\"id\":3,\"center\":{\"lat\":33.6560,\"lon\":-118.0050},"
        + "\"tags\":{\"emergency\":\"lifeguard\",\"lifeguard\":\"base\","
        + "\"name\":\"City of Huntington Beach Lifeguard HQ\"}},"
        // The old tag, nothing else on it.
        + "{\"type\":\"node\",\"id\":4,\"lat\":45.8900,\"lon\":-123.9620,"
        + "\"tags\":{\"emergency\":\"lifeguard_tower\"}},"
        // A way Overpass gave no centre for can't be placed, so it is skipped.
        + "{\"type\":\"way\",\"id\":5,\"tags\":{\"emergency\":\"lifeguard\"}}"
        + "]}";

    @Test
    public void readsPointsAndBuildingsAlike() {
        List<HelpLocation> towers = LifeguardClient.parse(BODY);

        assertEquals(4, towers.size());
        assertEquals(32.8571, towers.get(0).getLatitude(), 1e-9);
        assertEquals(-117.2573, towers.get(0).getLongitude(), 1e-9);
        assertEquals(33.6551, towers.get(1).getLatitude(), 1e-9);
        assertEquals(-118.0038, towers.get(1).getLongitude(), 1e-9);
        for (HelpLocation tower : towers) {
            assertEquals(HelpType.LIFEGUARD_TOWER, tower.getType());
        }
    }

    @Test
    public void keepsTheNumberOrElseTheName() {
        List<HelpLocation> towers = LifeguardClient.parse(BODY);

        assertEquals("33", towers.get(0).getName());
        assertEquals("12", towers.get(1).getName());
        assertEquals("City of Huntington Beach Lifeguard HQ", towers.get(2).getName());
        assertEquals("", towers.get(3).getName());
    }

    @Test
    public void nothingMappedIsAnEmptyListNotAFailure() {
        List<HelpLocation> towers = LifeguardClient.parse("{\"elements\":[]}");
        assertTrue(towers.isEmpty());
    }

    @Test
    public void aBodyThatIsNotOverpassIsAFailure() {
        assertNull(LifeguardClient.parse("<html>rate limited</html>"));
        assertNull(LifeguardClient.parse("{\"remark\":\"runtime error\"}"));
    }

    @Test
    public void aQueryOverpassGaveUpOnIsAFailureNotAnEmptyBeach() {
        // Served with a 200. Read as "nothing mapped", it would be cached and
        // leave the beach without towers for a month.
        assertNull(LifeguardClient.parse("{\"elements\":[],\"remark\":"
            + "\"runtime error: Query timed out in \\\"query\\\" at line 1 after 26 seconds.\"}"));
    }

    @Test
    public void asksForTheOldTagsAsWellAsTheCurrentOne() {
        String query = LifeguardClient.query(45.8918, -123.9615);
        assertTrue(query.contains("[\"emergency\"~\"^lifeguard\"]"));
        assertTrue(query.contains("around:" + LifeguardClient.SEARCH_RADIUS_M + ",45.89180,-123.96150"));
    }
}
