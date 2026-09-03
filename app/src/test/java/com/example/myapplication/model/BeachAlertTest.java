package com.example.myapplication.model;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The Alerts screen leads with whatever {@link BeachAlert#recordedFor} puts first,
 * so the ordering here decides which warning a user sees before any other.
 */
public class BeachAlertTest {

    private Beach beachWith(String id, String name, Hazard... hazards) {
        return new Beach(id, name, 0, 0, Arrays.asList(hazards), null, null, null);
    }

    private Hazard hazard(HazardType type, Severity severity) {
        return new Hazard(type, severity, "Test description", null);
    }

    @Test
    public void noHazards_produceNoAlerts() {
        List<Beach> beaches = Collections.singletonList(beachWith("a", "Calm Bay"));
        assertTrue(BeachAlert.recordedFor(beaches).isEmpty());
    }

    @Test
    public void everyHazardBecomesAnAlert() {
        List<Beach> beaches = Arrays.asList(
            beachWith("a", "One", hazard(HazardType.RIP_CURRENT, Severity.MODERATE)),
            beachWith("b", "Two",
                hazard(HazardType.UV_INDEX, Severity.LOW),
                hazard(HazardType.POLLUTION, Severity.HIGH)));
        assertEquals(3, BeachAlert.recordedFor(beaches).size());
    }

    @Test
    public void mostSevereLeads() {
        List<Beach> beaches = Arrays.asList(
            beachWith("a", "Mild", hazard(HazardType.UV_INDEX, Severity.LOW)),
            beachWith("b", "Worst", hazard(HazardType.RIP_CURRENT, Severity.EXTREME)),
            beachWith("c", "Middling", hazard(HazardType.POLLUTION, Severity.MODERATE)));

        List<BeachAlert> alerts = BeachAlert.recordedFor(beaches);
        assertEquals(Severity.EXTREME, alerts.get(0).getSeverity());
        assertEquals("Worst", alerts.get(0).getBeach().getName());
    }

    @Test
    public void orderingIsMonotonicallyDescending() {
        List<Beach> beaches = Arrays.asList(
            beachWith("a", "A", hazard(HazardType.UV_INDEX, Severity.MODERATE)),
            beachWith("b", "B", hazard(HazardType.RIP_CURRENT, Severity.LOW)),
            beachWith("c", "C", hazard(HazardType.POLLUTION, Severity.HIGH)),
            beachWith("d", "D", hazard(HazardType.AIR_QUALITY, Severity.EXTREME)));

        List<BeachAlert> alerts = BeachAlert.recordedFor(beaches);
        for (int i = 1; i < alerts.size(); i++) {
            assertTrue("alerts must not get more severe further down the list",
                alerts.get(i - 1).getSeverity().ordinal() >= alerts.get(i).getSeverity().ordinal());
        }
    }

    @Test
    public void idIsStableAndDistinguishesBeaches() {
        Beach one = beachWith("coral", "Coral", hazard(HazardType.RIP_CURRENT, Severity.HIGH));
        Beach two = beachWith("gannet", "Gannet", hazard(HazardType.RIP_CURRENT, Severity.HIGH));

        List<Beach> both = new ArrayList<>(Arrays.asList(one, two));
        List<BeachAlert> alerts = BeachAlert.recordedFor(both);

        // Same hazard type at two beaches must not collapse to one id.
        assertEquals(2, alerts.stream().map(BeachAlert::getId).distinct().count());
        // And the id must not change between runs.
        assertEquals(BeachAlert.recordedFor(both).get(0).getId(), alerts.get(0).getId());
    }

    @Test
    public void everyHazardTypeHasBothLabelForms() {
        // A missing label would surface as a crash on the Alerts screen.
        for (HazardType type : HazardType.values()) {
            assertTrue("no title label for " + type, type.getLabelRes() != 0);
            assertTrue("no inline label for " + type, type.getInlineLabelRes() != 0);
        }
    }
}
