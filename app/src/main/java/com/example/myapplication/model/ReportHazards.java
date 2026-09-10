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
 * <p>Only two kinds of report ever become a hazard. A dolphin off the break is
 * not a warning however recent it is, and neither is a beach somebody rated a 4.
 */
public final class ReportHazards {

    private ReportHazards() {
    }

    /** Everything currently reported at this beach that is worth warning about. */
    public static List<Hazard> forBeach(Context context, Beach beach) {
        List<Hazard> hazards = new ArrayList<>();

        Sighting stranding = new SightingStore(context)
            .activeWarningFor(beach.getId(), System.currentTimeMillis());
        if (stranding != null) {
            hazards.add(strandingHazard(context, stranding));
        }

        int rocks = new RockRatingStore(context).ratingFor(beach.getId());
        if (RockRating.isHazard(rocks)) {
            hazards.add(rockHazard(context, rocks));
        }

        return hazards;
    }

    /**
     * A marine mammal washed up on the sand.
     *
     * <p>Filed {@link Severity#HIGH}, which puts it at the top of the Alerts tab
     * and into the hero card there. That is a higher rating than the sun ever
     * gets and deserves saying why: unlike UV, this is a discrete thing that is
     * happening right now at one named beach, it carries a genuine bite and
     * disease risk to people and to dogs, approaching it is a federal offence
     * under the Marine Mammal Protection Act, and it expires by itself after two
     * days — so unlike a standing condition it cannot silently inflate the alert
     * list for the rest of the season.
     *
     * <p>The steps are the standing public guidance for a stranded animal, and
     * the third one is the one people get wrong: a seal on the sand is very
     * often resting rather than in trouble, and dragging it back into the water
     * is how a healthy animal gets killed.
     */
    private static Hazard strandingHazard(Context context, Sighting stranding) {
        String species = context.getString(stranding.getSpecies().getLabelRes())
            .toLowerCase(Locale.getDefault());
        return new Hazard(
            HazardType.MARINE_LIFE,
            Severity.HIGH,
            context.getString(R.string.hazard_stranding_body, species),
            Arrays.asList(
                context.getString(R.string.hazard_stranding_step_distance),
                context.getString(R.string.hazard_stranding_step_dogs),
                context.getString(R.string.hazard_stranding_step_leave),
                context.getString(R.string.hazard_stranding_step_tell)));
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
