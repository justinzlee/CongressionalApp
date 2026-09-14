package com.example.myapplication.model;

import android.content.Context;

import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Hazard {
    private final HazardType type;
    private final Severity severity;
    private final String description;
    private final List<String> treatmentSteps;

    @StringRes private final int descriptionRes;
    private final int[] treatmentStepsRes;

    public Hazard(HazardType type, Severity severity, String description, List<String> treatmentSteps) {
        this.type = type;
        this.severity = severity;
        this.description = description;
        this.treatmentSteps = Objects.requireNonNullElse(treatmentSteps, Collections.emptyList());
        this.descriptionRes = 0;
        this.treatmentStepsRes = null;
    }

    /**
     * A hazard written into the beach list itself rather than derived from
     * today's readings, held as resources until something with a Context reads
     * it. The beach list is built once, off any screen and in whatever language
     * happened to be in force then; {@link ConditionHazards#merged} is where it
     * meets a Context, and so where the words get chosen.
     */
    public Hazard(HazardType type, Severity severity, @StringRes int descriptionRes,
                  @StringRes int... treatmentStepsRes) {
        this.type = type;
        this.severity = severity;
        this.description = null;
        this.treatmentSteps = Collections.emptyList();
        this.descriptionRes = descriptionRes;
        this.treatmentStepsRes = treatmentStepsRes;
    }

    public HazardType getType() { return type; }
    public Severity getSeverity() { return severity; }
    public String getDescription() { return description; }
    public List<String> getTreatmentSteps() { return treatmentSteps; }

    /**
     * This hazard with any resource-held text read in the language the app is
     * in. Hazards already carrying words — everything derived from a reading or
     * a report — are returned untouched.
     */
    public Hazard localized(Context context) {
        if (descriptionRes == 0) return this;
        List<String> steps = new ArrayList<>();
        if (treatmentStepsRes != null) {
            for (int stepRes : treatmentStepsRes) {
                steps.add(context.getString(stepRes));
            }
        }
        return new Hazard(type, severity, context.getString(descriptionRes), steps);
    }
}
