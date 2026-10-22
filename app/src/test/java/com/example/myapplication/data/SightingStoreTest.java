package com.example.myapplication.data;

import com.example.myapplication.model.Sighting;
import com.example.myapplication.model.SightingSpecies;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Reading and writing the sighting log.
 *
 * <p>What is being defended here is a preferences file that an older build of
 * the app, or a restored backup off another phone, may have written. A report
 * that will not parse has to cost that one report — not the beach screen it was
 * going to be drawn on.
 */
public class SightingStoreTest {

    private static final long NOW = 1_700_000_000_000L;

    private Sighting sighting(String id, SightingSpecies species, boolean stranded,
                              String note, String photo, long at) {
        return new Sighting(id, "CA1", species, stranded, false, note, photo, at);
    }

    /** Everything written must come back the same. */
    @Test
    public void reportsSurviveARoundTrip() {
        List<Sighting> written = Arrays.asList(
            sighting("a", SightingSpecies.JELLYFISH, true, "Not moving", "/tmp/a.jpg", NOW),
            sighting("b", SightingSpecies.FISH, false, null, null, NOW - 5000));

        List<Sighting> read = SightingStore.decode("CA1",
            SightingStore.encode(written).toString());

        assertEquals(2, read.size());

        Sighting stranding = read.get(0);
        assertEquals("a", stranding.getId());
        assertEquals("CA1", stranding.getBeachId());
        assertEquals(SightingSpecies.JELLYFISH, stranding.getSpecies());
        assertTrue(stranding.isStranded());
        assertEquals("Not moving", stranding.getNote());
        assertEquals("/tmp/a.jpg", stranding.rawPhotoPath());
        assertEquals(NOW, stranding.getTimeMillis());

        Sighting inWater = read.get(1);
        assertEquals(SightingSpecies.FISH, inWater.getSpecies());
        assertFalse(inWater.isStranded());
        assertNull(inWater.getNote());
        assertNull(inWater.rawPhotoPath());
    }

    /** Newest first, whatever order they went in. */
    @Test
    public void reportsComeBackNewestFirst() {
        List<Sighting> written = Arrays.asList(
            sighting("oldest", SightingSpecies.RAY, false, null, null, NOW - 10_000),
            sighting("newest", SightingSpecies.FISH, false, null, null, NOW),
            sighting("middle", SightingSpecies.JELLYFISH, false, null, null, NOW - 5_000));

        List<Sighting> read = SightingStore.decode("CA1",
            SightingStore.encode(written).toString());

        assertEquals("newest", read.get(0).getId());
        assertEquals("middle", read.get(1).getId());
        assertEquals("oldest", read.get(2).getId());
    }

    /** Nothing stored yet is not an error. */
    @Test
    public void nothingStoredReadsAsNoReports() {
        assertTrue(SightingStore.decode("CA1", null).isEmpty());
        assertTrue(SightingStore.decode("CA1", "").isEmpty());
    }

    /**
     * A file that is not a sighting array at all — a half-written string, or a
     * value some future version of the app put under the same key.
     */
    @Test
    public void unreadableStorageReadsAsNoReports() {
        assertTrue(SightingStore.decode("CA1", "not json at all").isEmpty());
        assertTrue(SightingStore.decode("CA1", "{\"not\":\"an array\"}").isEmpty());
        assertTrue(SightingStore.decode("CA1", "[").isEmpty());
    }

    /**
     * One bad element costs one report. This is the case that matters: a log
     * with four good reports and one corrupt one must still show the four.
     */
    @Test
    public void oneBadReportDoesNotTakeTheRestWithIt() {
        String json = "["
            + "{\"id\":\"good\",\"species\":\"JELLYFISH\",\"stranded\":true,\"at\":" + NOW + "},"
            + "{\"species\":\"JELLYFISH\",\"at\":123}," // no id
            + "{\"id\":\"noTime\",\"species\":\"JELLYFISH\"}," // no timestamp
            + "\"a bare string\","
            + "{\"id\":\"alsoGood\",\"species\":\"FISH\",\"at\":" + (NOW - 1) + "}"
            + "]";

        List<Sighting> read = SightingStore.decode("CA1", json);

        assertEquals(2, read.size());
        assertEquals("good", read.get(0).getId());
        assertEquals("alsoGood", read.get(1).getId());
    }

    /** A species this build has never heard of falls back rather than throwing. */
    @Test
    public void anUnknownSpeciesStillReadsBack() {
        String json = "[{\"id\":\"x\",\"species\":\"NARWHAL\",\"at\":" + NOW + "}]";
        List<Sighting> read = SightingStore.decode("CA1", json);

        assertEquals(1, read.size());
        assertEquals(SightingSpecies.OTHER, read.get(0).getSpecies());
    }

    /** A report with no "stranded" field is not a warning — the safe default. */
    @Test
    public void aMissingStrandedFlagIsNotAWarning() {
        String json = "[{\"id\":\"x\",\"species\":\"JELLYFISH\",\"at\":" + NOW + "}]";
        Sighting read = SightingStore.decode("CA1", json).get(0);

        assertFalse(read.isStranded());
        assertFalse(read.isActiveWarning(NOW));
    }

    /** The beach id comes from the caller, not the file: the key already says it. */
    @Test
    public void theBeachIdComesFromTheCaller() {
        String json = "[{\"id\":\"x\",\"species\":\"JELLYFISH\",\"at\":" + NOW + "}]";
        assertEquals("FL7", SightingStore.decode("FL7", json).get(0).getBeachId());
    }

    /** An empty log encodes to an empty array, not to null or a crash. */
    @Test
    public void anEmptyLogEncodesCleanly() {
        assertEquals("[]", SightingStore.encode(Collections.emptyList()).toString());
        assertTrue(SightingStore.decode("CA1", "[]").isEmpty());
    }

    /**
     * A log written by older builds. Not a hypothetical corrupt file: it is
     * sitting on the phone of everybody who had them. Seals and sea lions land
     * on the seal card, a dolphin in OTHER, and every report survives with its
     * date, photograph and stranding warning intact. None of them says dead,
     * because none of those builds could.
     */
    @Test
    public void aLogFromTheOldSpeciesListStillReadsBack() {
        String json = "["
            + "{\"id\":\"a\",\"species\":\"SEAL\",\"stranded\":true,\"at\":" + NOW + "},"
            + "{\"id\":\"b\",\"species\":\"SEA_LION\",\"at\":" + (NOW - 1) + "},"
            + "{\"id\":\"c\",\"species\":\"DOLPHIN\",\"at\":" + (NOW - 2) + "}"
            + "]";

        List<Sighting> read = SightingStore.decode("CA1", json);

        assertEquals(3, read.size());
        assertEquals(SightingSpecies.SEAL, read.get(0).getSpecies());
        assertEquals(SightingSpecies.SEAL, read.get(1).getSpecies());
        assertEquals(SightingSpecies.OTHER, read.get(2).getSpecies());
        for (Sighting sighting : read) {
            assertFalse(sighting.isDead());
        }
        assertTrue(read.get(0).isActiveWarning(NOW));
    }

    /** A dead seal comes back dead. */
    @Test
    public void aDeadSealRoundTrips() {
        Sighting dead = new Sighting("a", "CA1", SightingSpecies.SEAL, true, true, null, null, NOW);
        List<Sighting> read = SightingStore.decode("CA1",
            SightingStore.encode(Collections.singletonList(dead)).toString());
        assertTrue(read.get(0).isDead());
    }
}
