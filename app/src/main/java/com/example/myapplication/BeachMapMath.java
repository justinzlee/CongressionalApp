package com.example.myapplication;

import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The arithmetic behind {@link BeachMapCard}, kept away from the views so plain
 * unit tests can pin it: the wording on the map's two pills, where the arrow
 * pointing back at you sits once you are off the edge of the map, and the
 * character each tower's marker carries.
 */
final class BeachMapMath {

    private static final double FEET_PER_METRE = 3.28084;
    private static final double METRES_PER_MILE = 1609.344;

    /** The equator's length as web map tiles measure it, in metres. */
    private static final double EQUATOR_M = 40075016.686;

    /** "7", "14", "2A", or the same after a "Tower" someone typed in front. */
    private static final Pattern TOWER_NUMBER =
        Pattern.compile("^(?:tower\\s*)?([A-Za-z0-9]{1,2})$", Pattern.CASE_INSENSITIVE);

    private BeachMapMath() {
    }

    /**
     * Ground covered by one dp at this latitude and zoom. The map draws its
     * 256-pixel tiles at 256dp — the size the design's web map gives them in
     * CSS pixels — so a zoom level here means what it means there.
     */
    static double metresPerDp(double latitude, double zoom) {
        return EQUATOR_M * Math.abs(Math.cos(Math.toRadians(latitude))) / Math.pow(2, zoom + 8);
    }

    /**
     * How far you are from the middle of the map, for the pill under the arrow.
     * Metric is the design's own rounding: to ten metres under a kilometre, a
     * decimal place under ten, whole kilometres past that. Imperial makes the
     * same steps in feet and miles.
     */
    static String distance(double metres, boolean metric) {
        if (metric) {
            if (metres < 950) {
                return String.format(Locale.US, "%d m", Math.round(metres / 10) * 10);
            }
            return String.format(Locale.US, metres < 9500 ? "%.1f km" : "%.0f km", metres / 1000);
        }
        double feet = metres * FEET_PER_METRE;
        if (feet < 1000) {
            return String.format(Locale.US, "%d ft", Math.round(feet / 10) * 10);
        }
        double miles = metres / METRES_PER_MILE;
        return String.format(Locale.US, miles < 9.5 ? "%.1f mi" : "%.0f mi", miles);
    }

    /** The scale pill: how much ground the given stretch of map covers. */
    static String scale(double metres, boolean metric) {
        if (metric) {
            return metres < 1000
                ? String.format(Locale.US, "%d m", Math.round(metres / 10) * 10)
                : String.format(Locale.US, "%.1f km", metres / 1000);
        }
        double feet = metres * FEET_PER_METRE;
        return feet < 1000
            ? String.format(Locale.US, "%d ft", Math.round(feet / 10) * 10)
            : String.format(Locale.US, "%.1f mi", metres / METRES_PER_MILE);
    }

    /**
     * Where the arrow pointing back at you goes, or null while you are on the
     * map and your own dot is showing.
     *
     * <p>You count as on the map only while clear of its edges by
     * {@code inset}. Off it, the arrow is pushed out from the middle along the
     * line towards you until it meets a box a further {@code edgeX} and
     * {@code edgeY} in from that — room for the arrow and its pill — so it
     * sits on the side of the map you would have to walk out of.
     *
     * @return {x, y, degrees clockwise from pointing straight up}
     */
    @Nullable
    static float[] pointer(float width, float height, float userX, float userY,
                           float inset, float edgeX, float edgeY) {
        boolean onMap = userX > inset && userX < width - inset
            && userY > inset && userY < height - inset;
        if (onMap) return null;

        float cx = width / 2f;
        float cy = height / 2f;
        float dx = userX - cx;
        float dy = userY - cy;
        float halfW = cx - inset - edgeX;
        float halfH = cy - inset - edgeY;
        float t = Math.min(halfW / Math.max(Math.abs(dx), 1e-6f),
            halfH / Math.max(Math.abs(dy), 1e-6f));
        float degrees = (float) Math.toDegrees(Math.atan2(dy, dx)) + 90f;
        return new float[] {cx + dx * t, cy + dy * t, degrees};
    }

    /**
     * The character on a tower's marker. Its own number when it has one short
     * enough to fit — the number painted on the tower, which is what people
     * arrange to meet at — and otherwise the side of the beach it stands on,
     * the way the design marks its two towers N and S.
     *
     * @param ref the tower's reference or name as mapped, if any
     */
    static String towerLabel(@Nullable String ref, double towerLat, double towerLon,
                             double beachLat, double beachLon) {
        if (ref != null) {
            Matcher number = TOWER_NUMBER.matcher(ref.trim());
            if (number.matches()) return number.group(1).toUpperCase(Locale.US);
        }
        double bearing = bearing(beachLat, beachLon, towerLat, towerLon);
        if (bearing >= 315 || bearing < 45) return "N";
        if (bearing < 135) return "E";
        if (bearing < 225) return "S";
        return "W";
    }

    /** Initial bearing from one point to another, degrees clockwise from north. */
    static double bearing(double fromLat, double fromLon, double toLat, double toLon) {
        double from = Math.toRadians(fromLat);
        double to = Math.toRadians(toLat);
        double across = Math.toRadians(toLon - fromLon);
        double y = Math.sin(across) * Math.cos(to);
        double x = Math.cos(from) * Math.sin(to)
            - Math.sin(from) * Math.cos(to) * Math.cos(across);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }
}
