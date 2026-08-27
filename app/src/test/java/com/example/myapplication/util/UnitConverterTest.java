package com.example.myapplication.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Validates UnitConverter.calculateDistance (Haversine) against known geometric
 * facts, independent of any app-specific data, so a broken formula (e.g. a
 * lat/lon swap or degrees/radians mistake) shows up immediately.
 */
public class UnitConverterTest {

    private static final double DELTA = 0.5; // miles

    @Test
    public void sameCoordinates_zeroDistance() {
        assertEquals(0.0, UnitConverter.calculateDistance(32.8589, -117.2558, 32.8589, -117.2558), 1e-9);
    }

    @Test
    public void oneDegreeOfLongitudeAtEquator_isAbout69Miles() {
        // 1 degree of longitude at the equator is a well-known constant (~69.09 mi
        // given Earth radius 3958.8 mi), and doesn't depend on any beach data.
        double d = UnitConverter.calculateDistance(0, 0, 0, 1);
        assertEquals(69.09, d, DELTA);
    }

    @Test
    public void oneDegreeOfLatitude_isAbout69Miles() {
        // Meridians are all great circles, so 1 degree of latitude is the same
        // distance anywhere on Earth (~69.09 mi).
        double d = UnitConverter.calculateDistance(0, 0, 1, 0);
        assertEquals(69.09, d, DELTA);
    }

    @Test
    public void distanceIsSymmetric() {
        double a = UnitConverter.calculateDistance(32.8589, -117.2558, 25.7826, -80.1285);
        double b = UnitConverter.calculateDistance(25.7826, -80.1285, 32.8589, -117.2558);
        assertEquals(a, b, 1e-9);
    }

    @Test
    public void nearbyPoints_areCloserThanFarPoints() {
        // La Jolla Shores (San Diego) vs. Carlsbad State Beach (~30mi away) vs.
        // South Beach, Miami (cross-country) from the same reference point.
        double reference_lat = 32.7157, reference_lon = -117.1611; // San Diego downtown
        double toCarlsbad = UnitConverter.calculateDistance(reference_lat, reference_lon, 33.1506, -117.3551);
        double toMiami = UnitConverter.calculateDistance(reference_lat, reference_lon, 25.7826, -80.1285);
        assertTrue("Carlsbad should be far closer to San Diego than Miami is", toCarlsbad < toMiami);
    }
}
