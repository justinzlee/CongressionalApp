package com.example.myapplication.model;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * When a reported animal is still a warning.
 *
 * <p>This is the decision that puts a red card at the top of somebody's Alerts
 * tab, and the one that takes it back down again. Getting the first half wrong
 * means a stranded animal goes unreported; getting the second half wrong means
 * the warning outlives the animal, and a stale warning teaches people to ignore
 * the next real one.
 */
public class SightingTest {

    private static final long NOW = 1_700_000_000_000L;
    private static final long HOUR = TimeUnit.HOURS.toMillis(1);
    private static final long DAY = TimeUnit.DAYS.toMillis(1);

    private Sighting at(SightingSpecies species, boolean stranded, long timeMillis) {
        return new Sighting("id", "CA1", species, stranded, null, null, timeMillis);
    }

    /** A stranding reported just now is the case the whole feature exists for. */
    @Test
    public void freshStrandingWarns() {
        assertTrue(at(SightingSpecies.SEAL, true, NOW).isActiveWarning(NOW));
        assertTrue(at(SightingSpecies.SEA_LION, true, NOW - HOUR).isActiveWarning(NOW));
        assertTrue(at(SightingSpecies.SEAL, true, NOW - DAY).isActiveWarning(NOW));
    }

    /**
     * And it stops. A stranded animal is resolved one way or the other well
     * inside two days, so a warning older than that is about an animal that is
     * no longer there.
     */
    @Test
    public void oldStrandingStopsWarning() {
        assertFalse(at(SightingSpecies.SEAL, true, NOW - 3 * DAY).isActiveWarning(NOW));
        assertFalse(at(SightingSpecies.SEAL, true, NOW - 30 * DAY).isActiveWarning(NOW));
    }

    /** The boundary itself: two days exactly is over, a minute under is not. */
    @Test
    public void theCutoffIsTwoDays() {
        assertTrue(at(SightingSpecies.SEAL, true, NOW - 2 * DAY + 60_000).isActiveWarning(NOW));
        assertFalse(at(SightingSpecies.SEAL, true, NOW - 2 * DAY).isActiveWarning(NOW));
    }

    /**
     * An animal in the water is never a warning, however recent. A dolphin off
     * the break is good news, and filing it as a hazard would put good news at
     * the top of the Alerts tab.
     */
    @Test
    public void animalsInTheWaterNeverWarn() {
        for (SightingSpecies species : SightingSpecies.values()) {
            assertFalse(species + " in the water must not warn",
                at(species, false, NOW).isActiveWarning(NOW));
        }
    }

    /**
     * A phone with a wrong clock can stamp a report in the future. Treated as
     * active: silently discarding it would drop a real warning, and of the two
     * ways to be wrong here, the quiet one is worse.
     */
    @Test
    public void aReportFromTheFutureStillWarns() {
        assertTrue(at(SightingSpecies.SEAL, true, NOW + DAY).isActiveWarning(NOW));
    }

    /** A path with no file behind it reads as no photograph, not as a broken one. */
    @Test
    public void aMissingPhotoFileReadsAsNoPhoto() {
        Sighting sighting = new Sighting("id", "CA1", SightingSpecies.SEAL, true,
            null, "/nowhere/at/all/missing.jpg", NOW);
        assertNull(sighting.getPhoto());
        // But the record keeps the path, so writing it back does not lose it.
        assertEquals("/nowhere/at/all/missing.jpg", sighting.rawPhotoPath());
    }

    /** Unknown species names must not take the screen down. */
    @Test
    public void unknownSpeciesFallsBackToSeal() {
        assertEquals(SightingSpecies.SEAL, SightingSpecies.parse("MANATEE"));
        assertEquals(SightingSpecies.SEAL, SightingSpecies.parse(null));
        assertEquals(SightingSpecies.SEAL, SightingSpecies.parse(""));
        assertEquals(SightingSpecies.DOLPHIN, SightingSpecies.parse("DOLPHIN"));
    }

    /** Every species can be written out and read back as itself. */
    @Test
    public void everySpeciesRoundTripsThroughItsName() {
        for (SightingSpecies species : SightingSpecies.values()) {
            assertEquals(species, SightingSpecies.parse(species.name()));
        }
    }
}
