package com.example.myapplication.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pins the two decisions taken while importing somebody's own beach photo that
 * can be checked without a device: how far the image is shrunk, and which way
 * up it ends.
 *
 * <p>The EXIF constants are written as their literal values rather than as
 * {@code ExifInterface.ORIENTATION_*}. They are compile-time constants either
 * way, and the numbers are fixed by the EXIF specification, so spelling them
 * out here keeps the test from leaning on the same library the code under test
 * reads them from.
 */
public class BeachPhotoStoreTest {

    private static final int MAX_EDGE = 1600;

    /**
     * The result must never come back larger than the cap. Decoding an image
     * one step too big is four times the memory, and on a phone importing a
     * fifty-megapixel photo that is the difference between a picture and a
     * crash.
     */
    @Test
    public void sampleSizeNeverLeavesTheImageOverTheCap() {
        int[] edges = {640, 1600, 1601, 2048, 4032, 8000, 12000};
        for (int edge : edges) {
            int sample = BeachPhotoStore.sampleSizeFor(edge, edge / 2, MAX_EDGE);
            assertTrue("sample size must be positive for " + edge, sample >= 1);
            assertTrue("longest edge " + edge + " sampled by " + sample + " is still over the cap",
                edge / sample <= MAX_EDGE);
        }
    }

    /** An image already small enough is decoded whole. */
    @Test
    public void smallImagesAreNotShrunk() {
        assertEquals(1, BeachPhotoStore.sampleSizeFor(1200, 900, MAX_EDGE));
        assertEquals(1, BeachPhotoStore.sampleSizeFor(MAX_EDGE, MAX_EDGE, MAX_EDGE));
    }

    /** BitmapFactory only honours powers of two, so nothing else may be returned. */
    @Test
    public void sampleSizeIsAPowerOfTwo() {
        for (int edge = 100; edge <= 12000; edge += 137) {
            int sample = BeachPhotoStore.sampleSizeFor(edge, 100, MAX_EDGE);
            assertEquals("sample size " + sample + " is not a power of two",
                0, sample & (sample - 1));
        }
    }

    /** The taller dimension is what the cap applies to, whichever one it is. */
    @Test
    public void portraitAndLandscapeShrinkTheSame() {
        assertEquals(
            BeachPhotoStore.sampleSizeFor(4000, 3000, MAX_EDGE),
            BeachPhotoStore.sampleSizeFor(3000, 4000, MAX_EDGE));
    }

    /**
     * A photo held sideways carries the turn in its EXIF block, and this app
     * re-encodes the pixels — dropping that block — so the turn has to be baked
     * in first or the beach comes out on its side.
     */
    @Test
    public void quarterTurnsFollowTheOrientationTag() {
        assertEquals(0, BeachPhotoStore.rotationFor(1));    // ORIENTATION_NORMAL
        assertEquals(180, BeachPhotoStore.rotationFor(3));  // ORIENTATION_ROTATE_180
        assertEquals(90, BeachPhotoStore.rotationFor(6));   // ORIENTATION_ROTATE_90
        assertEquals(270, BeachPhotoStore.rotationFor(8));  // ORIENTATION_ROTATE_270
    }

    /**
     * Undefined, absent and mirrored tags all leave the photo alone. Turning an
     * image that did not ask for it is more visible than leaving one that did.
     */
    @Test
    public void everythingElseIsLeftUpright() {
        int[] untouched = {0, 2, 4, 5, 7, 9, -1};
        for (int orientation : untouched) {
            assertEquals("orientation " + orientation + " should not turn the photo",
                0, BeachPhotoStore.rotationFor(orientation));
        }
    }
}
