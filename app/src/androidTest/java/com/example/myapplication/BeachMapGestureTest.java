package com.example.myapplication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.UiAutomation;
import android.content.Intent;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.widget.NestedScrollView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.views.MapView;

/**
 * The beach map inside the scrolling beach screen: one finger scrolls the page
 * past it, two fingers move it.
 *
 * <p>The map is over half a screen tall. When it took every drag that started
 * on it, a thumb scrolling down the page landed on the map, dragged it off the
 * beach, and could not get to the cards below without aiming for the margin.
 *
 * <p>Gestures are injected as real touch events at screen coordinates, so they
 * go through the scroll view's interception exactly as a finger's would.
 */
@RunWith(AndroidJUnit4.class)
public class BeachMapGestureTest {

    /** La Jolla Cove, which has a map and plenty of page below it. */
    private static final String BEACH_ID = "CA18";

    private static final int STEPS = 12;

    private ActivityScenario<BeachDetailActivity> scenario;
    private NestedScrollView page;
    private View mapFrame;
    private MapView map;
    private View hint;

    @Before
    public void openBeachWithMapInView() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), BeachDetailActivity.class)
            .putExtra(BeachDetailActivity.EXTRA_BEACH_ID, BEACH_ID);
        scenario = ActivityScenario.launch(intent);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        scenario.onActivity(activity -> {
            page = activity.findViewById(R.id.detailScrollView);
            View card = activity.findViewById(R.id.detailMapCard);
            mapFrame = card.findViewById(R.id.mapFrame);
            map = (MapView) ((ViewGroup) mapFrame).getChildAt(0);
            hint = card.findViewById(R.id.mapGestureHint);
            // The map's top a little under the top of the page, with the rest
            // of the page still to scroll to below it.
            page.scrollTo(0, offsetInPage(mapFrame) - dp(80));
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    @After
    public void close() {
        if (scenario != null) scenario.close();
    }

    @Test
    public void oneFingerDragUpTheMapScrollsThePage() {
        int[] frame = frameOnScreen();
        float x = frame[0] + frame[2] / 2f;
        int scrollBefore = scrollY();
        IGeoPoint centerBefore = center();

        drag(x, frame[1] + frame[3] * 0.8f, x, frame[1] + frame[3] * 0.3f);

        assertTrue("page should scroll", scrollY() > scrollBefore);
        assertSameCenter(centerBefore, center());
    }

    @Test
    public void oneFingerDragAcrossTheMapMovesNothingAndShowsTheHint() {
        int[] frame = frameOnScreen();
        float y = frame[1] + frame[3] / 2f;
        int scrollBefore = scrollY();
        IGeoPoint centerBefore = center();

        long start = SystemClock.uptimeMillis();
        drag(frame[0] + frame[2] * 0.2f, y, frame[0] + frame[2] * 0.8f, y);

        // First, while it is still up: it goes after 1.8 seconds, and the
        // reads below each wait for the screen to settle.
        int hintVisibility = onMain(() -> hint.getVisibility());
        long elapsed = SystemClock.uptimeMillis() - start;
        assertEquals("hint should be showing " + elapsed + " ms after the drag began",
            View.VISIBLE, hintVisibility);
        assertEquals(scrollBefore, scrollY());
        assertSameCenter(centerBefore, center());
    }

    @Test
    public void twoFingerDragMovesTheMapEvenUpAndDown() {
        int[] frame = frameOnScreen();
        float x = frame[0] + frame[2] * 0.35f;
        int scrollBefore = scrollY();
        IGeoPoint centerBefore = center();

        twoFingerDrag(x, frame[1] + frame[3] * 0.7f, dp(150), -dp(300));

        assertEquals("page should stay put", scrollBefore, scrollY());
        assertNotEquals("map should move", centerBefore.getLatitude(), center().getLatitude(), 1e-6);
    }

    @Test
    public void doubleTapStillZoomsTheMap() {
        int[] frame = frameOnScreen();
        float x = frame[0] + frame[2] / 2f;
        float y = frame[1] + frame[3] / 2f;
        double zoomBefore = onMain(() -> map.getZoomLevelDouble());

        tap(x, y);
        SystemClock.sleep(80);
        tap(x, y);
        SystemClock.sleep(1000);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertTrue("map should zoom in", onMain(() -> map.getZoomLevelDouble()) > zoomBefore);
    }

    // ── Reading the screen ───────────────────────────────────────────────

    private int scrollY() {
        return onMain(() -> page.getScrollY());
    }

    private IGeoPoint center() {
        return onMain(() -> map.getMapCenter());
    }

    private static void assertSameCenter(IGeoPoint expected, IGeoPoint actual) {
        assertEquals("map should not move", expected.getLatitude(), actual.getLatitude(), 1e-7);
        assertEquals("map should not move", expected.getLongitude(), actual.getLongitude(), 1e-7);
    }

    /** The map's left, top, width and height on screen. */
    private int[] frameOnScreen() {
        return onMain(() -> {
            int[] at = new int[2];
            mapFrame.getLocationOnScreen(at);
            return new int[] {at[0], at[1], mapFrame.getWidth(), mapFrame.getHeight()};
        });
    }

    private int offsetInPage(View view) {
        int top = 0;
        for (View v = view; v != page; v = (View) v.getParent()) top += v.getTop();
        return top;
    }

    private int dp(float dp) {
        return Math.round(dp * ApplicationProvider.getApplicationContext()
            .getResources().getDisplayMetrics().density);
    }

    private interface Read<T> { T get(); }

    private <T> T onMain(Read<T> read) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Object[] out = new Object[1];
        scenario.onActivity(activity -> out[0] = read.get());
        @SuppressWarnings("unchecked") T value = (T) out[0];
        return value;
    }

    // ── Touching it ──────────────────────────────────────────────────────

    private void tap(float x, float y) {
        long down = SystemClock.uptimeMillis();
        inject(down, MotionEvent.ACTION_DOWN, x, y);
        inject(down, MotionEvent.ACTION_UP, x, y);
    }

    private void drag(float x0, float y0, float x1, float y1) {
        long down = SystemClock.uptimeMillis();
        inject(down, MotionEvent.ACTION_DOWN, x0, y0);
        for (int i = 1; i <= STEPS; i++) {
            float f = i / (float) STEPS;
            SystemClock.sleep(16);
            inject(down, MotionEvent.ACTION_MOVE, x0 + (x1 - x0) * f, y0 + (y1 - y0) * f);
        }
        inject(down, MotionEvent.ACTION_UP, x1, y1);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    /** Two fingers side by side, moved together by (dx, dy). */
    private void twoFingerDrag(float x, float y, float dx, float dy) {
        float gap = dp(60);
        long down = SystemClock.uptimeMillis();
        int second = 1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        inject(down, MotionEvent.ACTION_DOWN, new float[] {x, y});
        inject(down, MotionEvent.ACTION_POINTER_DOWN | second, new float[] {x, y, x + gap, y});
        for (int i = 1; i <= STEPS; i++) {
            float f = i / (float) STEPS;
            SystemClock.sleep(16);
            inject(down, MotionEvent.ACTION_MOVE,
                new float[] {x + dx * f, y + dy * f, x + gap + dx * f, y + dy * f});
        }
        inject(down, MotionEvent.ACTION_POINTER_UP | second,
            new float[] {x + dx, y + dy, x + gap + dx, y + dy});
        inject(down, MotionEvent.ACTION_UP, new float[] {x + dx, y + dy});
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static void inject(long downTime, int action, float x, float y) {
        inject(downTime, action, new float[] {x, y});
    }

    /** One event with a pointer for each (x, y) pair in {@code xy}. */
    private static void inject(long downTime, int action, float[] xy) {
        int count = xy.length / 2;
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[count];
        for (int i = 0; i < count; i++) {
            properties[i] = new MotionEvent.PointerProperties();
            properties[i].id = i;
            properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].x = xy[2 * i];
            coords[i].y = xy[2 * i + 1];
            coords[i].pressure = 1f;
            coords[i].size = 1f;
        }
        MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
            count, properties, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        assertTrue("event not injected", automation.injectInputEvent(event, true));
        event.recycle();
    }
}
