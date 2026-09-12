package com.example.myapplication;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Pins the beach map's numbers against the design's: the same rounding on the
 * two pills, the same edge-hugging arrow, and the tower letters.
 */
public class BeachMapMathTest {

    // ── Pills ────────────────────────────────────────────────────────────

    @Test
    public void metricDistanceUsesTheDesignsSteps() {
        assertEquals("340 m", BeachMapMath.distance(343, true));
        assertEquals("1.8 km", BeachMapMath.distance(1800, true));
        assertEquals("12 km", BeachMapMath.distance(12400, true));
    }

    @Test
    public void imperialDistanceMakesTheSameStepsInFeetAndMiles() {
        assertEquals("330 ft", BeachMapMath.distance(100, false));
        assertEquals("1.1 mi", BeachMapMath.distance(1800, false));
        assertEquals("25 mi", BeachMapMath.distance(40000, false));
    }

    @Test
    public void scaleAtTheBeachZoom() {
        // 80dp at zoom 16 on the equator is 191 m.
        double span = BeachMapMath.metresPerDp(0, 16) * 80;
        assertEquals(191.1, span, 0.1);
        assertEquals("190 m", BeachMapMath.scale(span, true));
        assertEquals("630 ft", BeachMapMath.scale(span, false));
        assertEquals("1.5 km", BeachMapMath.scale(1530, true));
        assertEquals("1.0 mi", BeachMapMath.scale(1609, false));
    }

    @Test
    public void groundPerDpShrinksTowardsThePoles() {
        assertEquals(BeachMapMath.metresPerDp(0, 16) / 2,
            BeachMapMath.metresPerDp(60, 16), 1e-9);
    }

    // ── The arrow back to you ────────────────────────────────────────────

    @Test
    public void noArrowWhileYouAreOnTheMap() {
        assertNull(BeachMapMath.pointer(400, 420, 200, 210, 30, 32, 26));
        assertNull(BeachMapMath.pointer(400, 420, 31, 389, 30, 32, 26));
    }

    @Test
    public void arrowAppearsOnceYouAreWithinTheInsetOfAnEdge() {
        assertNotNull(BeachMapMath.pointer(400, 420, 29, 210, 30, 32, 26));
    }

    @Test
    public void youDueWestPutTheArrowOnTheLeftEdgePointingLeft() {
        float[] p = BeachMapMath.pointer(400, 420, -5000, 210, 30, 32, 26);
        assertEquals(30 + 32, p[0], 0.01);
        assertEquals(210, p[1], 0.01);
        // A quarter turn anticlockwise from straight up, written as three
        // clockwise; the canvas turns it the same way.
        assertEquals(270, p[2], 0.01);
    }

    @Test
    public void youDueSouthPutTheArrowOnTheBottomEdgePointingDown() {
        float[] p = BeachMapMath.pointer(400, 420, 200, 9000, 30, 32, 26);
        assertEquals(200, p[0], 0.01);
        assertEquals(420 - 30 - 26, p[1], 0.01);
        assertEquals(180, p[2], 0.01);
    }

    @Test
    public void aDiagonalMeetsWhicheverEdgeComesFirst() {
        // Up and to the right at 45°: the box reaches 138 either side of the
        // middle but 154 up and down, so the line meets its right-hand side.
        float[] p = BeachMapMath.pointer(400, 420, 1200, -790, 30, 32, 26);
        assertEquals(200 + 138, p[0], 0.01);
        assertEquals(210 - 138, p[1], 0.01);
        assertEquals(45, p[2], 0.01);
    }

    // ── Tower letters ────────────────────────────────────────────────────

    private static final double BEACH_LAT = 33.6557;
    private static final double BEACH_LON = -118.0043;

    @Test
    public void aTowersOwnNumberWins() {
        assertEquals("14", BeachMapMath.towerLabel("14", 33.66, -118.01, BEACH_LAT, BEACH_LON));
        assertEquals("2A", BeachMapMath.towerLabel("2a", 33.66, -118.01, BEACH_LAT, BEACH_LON));
        assertEquals("7", BeachMapMath.towerLabel("Tower 7", 33.66, -118.01, BEACH_LAT, BEACH_LON));
    }

    @Test
    public void withoutOneItIsTheSideOfTheBeach() {
        assertEquals("N", BeachMapMath.towerLabel(null, 33.6600, BEACH_LON, BEACH_LAT, BEACH_LON));
        assertEquals("S", BeachMapMath.towerLabel("", 33.6500, BEACH_LON, BEACH_LAT, BEACH_LON));
        assertEquals("E", BeachMapMath.towerLabel(null, BEACH_LAT, -117.9900, BEACH_LAT, BEACH_LON));
        assertEquals("W", BeachMapMath.towerLabel(
            "City of Huntington Beach Lifeguard HQ", BEACH_LAT, -118.0200, BEACH_LAT, BEACH_LON));
    }

    @Test
    public void laJollaShoresStationSitsSouthOfTheBeachPin() {
        assertEquals("S", BeachMapMath.towerLabel("La Jolla Shores Lifeguard Station",
            32.8562, -117.2569, 32.8589, -117.2558));
    }
}
