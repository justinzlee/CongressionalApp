package com.example.myapplication.model;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.example.myapplication.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Works out how well a beach suits each activity today.
 *
 * <p>Every activity is a weighted blend of the same handful of readings, scored
 * through {@link #band}: each reading is given the range that is ideal for that
 * activity and the range outside which it stops mattering, and falls off
 * linearly between the two. The weights are what differ, and they are the whole
 * opinion of this class — so they are written out one activity at a time,
 * with the reason for each, rather than hidden in a table.
 *
 * <p>Two rules hold across all four. A reading the app does not have is left out
 * of the blend entirely rather than scored zero, because unknown surf is not bad
 * surf. And no activity is allowed to score well on a beach the safety side of
 * the app is warning about — see {@link #hazardCeiling}, which caps the result
 * rather than averaging the danger away.
 */
public final class ActivityScorer {

    private ActivityScorer() {
    }

    /** Everything the scorer can look at. Any field may be null. */
    public static final class Inputs {
        @Nullable public BeachConditions conditions;
        @Nullable public AirQuality air;
        @Nullable public WaterQuality water;
        @Nullable public ParkingSummary parking;
        /** {@link RockRating#UNRATED} when nobody has rated the shore. */
        public int rockRating = RockRating.UNRATED;

        /**
         * Whether a lifeguard tower is listed for the beach, or null when the
         * app has no help data for it at all.
         *
         * <p>The null case is the common one and the distinction matters. Most
         * beaches in the list carry no {@code nearestHelp} entries, which means
         * nobody recorded any — not that the beach is unpatrolled. Reading an
         * empty list as "unguarded" scored every one of those beaches down by
         * the same amount, which both flattened the ranking and made "held back
         * by the lack of lifeguards" the reason on every single row.
         */
        @Nullable public Boolean guarded;
        /** The worst severity currently on the beach's hazard list, or null. */
        @Nullable public Severity worstHazard;
    }

    public static ActivityFit score(Activity activity, Beach beach, Inputs in) {
        List<ActivityFit.Factor> factors;
        switch (activity) {
            case SURF:   factors = surf(in); break;
            case PICNIC: factors = picnic(in); break;
            case FISH:   factors = fish(in); break;
            default:     factors = swim(in); break;
        }
        ActivityFit fit = ActivityFit.of(activity, beach, factors);
        return capped(fit, activity, beach, in);
    }

    /** All four, for the beach screen. */
    public static List<ActivityFit> scoreAll(Beach beach, Inputs in) {
        List<ActivityFit> all = new ArrayList<>();
        for (Activity activity : Activity.values()) {
            all.add(score(activity, beach, in));
        }
        return all;
    }

    // ── Swimming and body-surfing ────────────────────────────────────────
    //
    // Water quality outweighs everything, because it is the one factor here
    // that can actually make somebody ill, and unlike surf height it is
    // invisible from the sand. Surf is wanted but only a little of it: enough
    // to body-surf, not enough to hold anyone under. A patrolled beach gets
    // real credit - it is the single biggest difference to how a bad moment in
    // the water ends.

    private static List<ActivityFit.Factor> swim(Inputs in) {
        List<ActivityFit.Factor> factors = new ArrayList<>();

        if (in.water != null) {
            factors.add(new ActivityFit.Factor(
                R.string.factor_water, in.water.cleanlinessScore(), 3.0));
        }
        if (in.conditions != null && in.conditions.getWaveHeight() != null) {
            // Ideal 1-3ft: something to ride, nothing to fight. Zero by 8ft.
            factors.add(new ActivityFit.Factor(R.string.factor_surf,
                band(in.conditions.getWaveHeight(), 1.0, 3.0, 0.0, 8.0), 2.5));
        }
        if (in.conditions != null && in.conditions.getAirTemperature() != null) {
            factors.add(new ActivityFit.Factor(R.string.factor_warmth,
                band(in.conditions.getAirTemperature(), 76, 92, 58, 104), 1.5));
        }
        if (in.conditions != null && in.conditions.getWindSpeed() != null) {
            factors.add(new ActivityFit.Factor(R.string.factor_wind,
                band(in.conditions.getWindSpeed(), 0, 10, -1, 28), 1.0));
        }
        if (in.guarded != null) {
            factors.add(new ActivityFit.Factor(
                R.string.factor_lifeguards, in.guarded ? 100 : 45, 1.5));
        }
        if (RockRating.isRated(in.rockRating)) {
            // Shingle underfoot is a real problem walking into the water.
            factors.add(new ActivityFit.Factor(R.string.factor_underfoot,
                100 - (in.rockRating * 10), 1.0));
        }
        return factors;
    }

    // ── Surfing ──────────────────────────────────────────────────────────
    //
    // The one activity that wants big numbers. Surf is weighted hardest and its
    // ideal band sits where the rest of the app starts calling conditions
    // hazardous, which is correct and deliberate: a surfer reading this screen
    // wants to know the waves are up, and the safety verdict above it is still
    // there saying what that means. Wind matters more than for anything else
    // because it is what turns a clean face into slop.

    private static List<ActivityFit.Factor> surf(Inputs in) {
        List<ActivityFit.Factor> factors = new ArrayList<>();

        if (in.conditions != null && in.conditions.getWaveHeight() != null) {
            // Under a foot there is nothing to ride; over about twelve it is
            // no longer a question this app should be encouraging.
            factors.add(new ActivityFit.Factor(R.string.factor_surf,
                band(in.conditions.getWaveHeight(), 3.0, 8.0, 0.5, 14.0), 3.5));
        }
        if (in.conditions != null && in.conditions.getWindSpeed() != null) {
            factors.add(new ActivityFit.Factor(R.string.factor_wind,
                band(in.conditions.getWindSpeed(), 0, 8, -1, 25), 2.0));
        }
        if (in.water != null) {
            // Lower weight than swimming, not because surfers are safe from it
            // but because they are in the water briefly and mostly above it.
            factors.add(new ActivityFit.Factor(
                R.string.factor_water, in.water.cleanlinessScore(), 1.5));
        }
        if (in.conditions != null && in.conditions.getAirTemperature() != null) {
            factors.add(new ActivityFit.Factor(R.string.factor_warmth,
                band(in.conditions.getAirTemperature(), 68, 92, 48, 104), 0.8));
        }
        if (in.parking != null) {
            factors.add(new ActivityFit.Factor(
                R.string.factor_parking, in.parking.easeScore(), 0.8));
        }
        return factors;
    }

    // ── Picnicking ───────────────────────────────────────────────────────
    //
    // The only one of the four that happens entirely on the sand, so the water
    // barely features and the air does all the work. Wind is weighted hardest
    // because it is what actually ruins a picnic - it gets into the food and
    // takes the umbrella - and because nothing else on the beach fixes it.
    // Parking counts here more than anywhere else: this is the activity people
    // arrive at carrying a cool box and a folding chair.

    private static List<ActivityFit.Factor> picnic(Inputs in) {
        List<ActivityFit.Factor> factors = new ArrayList<>();

        if (in.conditions != null && in.conditions.getWindSpeed() != null) {
            factors.add(new ActivityFit.Factor(R.string.factor_wind,
                band(in.conditions.getWindSpeed(), 0, 8, -1, 24), 3.0));
        }
        if (in.conditions != null && in.conditions.getAirTemperature() != null) {
            factors.add(new ActivityFit.Factor(R.string.factor_warmth,
                band(in.conditions.getAirTemperature(), 70, 84, 52, 100), 2.5));
        }
        if (in.air != null) {
            factors.add(new ActivityFit.Factor(
                R.string.factor_air, in.air.outdoorScore(), 2.0));
        }
        if (in.parking != null) {
            factors.add(new ActivityFit.Factor(
                R.string.factor_parking, in.parking.easeScore(), 1.5));
        }
        if (in.conditions != null && in.conditions.getUvIndex() != null) {
            // Not a reason to stay home, but a reason to want shade, so it is
            // present and light rather than absent.
            factors.add(new ActivityFit.Factor(R.string.factor_uv,
                band(in.conditions.getUvIndex(), 0, 6, -1, 12), 1.0));
        }
        return factors;
    }

    // ── Fishing ──────────────────────────────────────────────────────────
    //
    // The one activity that wants a rocky beach. Structure holds fish, so the
    // rocks rating people file from the shore is read the opposite way round
    // here from how swimming reads it - the same number, inverted, which is a
    // good sign the scale is measuring something real. Heavy surf is the limit:
    // it makes casting pointless and the rocks lethal.

    private static List<ActivityFit.Factor> fish(Inputs in) {
        List<ActivityFit.Factor> factors = new ArrayList<>();

        if (RockRating.isRated(in.rockRating)) {
            factors.add(new ActivityFit.Factor(R.string.factor_structure,
                40 + (in.rockRating * 6), 2.0));
        }
        if (in.conditions != null && in.conditions.getWaveHeight() != null) {
            // A little movement stirs up bait; a lot ends the day.
            factors.add(new ActivityFit.Factor(R.string.factor_surf,
                band(in.conditions.getWaveHeight(), 1.0, 4.0, 0.0, 10.0), 2.0));
        }
        if (in.conditions != null && in.conditions.getWindSpeed() != null) {
            factors.add(new ActivityFit.Factor(R.string.factor_wind,
                band(in.conditions.getWindSpeed(), 0, 12, -1, 30), 2.0));
        }
        if (in.water != null) {
            // Lower than swimming: you are not in it. Not zero: people eat what
            // they catch, and a sewage advisory is a reason not to.
            factors.add(new ActivityFit.Factor(
                R.string.factor_water, in.water.cleanlinessScore(), 1.2));
        }
        if (in.parking != null) {
            factors.add(new ActivityFit.Factor(
                R.string.factor_parking, in.parking.easeScore(), 0.8));
        }
        return factors;
    }

    /**
     * Stops an activity scoring well on a beach the rest of the app is warning
     * about.
     *
     * <p>A cap rather than another factor in the blend. A weighted average lets
     * a high-surf warning be cancelled out by good parking and warm air, which
     * is exactly the arithmetic that would put "Great for swimming" over a beach
     * flying a red flag. A ceiling cannot be averaged away.
     *
     * <p>Surfing is exempt from the high-surf ceiling specifically, since big
     * waves are the point — but not from anything else, and not from the
     * extreme one.
     */
    private static ActivityFit capped(ActivityFit fit, Activity activity, Beach beach, Inputs in) {
        int ceiling = hazardCeiling(activity, in.worstHazard);
        // The weight only decides that this outranks the real factors when the
        // screen asks what held the score back; the ceiling itself is a hard
        // minimum applied by withCeiling.
        return fit.withCeiling(ceiling,
            new ActivityFit.Factor(R.string.factor_hazards, ceiling, 99.0));
    }

    /**
     * The most an activity may score given the worst hazard on the beach.
     * Package-private so the tests can pin it without building a whole beach.
     */
    @VisibleForTesting
    static int hazardCeiling(Activity activity, @Nullable Severity worst) {
        if (worst == null) return 100;
        switch (worst) {
            case EXTREME: return 15;
            case HIGH: return activity == Activity.SURF ? 70 : 35;
            case MODERATE: return 75;
            default: return 100;
        }
    }

    /**
     * How much of the ideal range's score is given up at its edges. The ideal
     * band runs from {@code 100 - IDEAL_FALLOFF} at its bounds to 100 dead
     * centre.
     */
    private static final int IDEAL_FALLOFF = 12;

    /**
     * Scores a reading against the range that suits an activity: near-full
     * marks anywhere inside [{@code idealLow}, {@code idealHigh}], peaking at
     * its centre, then falling away to 0 at {@code zeroLow} and {@code
     * zeroHigh}.
     *
     * <p>The ideal range was originally a flat plateau, on the reasoning that
     * two and three feet of surf are equally good to body-surf and a curve
     * would rank one above the other for no reason anybody on the sand could
     * tell. Run against real data that turned out to be badly wrong: on a calm
     * San Diego afternoon every reading at every beach lands inside its ideal
     * range, so eleven beaches all scored exactly 100 and the ranking ordered
     * nothing. A scale with no headroom cannot rank.
     *
     * <p>So the ideal range now slopes gently — a dome, not a plateau, giving
     * up {@link #IDEAL_FALLOFF} points at its edges. That is small enough that
     * it never claims two-foot surf is meaningfully worse than three, and large
     * enough to order a coast on a day when everywhere is genuinely pleasant.
     * A beach only reads 100 when a reading sits dead centre of what the
     * activity wants, which is rare and is the point.
     */
    @VisibleForTesting
    static int band(double value, double idealLow, double idealHigh,
                    double zeroLow, double zeroHigh) {
        if (value >= idealLow && value <= idealHigh) {
            double halfWidth = (idealHigh - idealLow) / 2;
            if (halfWidth <= 0) return 100;
            double centre = idealLow + halfWidth;
            double offset = Math.abs(value - centre) / halfWidth;
            return (int) Math.round(100 - IDEAL_FALLOFF * offset);
        }
        // Outside the ideal range, fall from its edge value to nothing.
        int edge = 100 - IDEAL_FALLOFF;
        if (value < idealLow) {
            if (value <= zeroLow) return 0;
            return (int) Math.round(edge * (value - zeroLow) / (idealLow - zeroLow));
        }
        if (value >= zeroHigh) return 0;
        return (int) Math.round(edge * (zeroHigh - value) / (zeroHigh - idealHigh));
    }
}
