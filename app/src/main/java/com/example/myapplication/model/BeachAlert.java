package com.example.myapplication.model;

import android.content.Context;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * One entry on the Alerts screen: a hazard on record at a specific beach,
 * flattened out of the beach list so the user can see everything worth
 * knowing about without opening each beach in turn.
 *
 * <p>These are the hazards the app already holds plus the ones its live
 * readings imply — nothing beyond that is invented, and a beach with calm
 * water and nothing on record contributes no alerts.
 */
public class BeachAlert {
    private final Beach beach;
    private final Hazard hazard;

    public BeachAlert(Beach beach, Hazard hazard) {
        this.beach = beach;
        this.hazard = hazard;
    }

    public Beach getBeach() { return beach; }
    public Hazard getHazard() { return hazard; }
    public Severity getSeverity() { return hazard.getSeverity(); }

    /** A stable id so a dismissed alert stays dismissed across restarts. */
    public String getId() {
        return beach.getId() + ":" + hazard.getType().name();
    }

    /**
     * Most severe first, so the worst warning leads the screen. Each beach
     * contributes the hazards on record for it plus the ones today's readings
     * imply, so a beach the home screen calls unsafe also turns up here
     * instead of the two screens disagreeing.
     */
    public static List<BeachAlert> from(Context context, List<Beach> beaches) {
        List<BeachAlert> alerts = new ArrayList<>();
        for (Beach beach : beaches) {
            for (Hazard hazard : ConditionHazards.merged(context, beach)) {
                alerts.add(new BeachAlert(beach, hazard));
            }
        }
        alerts.sort(Comparator.comparingInt(a -> -a.getSeverity().ordinal()));
        return alerts;
    }
}
