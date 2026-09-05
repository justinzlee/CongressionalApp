package com.example.myapplication.util;

/**
 * Works out whether the sun is above the horizon at a given place, right now.
 *
 * <p>This is deliberately arithmetic rather than a network call. The one thing
 * it is used for — choosing between the daylight and after-dark drawings of a
 * beach — has to be decided the instant a row is bound, before any request has
 * come back, and it has to keep working with the phone offline. The standard
 * sunrise equation gets within about a minute of the real thing anywhere
 * outside the polar circles, which is far closer than a picture needs.
 */
public final class SunClock {

    /** The sun's centre is this far below the horizon at official sunrise. */
    private static final double SUNRISE_ANGLE_DEGREES = -0.833;
    private static final double EARTH_TILT_DEGREES = 23.44;
    private static final double MILLIS_PER_DAY = 86400000.0;
    /** Julian date of the Unix epoch, for turning millis into Julian days. */
    private static final double UNIX_EPOCH_AS_JULIAN = 2440587.5;
    /** Julian date of J2000.0, the epoch the sunrise equation is written for. */
    private static final double J2000 = 2451545.0;

    private SunClock() {}

    /**
     * True when the sun is up at the given coordinates at the given instant.
     *
     * <p>Inside the Arctic and Antarctic circles there are days with no sunrise
     * and days with no sunset; those answer false and true for the whole day
     * respectively, which is the honest result rather than an error.
     */
    public static boolean isDaylightAt(double latitude, double longitude, long atMillis) {
        double julianNow = atMillis / MILLIS_PER_DAY + UNIX_EPOCH_AS_JULIAN;

        // The solar day this instant belongs to, which is the local one, not
        // the UTC one: at a west-coast beach the evening is already tomorrow in
        // UTC, and taking the UTC date there would compare the time against
        // tomorrow's sunrise and call a sunlit evening night.
        double dayNumber = Math.round(julianNow - J2000 + longitude / 360.0 + 0.0008);
        double meanSolarNoon = dayNumber - longitude / 360.0;

        double meanAnomaly = Math.toRadians((357.5291 + 0.98560028 * meanSolarNoon) % 360.0);
        double centre = 1.9148 * Math.sin(meanAnomaly)
            + 0.0200 * Math.sin(2 * meanAnomaly)
            + 0.0003 * Math.sin(3 * meanAnomaly);
        double eclipticLongitude =
            Math.toRadians((Math.toDegrees(meanAnomaly) + centre + 282.9372) % 360.0);

        double transit = J2000 + meanSolarNoon
            + 0.0053 * Math.sin(meanAnomaly)
            - 0.0069 * Math.sin(2 * eclipticLongitude);

        double declination =
            Math.asin(Math.sin(eclipticLongitude) * Math.sin(Math.toRadians(EARTH_TILT_DEGREES)));
        double lat = Math.toRadians(latitude);
        double cosHourAngle =
            (Math.sin(Math.toRadians(SUNRISE_ANGLE_DEGREES)) - Math.sin(lat) * Math.sin(declination))
                / (Math.cos(lat) * Math.cos(declination));

        if (cosHourAngle >= 1) return false;  // polar night: the sun never clears the horizon
        if (cosHourAngle <= -1) return true;  // midnight sun: it never drops below it

        double halfDay = Math.toDegrees(Math.acos(cosHourAngle)) / 360.0;
        return julianNow >= transit - halfDay && julianNow < transit + halfDay;
    }
}
