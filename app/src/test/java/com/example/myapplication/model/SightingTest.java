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
        assertTrue(at(SightingSpecies.JELLYFISH, true, NOW).isActiveWarning(NOW));
        assertTrue(at(SightingSpecies.BIRD, true, NOW - HOUR).isActiveWarning(NOW));
        assertTrue(at(SightingSpecies.OTHER, true, NOW - DAY).isActiveWarning(NOW));
    }

    /** Whatever washed up, it warns. The group changes the advice, not the alarm. */
    @Test
    public void anythingWashedUpWarns() {
        for (SightingSpecies species : SightingSpecies.values()) {
            assertTrue(species + " washed up must warn",
                at(species, true, NOW).isActiveWarning(NOW));
        }
    }

    /**
     * And it stops. A stranded animal is resolved one way or the other well
     * inside two days, so a warning older than that is about an animal that is
     * no longer there.
     */
    @Test
    public void oldStrandingStopsWarning() {
        assertFalse(at(SightingSpecies.JELLYFISH, true, NOW - 3 * DAY).isActiveWarning(NOW));
        assertFalse(at(SightingSpecies.JELLYFISH, true, NOW - 30 * DAY).isActiveWarning(NOW));
    }

    /** The boundary itself: two days exactly is over, a minute under is not. */
    @Test
    public void theStrandingCutoffIsTwoDays() {
        assertTrue(at(SightingSpecies.JELLYFISH, true, NOW - 2 * DAY + 60_000)
            .isActiveWarning(NOW));
        assertFalse(at(SightingSpecies.JELLYFISH, true, NOW - 2 * DAY)
            .isActiveWarning(NOW));
    }

    /**
     * The one in-water report that warns anybody. Nothing is on the sand, so
     * this is the case that would be lost if the warning still keyed off
     * {@link Sighting#isStranded()} alone.
     */
    @Test
    public void aSharkInTheWaterWarns() {
        assertTrue(at(SightingSpecies.SHARK, false, NOW).isActiveWarning(NOW));
        assertTrue(at(SightingSpecies.SHARK, false, NOW - 6 * HOUR).isActiveWarning(NOW));
    }

    /**
     * And it expires a day sooner than a stranding does. A stranded animal is
     * still lying there tomorrow; a shark swam past, and by the second day the
     * warning is describing yesterday's ocean.
     */
    @Test
    public void theSharkCutoffIsOneDay() {
        assertTrue(at(SightingSpecies.SHARK, false, NOW - DAY + 60_000).isActiveWarning(NOW));
        assertFalse(at(SightingSpecies.SHARK, false, NOW - DAY).isActiveWarning(NOW));
        assertFalse(at(SightingSpecies.SHARK, false, NOW - 2 * DAY).isActiveWarning(NOW));
    }

    /**
     * Everything else in the water is never a warning, however recent. A dolphin
     * off the break is good news, and filing it as a hazard would put good news
     * at the top of the Alerts tab.
     */
    @Test
    public void everythingElseInTheWaterNeverWarns() {
        for (SightingSpecies species : SightingSpecies.values()) {
            if (species == SightingSpecies.SHARK) continue;
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
        assertTrue(at(SightingSpecies.JELLYFISH, true, NOW + DAY).isActiveWarning(NOW));
        assertTrue(at(SightingSpecies.SHARK, false, NOW + DAY).isActiveWarning(NOW));
    }

    /** A path with no file behind it reads as no photograph, not as a broken one. */
    @Test
    public void aMissingPhotoFileReadsAsNoPhoto() {
        Sighting sighting = new Sighting("id", "CA1", SightingSpecies.JELLYFISH, true,
            null, "/nowhere/at/all/missing.jpg", NOW);
        assertNull(sighting.getPhoto());
        // But the record keeps the path, so writing it back does not lose it.
        assertEquals("/nowhere/at/all/missing.jpg", sighting.rawPhotoPath());
    }

    /**
     * Unknown species names must not take the screen down, and must not invent
     * protection either: a name this build has never heard of is an animal of
     * unknown kind, which is exactly what OTHER means.
     */
    @Test
    public void unknownSpeciesFallsBackToOther() {
        assertEquals(SightingSpecies.OTHER, SightingSpecies.parse("MANATEE"));
        assertEquals(SightingSpecies.OTHER, SightingSpecies.parse(null));
        assertEquals(SightingSpecies.OTHER, SightingSpecies.parse(""));
    }

    /**
     * Reports filed before this screen offered groups. The three names the app
     * used to write no longer have a card of their own, so they land in OTHER
     * along with everything else unrecognised — the report survives, and the
     * stranding advice it gets is the same advice every washed-up animal gets.
     */
    @Test
    public void theOldSpeciesNamesStillReadBack() {
        assertEquals(SightingSpecies.OTHER, SightingSpecies.parse("DOLPHIN"));
        assertEquals(SightingSpecies.OTHER, SightingSpecies.parse("SEAL"));
        assertEquals(SightingSpecies.OTHER, SightingSpecies.parse("SEA_LION"));
    }

    /** Every group can be written out and read back as itself. */
    @Test
    public void everySpeciesRoundTripsThroughItsName() {
        for (SightingSpecies species : SightingSpecies.values()) {
            assertEquals(species, SightingSpecies.parse(species.name()));
        }
    }

    /** Every group has a card label and a sentence noun; neither may be missing. */
    @Test
    public void everySpeciesHasBothItsWords() {
        for (SightingSpecies species : SightingSpecies.values()) {
            assertTrue("no label for " + species, species.getLabelRes() != 0);
            assertTrue("no noun for " + species, species.getNounRes() != 0);
            assertTrue("no icon for " + species, species.getIconRes() != 0);
        }
    }
}
