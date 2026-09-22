package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.myapplication.model.Beach;
import com.example.myapplication.model.HelpLocation;
import com.example.myapplication.model.HelpType;
import com.example.myapplication.util.UnitConverter;

import org.osmdroid.tileprovider.MapTileProviderBasic;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.TilesOverlay;

import java.util.ArrayList;
import java.util.List;

/**
 * The beach map on a beach's screen: the sand from above with its
 * lifeguard towers marked, and you on it — or, when you are somewhere else, an
 * arrow on the map's edge pointing the way back to you.
 *
 * <p>The imagery is Esri's, fetched through osmdroid ({@link MapTiles}); the
 * towers come from the app's own records and from OpenStreetMap
 * ({@link com.example.myapplication.network.LifeguardClient}); the markers are
 * drawn by {@link BeachMapMarkers}. This holds the map and the controls around
 * it, and forwards the screen's lifecycle to the map library.
 */
public class BeachMapCard extends LinearLayout {

    /** The zoom a beach opens at and Recenter returns to — the design's. */
    private static final double BEACH_ZOOM = 16.0;
    private static final double MIN_ZOOM = 9.0;

    /** The stretch of map the scale pill measures: the design's 80 pixels. */
    private static final float SCALE_SPAN_DP = 80f;

    /** Help points closer together than this are one place mapped twice. */
    private static final double SAME_PLACE_M = 60;

    /** Satellite or plain, remembered across beaches. */
    private static final String KEY_STYLE = "beach_map_style";
    private static final String STYLE_PLAIN = "plain";
    private static final String STYLE_SATELLITE = "satellite";

    private final View mapFrame;
    private final BeachMapMarkers markers;
    private final TextView scale;
    private final TextView satelliteButton;
    private final TextView plainButton;
    private final View legendTower;
    private final TextView gestureHint;

    @Nullable private MapView map;
    @Nullable private TilesOverlay labels;

    @Nullable private Beach beach;
    private final List<HelpLocation> helpPoints = new ArrayList<>();
    private boolean plain;
    private boolean metric;
    private boolean scalePending;

    private final Runnable updateScale = this::updateScale;
    private final Runnable hideGestureHint = this::hideGestureHint;

    public BeachMapCard(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setBackgroundResource(R.drawable.bg_map_card);
        // Inside the hairline, so the map does not paint over the card's edge,
        // and clipped to the rounded outline, so it does not square the corners.
        int edge = Math.round(getResources().getDisplayMetrics().density);
        setPadding(edge, edge, edge, edge);
        setClipToOutline(true);
        LayoutInflater.from(context).inflate(R.layout.view_beach_map_card, this, true);

        mapFrame = findViewById(R.id.mapFrame);
        markers = findViewById(R.id.mapMarkers);
        scale = findViewById(R.id.mapScale);
        satelliteButton = findViewById(R.id.mapSatellite);
        plainButton = findViewById(R.id.mapPlain);
        legendTower = findViewById(R.id.mapLegendTower);
        gestureHint = findViewById(R.id.mapGestureHint);
        findViewById(R.id.mapZoom).setClipToOutline(true);
        findViewById(R.id.mapStyle).setClipToOutline(true);

        findViewById(R.id.mapRecenter).setOnClickListener(v -> recenter());
        findViewById(R.id.mapZoomIn).setOnClickListener(v -> zoomBy(1));
        findViewById(R.id.mapZoomOut).setOnClickListener(v -> zoomBy(-1));
        satelliteButton.setOnClickListener(v -> setPlain(false));
        plainButton.setOnClickListener(v -> setPlain(true));

        plain = !isInEditMode() && STYLE_PLAIN.equals(prefs().getString(KEY_STYLE, null));
        paintToggles();

        // The layout preview has no network and no cache folder to give the
        // map library, so it shows the card around an empty map.
        if (isInEditMode()) return;
        MapTiles.configure(context);
        buildMap(context);
        applyStyle();
    }

