package com.example.myapplication;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the beach map draws over its imagery: the lifeguard towers, your
 * own dot with its pulse, and — once you are off the edge of the map — an arrow
 * on that edge pointing back at you, with how far away you are beneath it.
 *
 * <p>A view laid over the map rather than one of the map library's own
 * overlays, so the pulse can animate by redrawing a few circles instead of every
 * tile underneath. It reads the map's projection each time it draws, and
 * {@link BeachMapCard} asks it to draw whenever the map does, so the markers
 * keep up with the imagery frame for frame.
 */
public class BeachMapMarkers extends View {

    /** One help point on the map, and the character in its circle. */
    static final class Tower {
        final GeoPoint point;
        final String label;

        Tower(GeoPoint point, String label) {
            this.point = point;
            this.label = label;
        }
    }

    // Sizes are the design's, in dp.
    private static final float TOWER_RADIUS = 11f;
    private static final float TOWER_EDGE = 2f;
    private static final float DOT_RADIUS = 8f;
    private static final float DOT_RIM = 2.5f;
    private static final float ARROW_BOX = 26f;
    private static final float ARROW_GAP = 4f;
    private static final float CHIP_PAD_X = 7f;
    private static final float CHIP_PAD_Y = 3f;
    private static final float CHIP_EDGE = 1f;

    /** How near an edge you can be before the arrow takes over from your dot. */
    private static final float POINTER_INSET = 30f;
    /** Room kept for the arrow and its pill between that line and where it sits. */
    private static final float POINTER_EDGE_X = 32f;
    private static final float POINTER_EDGE_Y = 26f;
    private static final long POINTER_FADE_MS = 250L;

    private static final long PULSE_MS = 2400L;
    /** Each beat grows for its first 70% and rests, invisible, for the rest. */
    private static final float PULSE_GROW = 0.7f;
    private static final float PULSE_SCALE = 2.6f;
    private static final float PULSE_ALPHA = 0.55f;
    private static final PathInterpolator EASE_OUT = new PathInterpolator(0f, 0f, 0.58f, 1f);

    private final float dp;

    private final Paint towerEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint towerFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint towerText = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint dotHalo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotRim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pulseFill = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path arrow = new Path();
    private final Paint arrowShade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowOutline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowRim = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint chipFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF chip = new RectF();

    private final Point scratch = new Point();
    private final Rect onScreen = new Rect();

    @Nullable private MapView map;
    private List<Tower> towers = new ArrayList<>();
    @Nullable private GeoPoint user;
    private boolean metric;

    private final ValueAnimator pulse;
    private float pulsePhase;
    private boolean visible;
    /** Whether your dot was inside the map the last time it was drawn. */
    private boolean dotShowing;

    /** Where the arrow last was, kept so it can fade out where it stood. */
    @Nullable private float[] pointer;
    private boolean pointerWanted;
    private float pointerAlpha;
    @Nullable private ValueAnimator pointerFade;
    private String distance = "";

    private final ViewTreeObserver.OnScrollChangedListener onPageScroll = this::updatePulse;

    public BeachMapMarkers(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
        float sp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f,
            getResources().getDisplayMetrics());

        towerEdge.setColor(color(R.color.map_tower_ink));
        towerEdge.setShadowLayer(6f * dp, 0f, 2f * dp, 0x8C000000);
        towerFill.setColor(color(R.color.map_tower));
        towerText.setColor(color(R.color.map_tower_ink));
        towerText.setTextSize(11f * sp);
        towerText.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        towerText.setTextAlign(Paint.Align.CENTER);

        // A hairline of shadow round the white rim, and a soft drop beneath.
        dotHalo.setColor(0x80000000);
        dotHalo.setShadowLayer(6f * dp, 0f, 2f * dp, 0x99000000);
        dotRim.setColor(color(R.color.map_user_rim));
        dotFill.setColor(color(R.color.map_user));
        pulseFill.setColor(color(R.color.map_user));

