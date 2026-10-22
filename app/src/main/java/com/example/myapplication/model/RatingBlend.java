package com.example.myapplication.model;

/**
 * Combines a beach's real-world starting point with the rating somebody gave
 * it on this phone.
 *
 * <p>The starting point is what a public source says about the beach — how
 * many gulls eBird's birders counted, what OpenStreetMap says the sand is made
 * of — and it is the same for everyone who opens the app. A rating from
 * somebody standing on the sand is better evidence than either, so it is
 * weighted above the starting point while it is fresh, and fades back towards
 * it as it ages. How fast depends on the thing being rated: gulls follow the
 * picnickers and are gone by the weekend, the shingle moves with the winter
 * storms.
 *
 * <p>Both inputs are on the same whole-number scale, with {@code -1} for "no
 * value", which is what {@link RockRating#UNRATED} and {@link
 * SeagullRating#UNRATED} both are.
 */
public final class RatingBlend {

    public static final int NONE = -1;

    /** Gulls: a fresh rating counts twice the starting point, and half that after three days. */
    public static final double GULL_USER_WEIGHT = 2.0;
    public static final double GULL_HALF_LIFE_DAYS = 3.0;

    /**
     * Rocks: a fresh rating counts four times the starting point — the map tag
     * is drawn once for a whole stretch of shore, the rating is the sand in
     * front of you — and half that after six months.
     */
    public static final double ROCK_USER_WEIGHT = 4.0;
    public static final double ROCK_HALF_LIFE_DAYS = 180.0;

    private static final double DAY_MILLIS = 24d * 60 * 60 * 1000;

    private RatingBlend() {
    }

    /**
     * @param base          the starting point, or {@link #NONE}
     * @param user          this phone's rating, or {@link #NONE}
     * @param userAgeMillis how long ago the rating was given; a negative age,
     *                      from a phone clock set wrong, counts as fresh
     * @return the blend rounded to the scale, or {@link #NONE} when neither exists
     */
    public static int blend(int base, int user, long userAgeMillis,
                            double userWeight, double halfLifeDays) {
        if (user < 0) return base < 0 ? NONE : base;
        if (base < 0) return user;
        double weight = userWeight * userInfluence(userAgeMillis, halfLifeDays);
        return (int) Math.round((base + user * weight) / (1 + weight));
    }

    /** 1 for a rating given just now, halving every {@code halfLifeDays}. */
    static double userInfluence(long ageMillis, double halfLifeDays) {
        double days = Math.max(0, ageMillis) / DAY_MILLIS;
        return Math.pow(0.5, days / halfLifeDays);
    }
}