    private void buildMap(Context context) {
        Context app = context.getApplicationContext();
        MapView map = new PageMapView(context, new MapTileProviderBasic(app, MapTiles.SATELLITE));
        map.setTilesScaledToDpi(true);
        map.setMultiTouchControls(true);
        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        map.setMinZoomLevel(MIN_ZOOM);

        int ground = ContextCompat.getColor(context, R.color.map_ground);
        TilesOverlay base = map.getOverlayManager().getTilesOverlay();
        base.setLoadingBackgroundColor(ground);
        base.setLoadingLineColor(ground);

        // The plain look's names sit on a layer of their own over the grey
        // base, as Esri serves them, and only go on the map with that look.
        MapTileProviderBasic labelTiles = new MapTileProviderBasic(app, MapTiles.PLAIN_LABELS);
        labelTiles.getTileRequestCompleteHandlers().add(map.getTileRequestCompleteHandler());
        TilesOverlay labels = new TilesOverlay(labelTiles, context);
        labels.setLoadingBackgroundColor(Color.TRANSPARENT);
        labels.setLoadingLineColor(Color.TRANSPARENT);
        labels.setColorFilter(MapTiles.labelFilter());
        this.labels = labels;

        // Rides along with every frame the map draws, so the markers and the
        // scale move with it — mid-pinch and mid-fling included.
        // The library calls one draw or the other depending on how the frame
        // was asked for, so both are caught.
        map.getOverlays().add(new Overlay() {
            @Override
            public void draw(Canvas canvas, MapView mapView, boolean shadow) {
                if (!shadow) onMapDrawn();
            }

            @Override
            public void draw(Canvas canvas, Projection projection) {
                onMapDrawn();
            }
        });

        ((FrameLayout) mapFrame).addView(map, 0, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        markers.setMap(map);
        this.map = map;
    }

    // ── What goes on the map ─────────────────────────────────────────────

    public void setBeach(Beach beach) {
        this.beach = beach;
        metric = UnitConverter.useMetric(getContext());
        markers.setMetric(metric);
        mapFrame.setContentDescription(
            getContext().getString(R.string.map_description, beach.getName()));

        helpPoints.clear();
        for (HelpLocation help : beach.getNearestHelp()) {
            // A hospital is help, but not on the sand. This close in it would
            // sit off the map's edge, or be taken for a tower if it did not.
            if (help.getType() != HelpType.HOSPITAL) helpPoints.add(help);
        }
        showHelpPoints();
        recenter();
    }

    /** Towers from elsewhere, less any already on the map. */
    public void addTowers(List<HelpLocation> towers) {
        for (HelpLocation tower : towers) {
            if (!alreadyShown(tower)) helpPoints.add(tower);
        }
        showHelpPoints();
    }

    public void setUserLocation(double latitude, double longitude) {
        markers.setUser(new GeoPoint(latitude, longitude));
    }

    private boolean alreadyShown(HelpLocation candidate) {
        GeoPoint point = new GeoPoint(candidate.getLatitude(), candidate.getLongitude());
        for (HelpLocation shown : helpPoints) {
            GeoPoint other = new GeoPoint(shown.getLatitude(), shown.getLongitude());
            if (point.distanceToAsDouble(other) < SAME_PLACE_M) return true;
        }
        return false;
    }

    private void showHelpPoints() {
        if (beach == null) return;
        List<BeachMapMarkers.Tower> shown = new ArrayList<>();
        for (HelpLocation help : helpPoints) {
            shown.add(new BeachMapMarkers.Tower(
                new GeoPoint(help.getLatitude(), help.getLongitude()),
                BeachMapMath.towerLabel(help.getName(), help.getLatitude(), help.getLongitude(),
                    beach.getLatitude(), beach.getLongitude())));
        }
        markers.setTowers(shown);
        // The key only explains a marker that is actually there.
        legendTower.setVisibility(shown.isEmpty() ? GONE : VISIBLE);
    }

    // ── Controls ─────────────────────────────────────────────────────────

    private void recenter() {
        if (map == null || beach == null) return;
        map.getController().setZoom(BEACH_ZOOM);
        map.getController().setCenter(new GeoPoint(beach.getLatitude(), beach.getLongitude()));
    }

    /** A whole step in or out from wherever a pinch left the zoom. */
    private void zoomBy(int step) {
        if (map == null) return;
        double target = Math.round(map.getZoomLevelDouble()) + step;
        map.getController().setZoom(Math.max(MIN_ZOOM, Math.min(maxZoom(), target)));
    }

    private double maxZoom() {
        return plain ? MapTiles.PLAIN_MAX_ZOOM : MapTiles.SATELLITE_MAX_ZOOM;
    }

    private void setPlain(boolean plain) {
        if (this.plain == plain) return;
        this.plain = plain;
        prefs().edit().putString(KEY_STYLE, plain ? STYLE_PLAIN : STYLE_SATELLITE).apply();
        paintToggles();
        applyStyle();
    }

    private void applyStyle() {
        MapView map = this.map;
        if (map == null) return;
        map.setTileSource(plain ? MapTiles.PLAIN : MapTiles.SATELLITE);
        map.getOverlayManager().getTilesOverlay()
            .setColorFilter(plain ? null : MapTiles.satelliteFilter());
        List<Overlay> overlays = map.getOverlays();
        overlays.remove(labels);
        if (plain) overlays.add(0, labels);
        // The grey canvas stops at 16. Past it there would be nothing to show.
        map.setMaxZoomLevel(maxZoom());
        if (map.getZoomLevelDouble() > maxZoom()) map.getController().setZoom(maxZoom());
        map.invalidate();
    }

    private void paintToggles() {
        paintToggle(satelliteButton, !plain);
        paintToggle(plainButton, plain);
    }

    private void paintToggle(TextView button, boolean on) {
        button.setSelected(on);
        button.setBackgroundColor(on
            ? ContextCompat.getColor(getContext(), R.color.map_hud_active)
            : Color.TRANSPARENT);
        button.setTextColor(ContextCompat.getColor(getContext(),
            on ? R.color.map_hud_active_text : R.color.map_hud_text_quiet));
    }

    // ── Keeping up with the map ──────────────────────────────────────────

    private void onMapDrawn() {
        markers.invalidate();
        if (!scalePending) {
            scalePending = true;
            post(updateScale);
        }
    }

    private void updateScale() {
        scalePending = false;
        MapView map = this.map;
        if (map == null) return;
        double metres = BeachMapMath.metresPerDp(
            map.getMapCenter().getLatitude(), map.getZoomLevelDouble()) * SCALE_SPAN_DP;
        String label = BeachMapMath.scale(metres, metric);
        if (!label.contentEquals(scale.getText())) scale.setText(label);
    }

    /**
     * The map sits in a scrolling page and is over half a screen tall, so one
     * finger belongs to the page and two to the map. Were it the other way
     * round, a thumb scrolling down the page would land on the map, drag it off
     * the beach, and never get past to the cards below it.
     *
     * <p>A one-finger drag the page does not take — a sideways one — moves
     * nothing, so it is answered with the hint rather than left to feel broken.
     * Taps still reach the map, so a double tap zooms, and the buttons are there
     * for anyone with a hand free for only one finger.
     */
    private final class PageMapView extends MapView {
        private final int touchSlop;
        private float downX;
        private float downY;
        private boolean twoFingers;
        private boolean dragged;

        PageMapView(Context context, MapTileProviderBasic tiles) {
            super(context, tiles);
            touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX();
                    downY = event.getY();
                    twoFingers = false;
                    dragged = false;
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    // From here the gesture is the map's, in every direction,
                    // including after one of the two fingers lifts.
                    twoFingers = true;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (twoFingers) break;
                    float dx = Math.abs(event.getX() - downX);
                    float dy = Math.abs(event.getY() - downY);
                    if (!dragged && Math.max(dx, dy) > touchSlop) {
                        dragged = true;
                        // A mostly vertical drag is about to be taken by the
                        // page, which is what it was meant for; only a
                        // sideways one is left with nothing to do.
                        if (dx > dy) showGestureHint();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    // The map saw a finger go down and come up without seeing
                    // it move, which it would take for a tap. Two quick
                    // swipes would zoom it in.
                    if (dragged && !twoFingers) {
                        MotionEvent cancel = MotionEvent.obtain(event);
                        cancel.setAction(MotionEvent.ACTION_CANCEL);
                        super.dispatchTouchEvent(cancel);
                        cancel.recycle();
                        return true;
                    }
                    break;
                default:
                    break;
            }
            return super.dispatchTouchEvent(event);
        }
    }

    private void showGestureHint() {
        removeCallbacks(hideGestureHint);
        gestureHint.animate().cancel();
        gestureHint.setVisibility(VISIBLE);
        gestureHint.animate().alpha(1f).setDuration(150).start();
        // Timed here rather than as the animation's start delay, which
        // "Remove animations" scales to nothing along with the fade.
        postDelayed(hideGestureHint, 1800);
    }

    private void hideGestureHint() {
        gestureHint.animate().alpha(0f).setDuration(250)
            .withEndAction(() -> gestureHint.setVisibility(GONE)).start();
    }

    // ── Lifecycle, forwarded by the screen ───────────────────────────────

    public void onResume() {
        if (map != null) map.onResume();
    }

    public void onPause() {
        if (map != null) map.onPause();
    }

    /** Lets go of the map's tile threads and cache, which would otherwise outlive the screen. */
    public void onDestroy() {
        removeCallbacks(hideGestureHint);
        MapView map = this.map;
        if (map == null) return;
        // The label layer is only among the map's overlays while the plain
        // look is on; otherwise it has to be let go of by hand.
        if (labels != null && !map.getOverlays().contains(labels)) labels.onDetach(map);
        map.onDetach();
        markers.setMap(null);
        this.map = null;
    }

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
    }
}
