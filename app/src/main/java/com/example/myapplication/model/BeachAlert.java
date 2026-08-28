package com.example.myapplication.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * One entry on the Alerts screen: a hazard on record at a specific beach,
 * flattened out of the beach list so the user can see everything worth
 * knowing about without opening each beach in turn.
 *
 * <p>These are the hazards the app already holds — nothing is invented, and a
 * beach with nothing on record contributes no alerts.
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

    /** Most severe first, so the worst warning leads the screen. */
    public static List<BeachAlert> from(List<Beach> beaches) {
        List<BeachAlert> alerts = new ArrayList<>();
        for (Beach beach : beaches) {
            for (Hazard hazard : beach.getHazards()) {
                alerts.add(new BeachAlert(beach, hazard));
            }
        }
        alerts.sort(Comparator.comparingInt(a -> -a.getSeverity().ordinal()));
        return alerts;
    }
}