        // The design's 26-unit arrow: a chevron with a notch in its tail.
        arrow.moveTo(13f * dp, 2.5f * dp);
        arrow.lineTo(20.5f * dp, 20.5f * dp);
        arrow.lineTo(13f * dp, 16.4f * dp);
        arrow.lineTo(5.5f * dp, 20.5f * dp);
        arrow.close();
        arrowShade.setColor(color(R.color.map_arrow_shade));
        arrowShade.setShadowLayer(4f * dp, 0f, 2f * dp, 0xBF000000);
        arrowOutline.setColor(color(R.color.map_arrow_outline));
        arrowOutline.setStyle(Paint.Style.STROKE);
        arrowOutline.setStrokeWidth(2.2f * dp);
        arrowOutline.setStrokeJoin(Paint.Join.ROUND);
        arrowFill.setColor(color(R.color.map_arrow));
        arrowRim.setColor(color(R.color.map_arrow_rim));
        arrowRim.setStyle(Paint.Style.STROKE);
        arrowRim.setStrokeWidth(0.9f * dp);
        arrowRim.setStrokeJoin(Paint.Join.ROUND);

        chipFill.setColor(color(R.color.map_you_chip));
        chipEdge.setColor(color(R.color.map_hud_edge));
        chipEdge.setStyle(Paint.Style.STROKE);
        chipEdge.setStrokeWidth(CHIP_EDGE * dp);
        chipText.setColor(color(R.color.map_you_text));
        chipText.setTextSize(10f * sp);
        chipText.setTypeface(Typeface.MONOSPACE);
        chipText.setLetterSpacing(0.04f);
        chipText.setTextAlign(Paint.Align.CENTER);

