package com.example.myapplication.model;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import com.example.myapplication.R;

/**
 * The nought-to-ten scale for how rocky the sand is underfoot.
 *
 * <p>Every other number on this app's beach screens comes off a forecast API.
 * This one cannot: no marine forecast reports whether the shore is shingle or
 * soft sand, it changes with the season as storms move material around, and it
 * is the difference between a beach you can walk barefoot and one where a
 * six-year-old needs shoes. The only source for it is somebody standing on the
 * sand, which is why this scale exists and why rating it is a thing users do
 * rather than a thing the app fetches.
 *
 * <p>A bare number out of ten means nothing on its own — one person's 4 is
 * another's 7 — so the app never asks for it cold. Three drawn reference
 * pictures anchor the scale at {@link #REFERENCE_LOW 3}, {@link #REFERENCE_MID
 * 5} and {@link #REFERENCE_HIGH 9}, and each band carries a written label and a
 * line about what it means for the person reading it. The pictures are the
 * calibration; the words are what stops the number drifting.
 *
 * <p>The class holds no state. A rating is a plain {@code int} in a store, and
 * everything worth knowing about it is derived here, so the meaning of "7" is
 * decided in exactly one place and the plain-JVM tests can check it.
 */
public final class RockRating {

    /** Nobody can rate below this: it is the "no rocks at all" end of the scale. */
    public static final int MIN = 0;

    /** Ten, and the top of the scale the reference pictures are drawn against. */
    public static final int MAX = 10;

    /**
     * Where the scale starts unrated. Stored ratings are {@link #MIN}..{@link
     * #MAX}, so a sentinel outside that range is what "nobody has said" looks
     * like — distinct from a genuine 0, which is a real answer meaning clean
     * sand.
     */
    public static final int UNRATED = -1;

    /** The three points the drawn reference pictures illustrate. */
    public static final int REFERENCE_LOW = 3;
    public static final int REFERENCE_MID = 5;
    public static final int REFERENCE_HIGH = 9;

    /**
     * At and above this, the rating is worth raising as a hazard rather than
     * leaving as a note on the beach screen.
     *
     * <p>Seven is where the drawn scale stops being "watch your step" and starts
     * being ground you cannot cross barefoot or land a boat on. Below it the
     * rocks are an inconvenience and the beach screen says so in passing;
     * putting every 4 into the Alerts tab would bury genuine surf warnings under
     * shingle complaints.
     */
    public static final int HAZARD_FROM = 7;

    private RockRating() {
    }

    /** True for a real rating, false for {@link #UNRATED} or anything corrupt. */
    public static boolean isRated(int rating) {
        return rating >= MIN && rating <= MAX;
    }

    /**
     * Forces a number onto the scale. The slider cannot produce anything else,
     * but a rating also comes back out of stored preferences, which a restored
     * backup or a future version of this app could have written anything into.
     */
    public static int clamp(int rating) {
        if (rating < MIN) return MIN;
        return Math.min(rating, MAX);
    }

    /**
     * The band's name: "A few rocks", "Mostly rock". Five bands over eleven
     * numbers, because the eye cannot tell a 5 from a 6 in a photograph and
     * pretending otherwise would give the scale a precision it has not got.
     */
    @StringRes
    public static int labelRes(int rating) {
        switch (band(rating)) {
            case 0: return R.string.rocks_band_clear;
            case 1: return R.string.rocks_band_few;
            case 2: return R.string.rocks_band_patchy;
            case 3: return R.string.rocks_band_mostly;
            default: return R.string.rocks_band_throughout;
        }
    }

    /**
     * What the band means for the person about to walk on it — shoes, small
     * children, bare feet. This is the half of the rating that is actually
     * useful; the number is just how it gets compared between beaches.
     */
    @StringRes
    public static int adviceRes(int rating) {
        switch (band(rating)) {
            case 0: return R.string.rocks_advice_clear;
            case 1: return R.string.rocks_advice_few;
            case 2: return R.string.rocks_advice_patchy;
            case 3: return R.string.rocks_advice_mostly;
            default: return R.string.rocks_advice_throughout;
        }
    }

    /**
     * Which of the three drawn references this rating sits nearest, so the
     * rating screen can highlight the picture the user is agreeing with as they
     * move the slider. Ties go to the lower picture: between the 3 and the 5, a
     * 4 is better anchored by the sparser image, because the eye reads added
     * rock more readily than absent rock.
     */
    public static int nearestReference(int rating) {
        int value = clamp(rating);
        if (value <= (REFERENCE_LOW + REFERENCE_MID) / 2) return REFERENCE_LOW;
        if (value <= (REFERENCE_MID + REFERENCE_HIGH) / 2) return REFERENCE_MID;
        return REFERENCE_HIGH;
    }

    /** The drawn beach for one of the three reference points. */
    @DrawableRes
    public static int referenceImageRes(int reference) {
        if (reference == REFERENCE_LOW) return R.drawable.ic_rocks_scale_3;
        if (reference == REFERENCE_MID) return R.drawable.ic_rocks_scale_5;
        return R.drawable.ic_rocks_scale_9;
    }

    /** The caption under a reference picture: what that number looks like. */
    @StringRes
    public static int referenceCaptionRes(int reference) {
        if (reference == REFERENCE_LOW) return R.string.rocks_reference_3;
        if (reference == REFERENCE_MID) return R.string.rocks_reference_5;
        return R.string.rocks_reference_9;
    }

    /**
     * Whether this rating belongs in "What to watch for" and the Alerts tab.
     *
     * <p>Never more than {@link Severity#MODERATE} when it does: rock underfoot
     * is a sprained ankle and a cut sole, not a drowning, and the one rule the
     * hazard list follows is that its colours mean the same thing everywhere. A
     * red card for shingle sitting next to a red card for six-foot surf would
     * teach people to discount both.
     */
    public static boolean isHazard(int rating) {
        return isRated(rating) && rating >= HAZARD_FROM;
    }

    /**
     * A starting point read from what OpenStreetMap says the beach is made of,
     * or {@link #UNRATED} for a surface it does not name or one this cannot
     * place on the scale.
     *
     * <p>Deliberately short of the ends of the scale. A map tag covers the
     * whole drawn beach, so "rock" says the stretch is rocky, not that there is
     * no sand anywhere on it, and "sand" leaves room for the rocks at either
     * end. A tag listing several surfaces ({@code sand;pebbles}) sits in the
     * middle.
     */
    public static int forSurface(String surface) {
        if (surface == null) return UNRATED;
        String value = surface.trim().toLowerCase(java.util.Locale.ROOT);
        if (value.isEmpty()) return UNRATED;
        if (value.contains(";")) return 5;
        switch (value) {
            case "sand":
            case "fine_sand":
            case "dirt":
            case "earth":
            case "ground":
                return 1;
            case "gravel":
            case "fine_gravel":
            case "compacted":
                return 5;
            case "pebbles":
            case "pebblestone":
            case "shingle":
            case "shells":
                return 6;
            case "rock":
            case "rocks":
            case "stone":
            case "bare_rock":
                return 8;
            default:
                return UNRATED;
        }
    }

    /** 0-4, the band a rating falls in. Private: the bands are an implementation detail. */
    private static int band(int rating) {
        int value = clamp(rating);
        if (value <= 2) return 0;
        if (value <= 4) return 1;
        if (value <= 6) return 2;
        if (value <= 8) return 3;
        return 4;
    }
}
