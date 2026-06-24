package com.example.myapplication.model;

import java.util.Collections;
import java.util.List;

public class Hazard {
    private final HazardType type;
    private final Severity severity;
    private final String description;
    private final List<String> treatmentSteps;

    public Hazard(HazardType type, Severity severity, String description, List<String> treatmentSteps) {
        this.type = type;
        this.severity = severity;
        this.description = description;
        this.treatmentSteps = treatmentSteps != null ? treatmentSteps : Collections.emptyList();
    }

    public HazardType getType() { return type; }
    public Severity getSeverity() { return severity; }
    public String getDescription() { return description; }
    public List<String> getTreatmentSteps() { return treatmentSteps; }
}
