package com.example.myapplication.model;

import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.example.myapplication.R;

/**
 * Whether the water is likely to be contaminated today, and why.
 *
 * <p>This is inferred, not measured, and the distinction matters enough to say
 * in the type. Nobody publishes a free live bacteria count for every beach in
 * this app — the counties that do sample post results days later, after the
 * water has already changed — so what this models instead is the two things
 * that reliably predict contamination and are knowable in advance.
 *
 * <p>The first is rain. Storm drains carry the street into the sea, and the
 * standard public-health guidance on the whole US coast is to stay out for
 * roughly 72 hours after meaningful rainfall. That rule is why this class reads
 * a rainfall history rather than a water sample.
 *
 * <p>The second is the Tijuana River. Cross-border sewage flows put the far
 * south of San Diego County under near-permanent advisory, and rain makes it
 * dramatically worse. Those beaches carry a standing note whatever the weather,
 * because a swimmer at Imperial Beach on a dry day is still facing a different
 * risk from one at Malibu.
 *
 * <p>Every message this produces says it is a prediction. Telling somebody the
 * water is clean is not a claim this app is in a position to make.
 */
public final class WaterQuality {

    /**
     * Rain that counts, in millimetres over a day.
     *
     * <p>About a tenth of an inch. Below this the drains do not run; the
     * figure is the low end of what county advisories use as a trigger, chosen
     * low on purpose because the cost of a false warning is a cancelled swim
     * and the cost of a missed one is illness.
     */
    public static final double RAIN_TRIGGER_MM = 2.5;

    /** How long after rain the advisory stands. The 72-hour rule. */
    public static final int RAIN_ADVISORY_DAYS = 3;

    /** Worst first, so a beach's status is the highest one that applies. */
    public enum Status {
        /** No rain recently and no standing problem here. */
        LIKELY_CLEAN(R.string.water_clean, R.string.water_clean_body, R.color.safe),
        /** Rain in the last three days; storm drains have been running. */
        RUNOFF_ADVISORY(R.string.water_runoff, R.string.water_runoff_body, R.color.caution),
        /** A beach in the cross-border plume's reach, on a dry day. */
        SEWAGE_RISK(R.string.water_sewage, R.string.water_sewage_body, R.color.uv_high),
        /** Both at once, which is when the south county beaches close. */
        SEWAGE_AND_RAIN(R.string.water_sewage_rain, R.string.water_sewage_rain_body, R.color.danger);

        @StringRes public final int labelRes;
        @StringRes public final int bodyRes;
        @ColorRes public final int colorRes;

        Status(@StringRes int labelRes, @StringRes int bodyRes, @ColorRes int colorRes) {
            this.labelRes = labelRes;
            this.bodyRes = bodyRes;
            this.colorRes = colorRes;
        }
    }

    private final Status status;
    private final double recentRainMm;
    @Nullable private final Integer daysSinceRain;

    private WaterQuality(Status status, double recentRainMm, @Nullable Integer daysSinceRain) {
        this.status = status;
        this.recentRainMm = recentRainMm;
        this.daysSinceRain = daysSinceRain;
    }

    public Status getStatus() { return status; }

    /** Total rainfall over the advisory window, millimetres. */
    public double getRecentRainMm() { return recentRainMm; }

    /** Days since the last rain that cleared the trigger, or null if none did. */
    @Nullable public Integer getDaysSinceRain() { return daysSinceRain; }

    public boolean isAdvisory() { return status != Status.LIKELY_CLEAN; }

    /**
     * Works out the status from a beach and its recent rainfall.
     *
     * @param dailyRainMm rainfall per day, oldest first, ending with today.
     *                    Days the forecast had no figure for should be 0.
     */
    public static WaterQuality evaluate(Beach beach, double[] dailyRainMm) {
        double total = 0;
        Integer daysSince = null;

        int window = Math.min(RAIN_ADVISORY_DAYS, dailyRainMm.length);
        for (int i = 0; i < window; i++) {
            // Walk backwards from today, so index 0 of the window is today.
            double rain = dailyRainMm[dailyRainMm.length - 1 - i];
            total += rain;
            if (rain >= RAIN_TRIGGER_MM && daysSince == null) {
                daysSince = i;
            }
        }

        boolean rained = daysSince != null;
        TijuanaPlume.Reach reach = TijuanaPlume.reachFor(beach);

        // A beach in the plume's chronic band is under advisory whatever the
        // weather. One in the rain-driven band only joins it once the flow has
        // been high enough to carry that far, which is what the rain stands in
        // for - so on a dry day it is treated as any other beach.
        boolean sewage = reach == TijuanaPlume.Reach.CHRONIC
            || (reach == TijuanaPlume.Reach.RAIN_DRIVEN && rained);

        Status status;
        if (sewage && rained) {
            status = Status.SEWAGE_AND_RAIN;
        } else if (sewage) {
            status = Status.SEWAGE_RISK;
        } else if (rained) {
            status = Status.RUNOFF_ADVISORY;
        } else {
            status = Status.LIKELY_CLEAN;
        }
        return new WaterQuality(status, total, daysSince);
    }

    /**
     * 0-100 for the activity scores. Swimming cares about this far more than
     * fishing from a pier does, so the weighting is left to the scorer; this is
     * only how bad the water is.
     */
    public int cleanlinessScore() {
        switch (status) {
            case LIKELY_CLEAN: return 100;
            case RUNOFF_ADVISORY: return 35;
            case SEWAGE_RISK: return 20;
            default: return 0;
        }
    }
}
