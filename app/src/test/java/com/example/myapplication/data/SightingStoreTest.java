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
        return new Sighting(id, "CA1", species, stranded, note, photo, at);
    }

    /** Everything written must come back the same. */
    @Test
    public void reportsSurviveARoundTrip() {
        List<Sighting> written = Arrays.asList(
            sighting("a", SightingSpecies.SEAL, true, "Not moving", "/tmp/a.jpg", NOW),
            sighting("b", SightingSpecies.DOLPHIN, false, null, null, NOW - 5000));

        List<Sighting> read = SightingStore.decode("CA1",
            SightingStore.encode(written).toString());

        assertEquals(2, read.size());

        Sighting seal = read.get(0);
        assertEquals("a", seal.getId());
        assertEquals("CA1", seal.getBeachId());
        assertEquals(SightingSpecies.SEAL, seal.getSpecies());
        assertTrue(seal.isStranded());
        assertEquals("Not moving", seal.getNote());
        assertEquals("/tmp/a.jpg", seal.rawPhotoPath());
        assertEquals(NOW, seal.getTimeMillis());

        Sighting dolphin = read.get(1);
        assertEquals(SightingSpecies.DOLPHIN, dolphin.getSpecies());
        assertFalse(dolphin.isStranded());
        assertNull(dolphin.getNote());
        assertNull(dolphin.rawPhotoPath());
    }

    /** Newest first, whatever order they went in. */
    @Test
    public void reportsComeBackNewestFirst() {
        List<Sighting> written = Arrays.asList(
            sighting("oldest", SightingSpecies.SEAL, false, null, null, NOW - 10_000),
            sighting("newest", SightingSpecies.DOLPHIN, false, null, null, NOW),
            sighting("middle", SightingSpecies.SEA_LION, false, null, null, NOW - 5_000));

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
            + "{\"id\":\"good\",\"species\":\"SEAL\",\"stranded\":true,\"at\":" + NOW + "},"
            + "{\"species\":\"SEAL\",\"at\":123},"          // no id
            + "{\"id\":\"noTime\",\"species\":\"SEAL\"},"   // no timestamp
            + "\"a bare string\","
            + "{\"id\":\"alsoGood\",\"species\":\"DOLPHIN\",\"at\":" + (NOW - 1) + "}"
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
        assertEquals(SightingSpecies.SEAL, read.get(0).getSpecies());
    }

    /** A report with no "stranded" field is not a warning — the safe default. */
    @Test
    public void aMissingStrandedFlagIsNotAWarning() {
        String json = "[{\"id\":\"x\",\"species\":\"SEAL\",\"at\":" + NOW + "}]";
        Sighting read = SightingStore.decode("CA1", json).get(0);

        assertFalse(read.isStranded());
        assertFalse(read.isActiveWarning(NOW));
    }

    /** The beach id comes from the caller, not the file: the key already says it. */
    @Test
    public void theBeachIdComesFromTheCaller() {
        String json = "[{\"id\":\"x\",\"species\":\"SEAL\",\"at\":" + NOW + "}]";
        assertEquals("FL7", SightingStore.decode("FL7", json).get(0).getBeachId());
    }

    /** An empty log encodes to an empty array, not to null or a crash. */
    @Test
    public void anEmptyLogEncodesCleanly() {
        assertEquals("[]", SightingStore.encode(Collections.emptyList()).toString());
        assertTrue(SightingStore.decode("CA1", "[]").isEmpty());
    }
}
