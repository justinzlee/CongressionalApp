package com.example.myapplication.network;

import com.example.myapplication.model.ParkingSummary;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Pins how a batched parking answer is split between beaches.
 *
 * <p>The ranking screen asks about every uncached beach in one query and has to
 * score each on the parking its own screen would show. What matters is that
 * each beach's share of a batched answer reads exactly as its own query would,
 * and that a share which might be incomplete is never read at all — whatever
 * is read here is cached for a month.
 */
public class ParkingClientTest {

    private static final String LOT_FREE = "{\"type\":\"node\",\"id\":10,\"lat\":32.86,"
        + "\"lon\":-117.25,\"tags\":{\"amenity\":\"parking\",\"fee\":\"no\"}}";
    private static final String LOT_PAID = "{\"type\":\"way\",\"id\":11,"
        + "\"center\":{\"lat\":32.86,\"lon\":-117.25},"
        + "\"tags\":{\"amenity\":\"parking\",\"fee\":\"yes\",\"capacity\":\"40\"}}";
    private static final String STREET = "{\"type\":\"way\",\"id\":12,"
        + "\"center\":{\"lat\":33.59,\"lon\":-117.87},"
        + "\"tags\":{\"amenity\":\"parking\",\"parking\":\"street_side\"}}";
    private static final String HOTEL = "{\"type\":\"node\",\"id\":13,\"lat\":33.59,"
        + "\"lon\":-117.87,\"tags\":{\"amenity\":\"parking\",\"access\":\"customers\"}}";

    private static String marker(String beach) {
        return "{\"type\":\"marker\",\"id\":1,\"tags\":{\"beach\":\"" + beach + "\"}}";
    }

    private static String body(String... elements) {
        return "{\"version\":0.6,\"elements\":[" + String.join(",", elements) + "]}";
    }

    private static void assertSameParking(ParkingSummary expected, ParkingSummary actual) {
        assertNotNull(actual);
        assertEquals(expected.getLots(), actual.getLots());
        assertEquals(expected.getFreeLots(), actual.getFreeLots());
        assertEquals(expected.getPaidLots(), actual.getPaidLots());
        assertEquals(expected.getKnownCapacity(), actual.getKnownCapacity());
        assertEquals(expected.getStreetSide(), actual.getStreetSide());
    }

    @Test
    public void eachBeachsShareReadsAsItsOwnQueryWould() {
        Map<String, ParkingSummary> batch = ParkingClient.parseBatch(body(
            marker("CA1"), LOT_FREE, LOT_PAID,
            marker("LA5"), STREET, HOTEL));

        assertSameParking(ParkingClient.parse(body(LOT_FREE, LOT_PAID)), batch.get("CA1"));
        assertSameParking(ParkingClient.parse(body(STREET, HOTEL)), batch.get("LA5"));
    }

    /** A marker with nothing after it is a real answer: no parking found there. */
    @Test
    public void aBeachWithNoParkingIsReportedAsNone() {
        Map<String, ParkingSummary> batch = ParkingClient.parseBatch(body(
            marker("CA1"), marker("LA5"), STREET));

        assertNotNull(batch.get("CA1"));
        assertEquals(0, batch.get("CA1").getLots());
        assertEquals(0, batch.get("CA1").getStreetSide());
        assertEquals(1, batch.get("LA5").getStreetSide());
    }

    /** No marker, no answer: unknown, which is not the same as "no parking". */
    @Test
    public void aBeachMissingFromTheAnswerIsLeftOut() {
        Map<String, ParkingSummary> batch =
            ParkingClient.parseBatch(body(marker("CA1"), LOT_FREE));
        assertFalse(batch.containsKey("LA5"));
    }

    /**
     * Overpass abandons a query that runs out of time with a remark rather than
     * an error status, along with whatever elements it had reached by then. The
     * last beach's share could be short, so none of it is trusted.
     */
    @Test
    public void anAnswerCutShortIsNotReadAtAll() {
        String cutShort = "{\"version\":0.6,"
            + "\"remark\":\"runtime error: Query timed out in \\\"query\\\" at line 1 after 25 seconds.\","
            + "\"elements\":[" + marker("CA1") + "," + LOT_FREE + "]}";
        assertTrue(ParkingClient.parseBatch(cutShort).isEmpty());
    }

    @Test
    public void unreadableAnswersYieldNothingRatherThanThrowing() {
        assertTrue(ParkingClient.parseBatch("<html>504 Gateway Timeout</html>").isEmpty());
        assertTrue(ParkingClient.parseBatch("").isEmpty());
        assertTrue(ParkingClient.parseBatch("{}").isEmpty());
    }

    /** Anything ahead of the first marker belongs to no beach. */
    @Test
    public void elementsBeforeAnyMarkerAreIgnored() {
        Map<String, ParkingSummary> batch =
            ParkingClient.parseBatch(body(LOT_PAID, marker("CA1"), LOT_FREE));
        assertEquals(1, batch.get("CA1").getLots());
        assertEquals(0, batch.get("CA1").getPaidLots());
    }

    /** Splitting the query in two for the batch must not have changed the single one. */
    @Test
    public void singleQueryIsTheQueryItAlwaysWas() {
        assertEquals("[out:json][timeout:25];("
                + "node[\"amenity\"=\"parking\"](around:1200,32.85890,-117.25580);"
                + "way[\"amenity\"=\"parking\"](around:1200,32.85890,-117.25580);"
                + ");out tags center 80;",
            ParkingClient.query(32.8589, -117.2558));
    }
}
