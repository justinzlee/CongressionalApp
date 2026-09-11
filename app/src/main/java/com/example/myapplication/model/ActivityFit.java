package com.example.myapplication.model;

import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.example.myapplication.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * How well one beach suits one activity today, and why.
 *
 * <p>The "why" is not decoration. A bare 72/100 is unfalsifiable — the reader
 * cannot tell whether it means clean water and no surf or the reverse, and has
 * no way to notice when the app is wrong. So a fit carries the factors it was
 * built from, each with its own score and weight, and the screen shows what
 * lifted the number and what held it down. Someone who disagrees with "held
 * back by wind" can look at the wind and decide for themselves, which is the
 * only honest way to present a made-up composite.
 *
 * <p>Unknown readings are left out rather than scored zero. A beach with no
 * wave model would otherwise rank below a beach with genuinely terrible surf,
 * which is backwards: one is bad for surfing and the other is unknown. What
 * that costs is confidence, which {@link #isConfident()} reports so the screen
 * can mark a thin answer instead of presenting it like a solid one.
 */
public final class ActivityFit {

    /** Under this many known factors, the score is too thin to stand behind. */
    private static final int CONFIDENT_FROM = 4;

    /** One input to a score: what it is, how good it was, how much it counted. */
    public static final class Factor {
        @StringRes private final int labelRes;
        private final int score;
        private final double weight;

        public Factor(@StringRes int labelRes, int score, double weight) {
            this.labelRes = labelRes;
            this.score = Math.max(0, Math.min(100, score));
            this.weight = weight;
        }

        @StringRes public int getLabelRes() { return labelRes; }
        public int getScore() { return score; }
        public double getWeight() { return weight; }

        /** How much this factor moved the total away from a perfect one. */
        double lossContribution() { return (100 - score) * weight; }
    }

    private final Activity activity;
    private final Beach beach;
    private final int score;
    private final List<Factor> factors;

    /**
     * The reason a ceiling was imposed, when one was. Held separately because a
     * ceiling is the limiting factor by definition, whatever the arithmetic in
     * {@link #getLimitingFactor()} would otherwise have picked.
     */
    @Nullable private final Factor ceilingReason;

    private ActivityFit(Activity activity, Beach beach, int score, List<Factor> factors) {
        this(activity, beach, score, factors, null);
    }

    private ActivityFit(Activity activity, Beach beach, int score, List<Factor> factors,
                        @Nullable Factor ceilingReason) {
        this.activity = activity;
        this.beach = beach;
        this.score = score;
        this.factors = Collections.unmodifiableList(factors);
        this.ceilingReason = ceilingReason;
    }

    /**
     * The same fit held to a hard upper bound, with {@code reason} recorded
     * alongside the factors so the screen can say what imposed it.
     *
     * <p>A genuine {@code min}, not another heavy factor in the blend. Adding
     * the ceiling as a weighted factor only pulls the score towards it
     * asymptotically — a beach under a moderate warning came out at 77 against
     * a ceiling of 75 — and a cap that the arithmetic can step over is not a
     * cap. The reason's own weight is irrelevant to the score here and only
     * decides that it outranks the real factors in {@link #getLimitingFactor()}.
     */
    public ActivityFit withCeiling(int ceiling, Factor reason) {
        if (score <= ceiling) return this;
        List<Factor> capped = new ArrayList<>(factors);
        capped.add(reason);
        return new ActivityFit(activity, beach, ceiling, capped, reason);
    }

    /** Builds a fit from whatever factors were known, weighting by each one. */
    public static ActivityFit of(Activity activity, Beach beach, List<Factor> factors) {
        double weighted = 0;
        double totalWeight = 0;
        for (Factor factor : factors) {
            weighted += factor.getScore() * factor.getWeight();
            totalWeight += factor.getWeight();
        }
        int score = totalWeight == 0 ? 0 : (int) Math.round(weighted / totalWeight);
        return new ActivityFit(activity, beach, score, new ArrayList<>(factors));
    }

    public Activity getActivity() { return activity; }
    public Beach getBeach() { return beach; }

    /** 0-100. Comparable between beaches for the same activity, and only then. */
    public int getScore() { return score; }

    public List<Factor> getFactors() { return factors; }

    public boolean isConfident() { return factors.size() >= CONFIDENT_FROM; }

    /** "Great", "Good", "So-so", "Poor" — the word that goes next to the number. */
    @StringRes
    public int getVerdictRes() {
        if (score >= 80) return R.string.fit_great;
        if (score >= 62) return R.string.fit_good;
        if (score >= 40) return R.string.fit_soso;
        return R.string.fit_poor;
    }

    @ColorRes
    public int getColorRes() {
        if (score >= 80) return R.color.safe;
        if (score >= 62) return R.color.primary;
        if (score >= 40) return R.color.caution;
        return R.color.danger;
    }

    /**
     * The factor costing this beach the most, or null when nothing is really
     * holding it back. What the screen shows as "held back by ...".
     *
     * <p>Ranked by weighted loss rather than by raw score, so a factor that
     * scored badly but barely counts does not get blamed for a number it hardly
     * moved.
     */
    @Nullable
    public Factor getLimitingFactor() {
        // A ceiling always wins. Without this, a moderate hazard capping the
        // score at exactly 75 was filtered out by the "still decent" rule
        // below, and the screen went on to say the beach was *helped by* water
        // quality while a rip-current warning was holding the number down.
        if (ceilingReason != null) return ceilingReason;

        Factor worst = factors.stream()
            .max(Comparator.comparingDouble(Factor::lossContribution))
            .orElse(null);
        // Nothing worth naming if the worst offender is still decent.
        return worst == null || worst.getScore() >= 75 ? null : worst;
    }

    /**
     * The factor doing the most for this beach, for the days when the answer is
     * good and "held back by" would have nothing to say.
     */
    @Nullable
    public Factor getStrongestFactor() {
        Factor best = factors.stream()
            .filter(f -> f.getScore() >= 80)
            .max(Comparator.comparingDouble(Factor::getWeight))
            .orElse(null);
        return best;
    }
}
