package com.example.myapplication.model;

import androidx.annotation.VisibleForTesting;

/**
 * How far the Tijuana River sewage plume reaches up the San Diego coast.
 *
 * <p>Cross-border flows come out of the river mouth just north of the border
 * and the longshore current carries them north, so which beaches are affected
 * is a question about distance from one point rather than a list of names. That
 * is how this is modelled: a beach's exposure is computed from where it is, so
 * adding a beach to the app puts it in the right band automatically and nothing
 * here has to be kept in step with {@code BeachRepository}.
 *
 * <p>The bands are deliberately conservative in different directions. The inner
 * one carries a standing advisory whatever the weather, because the far south
 * county beaches are under one for most of the year and telling a swimmer there
 * that the water is fine on a dry day would be wrong. The outer one only
 * matters once it has rained, because that is when the flow volume is high
 * enough to carry that far and it is the rain, not the distance, doing the
 * work.
 *
 * <p>This is a prediction from geography, not a measurement. Nothing here reads
 * a bacteria count — see {@link WaterQuality}, which says the same thing to the
 * user in words.
 */
public final class TijuanaPlume {

    /**
     * The river mouth, where the flow enters the sea: Border Field State Park,
     * a little north of the international boundary.
     */
    @VisibleForTesting static final double MOUTH_LAT = 32.5545;
    @VisibleForTesting static final double MOUTH_LON = -117.1305;

    /**
     * Inside this, a beach is under the standing advisory. Ten kilometres
     * covers the beaches that close most often - Border Field, Imperial Beach
     * and the south end of Silver Strand.
     */
    @VisibleForTesting static final double CHRONIC_KM = 10.0;

    /**
     * Out to here, the plume is a rain-day problem rather than a daily one.
     * Twenty kilometres reaches Coronado, which is advised against after
     * storms and is otherwise usually fine.
     */
    @VisibleForTesting static final double RAIN_DRIVEN_KM = 20.0;

    /** North of the mouth only: the current runs that way. */
    public enum Reach {
        /** Far enough away, or on another coast entirely. */
        NONE,
        /** Affected once it has rained. */
        RAIN_DRIVEN,
        /** Under advisory whatever the weather. */
        CHRONIC
    }

    private TijuanaPlume() {
    }

    public static Reach reachFor(Beach beach) {
        if (beach == null) return Reach.NONE;

        // South of the mouth is Mexico, and the current does not run that way;
        // a beach below the river is not this app's problem to warn about.
        if (beach.getLatitude() < MOUTH_LAT) return Reach.NONE;

        double km = kilometresBetween(
            MOUTH_LAT, MOUTH_LON, beach.getLatitude(), beach.getLongitude());
        if (km <= CHRONIC_KM) return Reach.CHRONIC;
        if (km <= RAIN_DRIVEN_KM) return Reach.RAIN_DRIVEN;
        return Reach.NONE;
    }

    /** Great-circle distance in kilometres. */
    @VisibleForTesting
    static double kilometresBetween(double lat1, double lon1, double lat2, double lon2) {
        final double earthRadiusKm = 6371.0088;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * earthRadiusKm * Math.asin(Math.min(1, Math.sqrt(a)));
    }
}
