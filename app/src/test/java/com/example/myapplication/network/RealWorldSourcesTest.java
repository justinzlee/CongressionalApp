package com.example.myapplication.network;

import com.example.myapplication.model.SightingSpecies;
import com.example.myapplication.model.WildlifeTally;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Reading the three public sources the starting points come from. The bodies
 * are trimmed copies of real answers: iNaturalist's from La Jolla Cove, eBird's
 * in the documented shape, Overpass's for a beach drawn twice.
 */
public class RealWorldSourcesTest {

    // ── iNaturalist ──────────────────────────────────────────────────────

    private static final String INAT = "{\"total_results\":5,\"results\":["
        // California sea lion: under Phocoidea (372843)
        + "{\"taxon\":{\"ancestor_ids\":[48460,1,2,355675,40151,372843,41736,41740]},\"annotations\":[]},"
        // A dead one
        + "{\"taxon\":{\"ancestor_ids\":[48460,1,2,355675,40151,372843,41736,41740]},"
        + "\"annotations\":[{\"controlled_attribute_id\":17,\"controlled_value_id\":19}]},"
        // Western gull: under Aves (3) and Larinae (447155) — a seagull, not a bird
        + "{\"taxon\":{\"ancestor_ids\":[48460,1,2,355675,3,447155,4343,4364]},\"annotations\":[]},"
        // A pelican: just a bird
        + "{\"taxon\":{\"ancestor_ids\":[48460,1,2,355675,3,4323]},\"annotations\":[]},"
        // Garibaldi: ray-finned fish
        + "{\"taxon\":{\"ancestor_ids\":[48460,1,2,355675,47178,104964]},\"annotations\":[]},"
        // No taxon at all
        + "{\"annotations\":[]}"
        + "]}";

    @Test
    public void iNaturalistSightingsAreSortedIntoTheFormsGroups() {
        WildlifeTally tally = INaturalistClient.parse(INAT);
        assertEquals(2, tally.count(SightingSpecies.SEAL));
        assertEquals(1, tally.count(SightingSpecies.SEAGULL));
        assertEquals(1, tally.count(SightingSpecies.BIRD));
        assertEquals(1, tally.count(SightingSpecies.FISH));
        assertEquals(1, tally.getDeadSeals());
        assertEquals(SightingSpecies.SEAL, tally.groupsBySize().get(0));
    }

    @Test
    public void anINaturalistTallyRoundTripsThroughTheCache() {
        WildlifeTally tally = INaturalistClient.parse(INAT);
        String stored = "{\"SEAL\":2,\"SEAGULL\":1,\"BIRD\":1,\"FISH\":1,\"_dead\":1}";
        WildlifeTally back = INaturalistClient.decodeCache(stored);
        for (SightingSpecies group : SightingSpecies.values()) {
            assertEquals(tally.count(group), back.count(group));
        }
        assertEquals(1, back.getDeadSeals());
    }

    @Test
    public void aBrokenINaturalistAnswerIsNotAnEmptyFortnight() {
        assertNull(INaturalistClient.parse("<html>busy</html>"));
        assertNull(INaturalistClient.parse("{\"error\":\"rate limited\"}"));
    }

    // ── eBird ────────────────────────────────────────────────────────────

    @Test
    public void eBirdGullsAreSummedAcrossSpecies() {
        String body = "["
            + "{\"comName\":\"Western Gull\",\"sciName\":\"Larus occidentalis\",\"howMany\":40},"
            + "{\"comName\":\"Heermann's Gull\",\"sciName\":\"Larus heermanni\",\"howMany\":12},"
            + "{\"comName\":\"Gull-billed Tern\",\"sciName\":\"Gelochelidon nilotica\",\"howMany\":3},"
            + "{\"comName\":\"gull sp.\",\"sciName\":\"Larinae sp.\",\"howMany\":5},"
            + "{\"comName\":\"Bonaparte's Gull\",\"sciName\":\"Chroicocephalus philadelphia\"},"
            + "{\"comName\":\"Brown Pelican\",\"sciName\":\"Pelecanus occidentalis\",\"howMany\":8}"
            + "]";
        // 40 + 12 + 5 unnamed gulls + 1 for the one marked present without a
        // count. The tern is not a gull, whatever its name says.
        assertEquals(Integer.valueOf(58), EBirdClient.parse(body));
    }

    @Test
    public void birdsButNoGullsIsZeroNotNothing() {
        assertEquals(Integer.valueOf(0), EBirdClient.parse(
            "[{\"comName\":\"Brown Pelican\",\"sciName\":\"Pelecanus occidentalis\",\"howMany\":8}]"));
        assertTrue(EBirdClient.parse("[]") < 0);
        assertNull(EBirdClient.parse("{\"errors\":[]}"));
    }

    // ── OpenStreetMap ────────────────────────────────────────────────────

    @Test
    public void theNearestTaggedBeachGivesTheSurface() {
        String body = "{\"elements\":["
            + "{\"type\":\"way\",\"center\":{\"lat\":32.8510,\"lon\":-117.2730},\"tags\":{\"natural\":\"beach\",\"name\":\"Boomer Beach\"}},"
            + "{\"type\":\"way\",\"center\":{\"lat\":32.8504,\"lon\":-117.2716},\"tags\":{\"natural\":\"beach\",\"surface\":\"rock\"}},"
            + "{\"type\":\"way\",\"center\":{\"lat\":32.8540,\"lon\":-117.2700},\"tags\":{\"natural\":\"beach\",\"surface\":\"sand\"}}"
            + "]}";
        assertEquals("rock", ShoreSurfaceClient.nearestSurface(body, 32.8503, -117.2717));
    }

    @Test
    public void untaggedBeachesAreKnownUntaggedAndFailuresAreNot() {
        assertEquals("", ShoreSurfaceClient.nearestSurface(
            "{\"elements\":[{\"tags\":{\"natural\":\"beach\"}}]}", 0, 0));
        assertNull(ShoreSurfaceClient.nearestSurface(
            "{\"remark\":\"runtime error: timeout\",\"elements\":[]}", 0, 0));
        assertNull(ShoreSurfaceClient.nearestSurface("not json", 0, 0));
    }
}