        pulse = ValueAnimator.ofFloat(0f, 1f);
        pulse.setDuration(PULSE_MS);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setInterpolator(new LinearInterpolator());
        pulse.addUpdateListener(a -> {
            pulsePhase = (Float) a.getAnimatedValue();
            invalidate();
        });
    }

    void setMap(@Nullable MapView map) {
        this.map = map;
        invalidate();
    }

    void setTowers(List<Tower> towers) {
        this.towers = towers;
        invalidate();
    }

    void setUser(@Nullable GeoPoint user) {
        this.user = user;
        invalidate();
    }

    void setMetric(boolean metric) {
        this.metric = metric;
        invalidate();
    }

    // ── The pulse runs only while somebody could be looking at it ─────────

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnScrollChangedListener(onPageScroll);
    }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnScrollChangedListener(onPageScroll);
        pulse.cancel();
        if (pointerFade != null) pointerFade.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        visible = isVisible;
        updatePulse();
    }

    /**
     * Starts the pulse when your dot is on the map and the map is on screen,
     * and stops it otherwise. The map sits well down a long page, and an
     * animation left running out of sight would wake the phone sixty times a
     * second for nothing.
     */
    private void updatePulse() {
        boolean run = visible && dotShowing && isAttachedToWindow()
            && getGlobalVisibleRect(onScreen);
        if (run && !pulse.isStarted()) {
            pulse.start();
        } else if (!run && pulse.isStarted()) {
            pulse.cancel();
        }
    }

    // ── Drawing ──────────────────────────────────────────────────────────

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        MapView map = this.map;
        if (map == null) return;
        Projection projection = map.getProjection();

        for (Tower tower : towers) {
            projection.toPixels(tower.point, scratch);
            drawTower(canvas, scratch.x, scratch.y, tower.label);
        }

        boolean showing = false;
        float[] wanted = null;
        if (user != null) {
            projection.toPixels(user, scratch);
            float x = scratch.x;
            float y = scratch.y;
            float reach = (DOT_RADIUS + DOT_RIM) * PULSE_SCALE * dp;
            showing = x > -reach && x < getWidth() + reach
                && y > -reach && y < getHeight() + reach;
            if (showing) drawUser(canvas, x, y);

            wanted = BeachMapMath.pointer(getWidth(), getHeight(), x, y,
                POINTER_INSET * dp, POINTER_EDGE_X * dp, POINTER_EDGE_Y * dp);
            distance = getContext().getString(R.string.map_you,
                BeachMapMath.distance(user.distanceToAsDouble(map.getMapCenter()), metric));
        }

        if (showing != dotShowing) {
            dotShowing = showing;
            post(this::updatePulse);
        }

        if (wanted != null) pointer = wanted;
        fadePointer(wanted != null);
        if (pointer != null && pointerAlpha > 0f) drawPointer(canvas, pointer, pointerAlpha);
    }

    private void drawTower(Canvas canvas, float x, float y, String label) {
        canvas.drawCircle(x, y, (TOWER_RADIUS + TOWER_EDGE) * dp, towerEdge);
        canvas.drawCircle(x, y, TOWER_RADIUS * dp, towerFill);
        Paint.FontMetrics metrics = towerText.getFontMetrics();
        canvas.drawText(label, x, y - (metrics.ascent + metrics.descent) / 2f, towerText);
    }

    /**
     * Your dot, and the ring pulsing out of it. The ring is drawn over the dot,
     * not under: at the start of each beat it tints the whole dot, rim and all,
     * before it spreads and fades, which is the look the design has.
     */
    private void drawUser(Canvas canvas, float x, float y) {
        float outer = (DOT_RADIUS + DOT_RIM) * dp;
        canvas.drawCircle(x, y, outer + dp, dotHalo);
        canvas.drawCircle(x, y, outer, dotRim);
        canvas.drawCircle(x, y, DOT_RADIUS * dp, dotFill);

        float grow = pulsePhase < PULSE_GROW ? EASE_OUT.getInterpolation(pulsePhase / PULSE_GROW) : 1f;
        float alpha = PULSE_ALPHA * (1f - grow);
        if (alpha > 0.004f) {
            pulseFill.setAlpha(Math.round(alpha * 255));
            canvas.drawCircle(x, y, outer * (1f + (PULSE_SCALE - 1f) * grow), pulseFill);
        }
    }

    /** The arrow, turned to face you, over a pill saying how far you are. */
    private void drawPointer(Canvas canvas, float[] at, float alpha) {
        Paint.FontMetrics metrics = chipText.getFontMetrics();
        float chipHeight = chipText.getFontSpacing() + 2f * (CHIP_PAD_Y + CHIP_EDGE) * dp;
        float chipWidth = chipText.measureText(distance) + 2f * (CHIP_PAD_X + CHIP_EDGE) * dp;
        float box = ARROW_BOX * dp;
        float column = box + ARROW_GAP * dp + chipHeight;
        float top = at[1] - column / 2f;

        int layer = alpha < 1f
            ? canvas.saveLayerAlpha(at[0] - Math.max(box, chipWidth), top - box,
                at[0] + Math.max(box, chipWidth), top + column + box, Math.round(alpha * 255))
            : canvas.save();

        canvas.save();
        canvas.translate(at[0] - box / 2f, top);
        canvas.rotate(at[2], box / 2f, box / 2f);
        canvas.drawPath(arrow, arrowShade);
        canvas.drawPath(arrow, arrowOutline);
        canvas.drawPath(arrow, arrowFill);
        canvas.drawPath(arrow, arrowRim);
        canvas.restore();

        float chipTop = top + box + ARROW_GAP * dp;
        chip.set(at[0] - chipWidth / 2f, chipTop, at[0] + chipWidth / 2f, chipTop + chipHeight);
        float radius = chipHeight / 2f;
        canvas.drawRoundRect(chip, radius, radius, chipFill);
        float half = CHIP_EDGE * dp / 2f;
        chip.inset(half, half);
        canvas.drawRoundRect(chip, radius - half, radius - half, chipEdge);
        float middle = chipTop + chipHeight / 2f;
        canvas.drawText(distance, at[0], middle - (metrics.ascent + metrics.descent) / 2f, chipText);

        canvas.restoreToCount(layer);
    }

    /** Fades the arrow in when you leave the map and out when you come back onto it. */
    private void fadePointer(boolean wanted) {
        if (wanted == pointerWanted) return;
        pointerWanted = wanted;
        if (pointerFade != null) pointerFade.cancel();
        pointerFade = ValueAnimator.ofFloat(pointerAlpha, wanted ? 1f : 0f);
        pointerFade.setDuration(POINTER_FADE_MS);
        pointerFade.addUpdateListener(a -> {
            pointerAlpha = (Float) a.getAnimatedValue();
            invalidate();
        });
        pointerFade.start();
    }

    private int color(int res) {
        return ContextCompat.getColor(getContext(), res);
    }
}
