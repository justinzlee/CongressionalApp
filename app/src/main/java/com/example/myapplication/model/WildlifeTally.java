package com.example.myapplication.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * How many of each animal group were seen at a beach over the last
 * {@link #WINDOW_DAYS} days: the real-world sightings iNaturalist holds for
 * the stretch, with this phone's own reports added on top.
 *
 * <p>A count, not a list. A busy beach logs dozens of sea lions a fortnight,
 * and drawing each one as a row would bury the one report on this phone that
 * matters — the washed-up animal — under photographs of the same healthy one.
 *
 * <p>Never a warning by itself. A sighting on iNaturalist does not say whether
 * the animal was in the water or on the sand, and the sharks that turn up most
 * near this app's beaches are leopard sharks that have never bitten anyone.
 * Warnings stay with the reports filed here, where the form asks.
 */
public final class WildlifeTally {

    public static final int WINDOW_DAYS = 14;

    private final Map<SightingSpecies, Integer> counts;
    private final int deadSeals;

    public WildlifeTally(Map<SightingSpecies, Integer> counts, int deadSeals) {
        this.counts = counts.isEmpty()
            ? new EnumMap<>(SightingSpecies.class) : new EnumMap<>(counts);
        this.deadSeals = deadSeals;
    }

    public static WildlifeTally empty() {
        return new WildlifeTally(Collections.emptyMap(), 0);
    }

    public int count(SightingSpecies group) {
        Integer count = counts.get(group);
        return count == null ? 0 : count;
    }

    public int getDeadSeals() {
        return deadSeals;
    }

    public boolean isEmpty() {
        return counts.isEmpty();
    }

    /**
     * This tally with this phone's reports from inside the window added in.
     * Reports older than the window are left out, so both halves of the sum
     * cover the same fortnight.
     */
    public WildlifeTally plus(List<Sighting> reports, long now) {
        long since = now - WINDOW_DAYS * 24L * 60 * 60 * 1000;
        Map<SightingSpecies, Integer> merged = new EnumMap<>(SightingSpecies.class);
        merged.putAll(counts);
        int dead = deadSeals;
        for (Sighting report : reports) {
            if (report.getTimeMillis() < since) continue;
            merged.merge(report.getSpecies(), 1, Integer::sum);
            if (report.isDead()) dead++;
        }
        return new WildlifeTally(merged, dead);
    }

    /** The groups seen at all, most-seen first; ties keep the form's order. */
    public List<SightingSpecies> groupsBySize() {
        List<SightingSpecies> groups = new ArrayList<>();
        for (SightingSpecies group : SightingSpecies.values()) {
            if (count(group) > 0) groups.add(group);
        }
        groups.sort((a, b) -> Integer.compare(count(b), count(a)));
        return groups;
    }
}
