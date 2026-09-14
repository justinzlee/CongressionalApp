package com.example.myapplication.model;

import android.content.Context;

import com.example.myapplication.R;
import com.example.myapplication.data.RockRatingStore;
import com.example.myapplication.data.SightingStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Turns what people have reported at a beach into the same kind of {@link
 * Hazard} the beach list holds and the forecast implies.
 *
 * <p>This is the whole point of collecting the reports. A stranded seal noted on
 * the sighting screen that only ever showed up on the sighting screen would be a
 * diary entry; routed through here it reaches "What to watch for" on the beach,
 * the Alerts tab, and the unread badge on the tab bar, because every one of
 * those is built from {@link ConditionHazards#merged}. One insertion point, and
 * a report behaves like every other warning in the app.
 *
 * <p>Only three kinds of report ever become a hazard: an animal washed up on the
 * sand, a shark in the water, and ground somebody rated as rock. A dolphin off
 * the break is not a warning however recent it is, and neither is a beach
 * somebody rated a 4.
 */
public final class ReportHazards {

    private ReportHazards() {
    }

    /** Everything currently reported at this beach that is worth warning about. */
    public static List<Hazard> forBeach(Context context, Beach beach) {
        List<Hazard> hazards = new ArrayList<>();

        Sighting warning = new SightingStore(context)
            .activeWarningFor(beach.getId(), System.currentTimeMillis());
        if (warning != null) {
            hazards.add(warning.isStranded()
                ? strandingHazard(context, warning)
                : sharkHazard(context));
        }

        int rocks = new RockRatingStore(context).ratingFor(beach.getId());
        if (RockRating.isHazard(rocks)) {
            hazards.add(rockHazard(context, rocks));
        }

        return hazards;
    }

    /**
     * An animal washed up on the sand.
     *
     * <p>Filed {@link Severity#HIGH}, which puts it at the top of the Alerts tab
     * and into the hero card there. That is a higher rating than the sun ever
     * gets and deserves saying why: unlike UV, this is a discrete thing that is
     * happening right now at one named beach, it carries a genuine bite and
     * disease risk to people and to dogs, and it expires by itself after two
     * days — so unlike a standing condition it cannot silently inflate the alert
     * list for the rest of the season.
     *
     * <p>One set of steps for every group, because the report no longer says
     * which animal it is with enough confidence to give advice that is only true
     * of some of them. So each step has to hold for anything that washes up, and
     * the two pieces of guidance that used to belong to the marine-mammal card
     * are carried here instead: do not refloat it, and a seal, sea lion, dolphin
     * or whale has a stranding network to call and a federal law protecting it.
     * Naming those animals in the step is what keeps that advice reaching them
     * now that the form has no card for them.
     */
    private static Hazard strandingHazard(Context context, Sighting stranding) {
        String noun = context.getString(stranding.getSpecies().getNounRes());
        return new Hazard(
            HazardType.MARINE_LIFE,
            Severity.HIGH,
            context.getString(R.string.hazard_stranding_body, noun),
            Arrays.asList(
                context.getString(R.string.hazard_stranding_step_back),
                context.getString(R.string.hazard_stranding_step_dogs),
                context.getString(R.string.hazard_stranding_step_leave),
                context.getString(R.string.hazard_stranding_step_tell)));
    }

    /**
     * A shark reported in the water.
     *
     * <p>{@link Severity#HIGH} like a stranding, and for the same reason it is
     * safe to rate that high: it is one named beach, right now, and it clears
     * itself after a day. The steps are the standing lifeguard instruction and
     * they are worded to be followed rather than to be alarming — people drown
     * getting out of the water badly far more often than sharks bite them, so
     * "calmly, facing it" is doing more work here than the warning itself.
     */
    private static Hazard sharkHazard(Context context) {
        return new Hazard(
            HazardType.MARINE_LIFE,
            Severity.HIGH,
            context.getString(R.string.hazard_shark_body),
            Arrays.asList(
                context.getString(R.string.hazard_shark_step_exit),
                context.getString(R.string.hazard_shark_step_group),
                context.getString(R.string.hazard_shark_step_tell)));
    }

    /**
     * Ground you cannot cross barefoot. Never worse than {@link
     * Severity#MODERATE} — see {@link RockRating#isHazard}.
     */
    private static Hazard rockHazard(Context context, int rating) {
        return new Hazard(
            HazardType.ROCKS_AND_STRUCTURES,
            Severity.MODERATE,
            context.getString(R.string.hazard_rocks_rated_body, rating,
                context.getString(RockRating.labelRes(rating)).toLowerCase(Locale.getDefault())),
            Arrays.asList(
                context.getString(R.string.hazard_rocks_step_shoes),
                context.getString(R.string.hazard_rocks_step_entry)));
    }
}
