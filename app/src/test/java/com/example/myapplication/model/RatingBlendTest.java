package com.example.myapplication.model;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

/**
 * How a real-world starting point and a rating from this phone combine.
 *
 * <p>The rule the user sees: everyone starts from the same public data, a
 * fresh rating from somebody standing on the sand outweighs it, and as that
 * rating ages the beach drifts back to what the public data says.
 */
public class RatingBlendTest {

    private static final long DAY = TimeUnit.DAYS.toMillis(1);

    private static int gulls(int base, int user, long age) {
        return RatingBlend.blend(base, user, age,
            RatingBlend.GULL_USER_WEIGHT, RatingBlend.GULL_HALF_LIFE_DAYS);
    }

    private static int rocks(int base, int user, long age) {
        return RatingBlend.blend(base, user, age,
            RatingBlend.ROCK_USER_WEIGHT, RatingBlend.ROCK_HALF_LIFE_DAYS);
    }

    @Test
    public void eitherSideAloneIsTakenAsItIs() {
        assertEquals(RatingBlend.NONE, gulls(-1, -1, 0));
        assertEquals(2, gulls(2, -1, 0));
        assertEquals(3, gulls(-1, 3, 30 * DAY));
    }

    /** eBird says plenty, somebody there now says lots: lots wins while it is fresh. */
    @Test
    public void aFreshGullRatingOutweighsTheCount() {
        assertEquals(3, gulls(2, 3, 0));
        assertEquals(1, gulls(4, 0, 0));
    }

    /** A week on, the same rating has faded and the count is back in charge. */
    @Test
    public void anOldGullRatingFadesBackToTheCount() {
        assertEquals(2, gulls(2, 3, 7 * DAY));
        assertEquals(4, gulls(4, 0, 30 * DAY));
    }

    /** The map calls the beach rock; somebody standing on it says clear sand. */
    @Test
    public void aRockRatingFromTheSandOutweighsTheMap() {
        assertEquals(3, rocks(8, 2, 0));
        // Still mostly theirs after a month: shingle does not move that fast.
        assertEquals(3, rocks(8, 2, 30 * DAY));
        // Two years on, the map has most of it back.
        assertEquals(7, rocks(8, 2, 730 * DAY));
    }

    /** A phone clock set wrong must not give a rating more than full weight. */
    @Test
    public void aRatingFromTheFutureCountsAsFresh() {
        assertEquals(gulls(2, 3, 0), gulls(2, 3, -5 * DAY));
    }

    @Test
    public void gullCountsLandOnTheScale() {
        assertEquals(SeagullRating.NONE, SeagullRating.levelForCount(0));
        assertEquals(1, SeagullRating.levelForCount(3));
        assertEquals(2, SeagullRating.levelForCount(25));
        assertEquals(3, SeagullRating.levelForCount(26));
        assertEquals(SeagullRating.MAX, SeagullRating.levelForCount(400));
    }

    @Test
    public void mapSurfacesLandOnTheRockScale() {
        assertEquals(1, RockRating.forSurface("sand"));
        assertEquals(5, RockRating.forSurface("sand;pebbles"));
        assertEquals(6, RockRating.forSurface("Shingle"));
        assertEquals(8, RockRating.forSurface("rock"));
        assertEquals(RockRating.UNRATED, RockRating.forSurface(""));
        assertEquals(RockRating.UNRATED, RockRating.forSurface("concrete"));
        assertEquals(RockRating.UNRATED, RockRating.forSurface(null));
    }
}
