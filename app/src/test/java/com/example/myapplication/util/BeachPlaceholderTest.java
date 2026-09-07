package com.example.myapplication.util;

import com.example.myapplication.data.BeachRepository;
import com.example.myapplication.model.Beach;

import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pins how a beach is matched to one of the five placeholder scenes.
 *
 * <p>Two things matter and neither is visible from the drawing itself. A beach
 * has to keep the same scene every time it is drawn, or scrolling a list would
 * shuffle the pictures under the names. And the five have to actually get used,
 * or the whole point of drawing five of them is lost.
 */
public class BeachPlaceholderTest {

    private static Beach withId(String id) {
        return new Beach(id, "Test Beach", 0, 0, null, null, null, null);
    }

    @Test
    public void aBeachAlwaysGetsTheSameScene() {
        Beach beach = withId("CA1");
        int first = BeachPlaceholder.sceneFor(beach);
        for (int i = 0; i < 50; i++) {
            assertEquals("The scene must not change between draws",
                first, BeachPlaceholder.sceneFor(withId("CA1")));
        }
    }

    @Test
    public void differentBeachesGetDifferentScenes() {
        // Not a guarantee for any given pair, but these two must not collide or
        // the neighbouring-beach case the variants exist for is unserved.
        assertTrue(BeachPlaceholder.sceneFor(withId("CA1"))
            != BeachPlaceholder.sceneFor(withId("HI1")));
    }

    /**
     * Every scene has to appear somewhere in the real dataset. A scene that no
     * beach is ever assigned is dead artwork, and the assignment is arithmetic
     * on ids rather than anything a reader can eyeball.
     */
    @Test
    public void everySceneIsUsedAcrossTheRealBeachList() {
        List<Beach> beaches = BeachRepository.newForTesting().getBeaches();

        Map<Integer, Integer> uses = new HashMap<>();
        for (Beach beach : beaches) {
            int scene = BeachPlaceholder.sceneFor(beach);
            uses.put(scene, uses.getOrDefault(scene, 0) + 1);
        }

        assertEquals("All five scenes should be in use", 5, uses.size());

        // No scene should carry the list on its own. With 114 beaches an even
        // split is about 23 each; this only catches a badly lopsided hash.
        for (Map.Entry<Integer, Integer> use : uses.entrySet()) {
            assertTrue("One scene is doing too much of the work: " + use.getValue()
                    + " of " + beaches.size(),
                use.getValue() <= beaches.size() / 2);
        }
    }

    @Test
    public void beachesWithoutAnIdStillGetAScene() {
        // Never happens with the hardcoded list, but sceneFor indexes an array
        // and must not throw its way onto a screen about water safety.
        int scene = BeachPlaceholder.sceneFor(withId(null));
        Set<Integer> all = new HashSet<>();
        for (Beach beach : BeachRepository.newForTesting().getBeaches()) {
            all.add(BeachPlaceholder.sceneFor(beach));
        }
        assertTrue("The fallback should be one of the real scenes", all.contains(scene));
    }
}
