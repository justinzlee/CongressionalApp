package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.example.myapplication.util.Motion;

/**
 * Everything on the launch screen except the mark and the words: the open sea
 * behind them, the sun over them, and three bands of surf along the bottom.
 *
 * <p>The mark is {@link FlaglineMarkView}'s job, and the wordmark and tagline
 * are real text views, so this one draws only what the layout cannot: an
 * elliptical gradient, two hard-edged halos, and waves that scroll and bob on
 * different clocks. All of it sits on the deep sea in both themes — the launch
 * screen is the brand's own ground, not a surface that follows the phone.
 *
 * <p>Geometry is in the design's own units, which are CSS pixels against a
 * 390 x 844 reference phone, read here as dp.
 */
public class FlaglineSplashView extends View {

    // The sun, and the two rings of light around it.
    private static final float SUN_TOP = 58f;
    private static final float SUN_SIZE = 24f;
    private static final float HALO_INNER = 30f;
    private static final float HALO_OUTER = 62f;
    private static final int HALO_INNER_ALPHA = 26;   // .10
    private static final int HALO_OUTER_ALPHA = 15;   // .06
    private static final long SUN_DELAY = 100, SUN_DURATION = 1100;
    private static final float SUN_RISE = 52f;
    private static final float SUN_START_SCALE = .55f;

    // The surf. Three bands of the same wave at different depths, sizes and
    // speeds, each drawn twice as wide as the screen so the scroll can loop.
    private static final float BAND_HEIGHT = 200f;
    /** One full crest-to-crest wave. */
    private static final float PERIOD = 195f;
    /**
     * How far a band travels before it starts over. Two whole periods, so the
     * band it wraps back to lines up with the one it left and the loop never
     * shows a seam.
     */
    private static final float SCROLL = 2f * PERIOD;
    private static final float[] BASELINE = {80f, 104f, 132f};
    private static final float[] AMPLITUDE = {30f, 26f, 22f};
    /** How far each band's box hangs below the foot of the screen. */
    private static final float[] DROP = {14f, 30f, 46f};
    private static final long[] SCROLL_MS = {13000L, 9000L, 6500L};
    private static final long[] BOB_MS = {7500L, 9500L, 6000L};
    private static final long[] BOB_DELAY = {0L, 600L, 300L};
    private static final float BOB_RISE = 9f;
    private static final long SWELL_DELAY = 350, SWELL_DURATION = 1000;
    private static final float SWELL_RISE = 130f;

    /**
     * A point on the clock past every entry animation, used to paint the
     * settled frame before the screen plays and when motion is turned off.
     */
    private static final long SETTLED = 10_000L;

    private final float density;
    private final Paint seaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sunPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path[] bands = new Path[3];
    private final int[] bandColors = new int[3];
    private final int deepColor;
    private final int seaColor;
    private final int sunColor;

    /** When {@link #play()} was called, or 0 while the screen is not running. */
    private long startedAt;
    private boolean reduced;

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            if (startedAt == 0L) return;
            invalidate();
            postOnAnimation(this);
        }
    };

    public FlaglineSplashView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;

        deepColor = ContextCompat.getColor(context, R.color.brand_deep);
        seaColor = ContextCompat.getColor(context, R.color.brand_sea);
        sunColor = ContextCompat.getColor(context, R.color.brand_sun);
        bandColors[0] = ContextCompat.getColor(context, R.color.brand_sea);
        bandColors[1] = withAlpha(ContextCompat.getColor(context, R.color.brand_mid), 217);
        bandColors[2] = withAlpha(ContextCompat.getColor(context, R.color.brand_shallow), 140);

        // Stands in for the gradient until the view has a size to build it against.
        seaPaint.setColor(deepColor);
    }

    /**
     * Runs the entry sequence from the top. Called once, on a cold start; with
     * animations turned off it paints the settled frame and stops there.
     */
    public void play() {
        reduced = Motion.isReduced(getContext());
        startedAt = SystemClock.uptimeMillis();
        if (reduced) {
            invalidate();
        } else {
            removeCallbacks(frame);
            postOnAnimation(frame);
        }
    }

    /** Stops the frame loop; the screen is on its way out or off-screen. */
    public void stop() {
        startedAt = 0L;
        removeCallbacks(frame);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(frame);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w == 0 || h == 0) return;

        // An ellipse 120% of the width by 70% of the height, centred near the
        // top edge. Android's radial shader is circular, so it is built at the
        // horizontal radius and squashed vertically by a local matrix.
        float cx = w * .5f;
        float cy = h * .08f;
        float rx = w * 1.2f;
        float ry = h * .7f;
        RadialGradient sea = new RadialGradient(cx, cy, rx,
            new int[]{seaColor, deepColor, deepColor},
            new float[]{0f, .62f, 1f}, Shader.TileMode.CLAMP);
        Matrix squash = new Matrix();
        squash.setScale(1f, ry / rx, cx, cy);
        sea.setLocalMatrix(squash);
        seaPaint.setShader(sea);

        for (int i = 0; i < bands.length; i++) {
            bands[i] = buildBand(i, w, h);
        }
    }

    /**
     * One band of surf, at rest: a run of S-curves along its baseline, closed
     * down to the foot of its own box so the fill reaches past the screen edge.
     * A period of slack is drawn off each side to cover the scroll.
     */
    private Path buildBand(int index, int w, int h) {
        float boxBottom = h + dp(DROP[index]);
        float base = boxBottom - dp(BAND_HEIGHT) + dp(BASELINE[index]);
        float amp = dp(AMPLITUDE[index]);
        float half = dp(PERIOD) * .5f;
        float startX = -dp(PERIOD);
        float endX = w + dp(SCROLL) + dp(PERIOD);

        Path path = new Path();
        path.moveTo(startX, base);
        float x = startX;
        float y = base;
        boolean rising = true;
        while (x < endX) {
            float next = rising ? base - amp : base;
            path.cubicTo(x + half * .5f, y, x + half * .5f, next, x + half, next);
            x += half;
            y = next;
            rising = !rising;
        }
        path.lineTo(x, boxBottom);
        path.lineTo(startX, boxBottom);
        path.close();
        return path;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        // Before play(), and whenever motion is turned off, every element is
        // drawn where it comes to rest.
        long now = startedAt == 0L || reduced
            ? SETTLED : SystemClock.uptimeMillis() - startedAt;

        canvas.drawPaint(seaPaint);
        drawSun(canvas, w, now);
        drawSurf(canvas, now);
    }

    private void drawSun(Canvas canvas, int w, long now) {
        float p = Motion.SETTLE.getInterpolation(Motion.phase(now, SUN_DELAY, SUN_DURATION));
        float scale = SUN_START_SCALE + (1f - SUN_START_SCALE) * p;
        // Opaque by the time it is 60% of the way up, as the design has it.
        float alpha = Math.min(1f, p / .6f);
        if (alpha <= 0f) return;

        float cx = w * .5f;
        float cy = dp(SUN_TOP) + dp(SUN_SIZE) * .5f + dp(SUN_RISE) * (1f - p);
        float r = dp(SUN_SIZE) * .5f * scale;

        // The halos are hard-edged rings rather than a blur - the design draws
        // them as two spread shadows, so each is a flat disc of its own.
        sunPaint.setColor(withAlpha(sunColor, (int) (HALO_OUTER_ALPHA * alpha)));
        canvas.drawCircle(cx, cy, r + dp(HALO_OUTER) * scale, sunPaint);
        sunPaint.setColor(withAlpha(sunColor, (int) (HALO_INNER_ALPHA * alpha)));
        canvas.drawCircle(cx, cy, r + dp(HALO_INNER) * scale, sunPaint);
        sunPaint.setColor(withAlpha(sunColor, (int) (255 * alpha)));
        canvas.drawCircle(cx, cy, r, sunPaint);
    }

    private void drawSurf(Canvas canvas, long now) {
        // The whole stack rides up once, then each band keeps its own two
        // rhythms: sideways at a constant speed, and a slow lift and fall.
        float swell = dp(SWELL_RISE) * (1f - Motion.SETTLE.getInterpolation(
            Motion.phase(now, SWELL_DELAY, SWELL_DURATION)));

        for (int i = 0; i < bands.length; i++) {
            if (bands[i] == null) continue;
            float scroll = 0f;
            float bob = 0f;
            if (!reduced) {
                scroll = -dp(SCROLL) * ((now % SCROLL_MS[i]) / (float) SCROLL_MS[i]);
                if (now > BOB_DELAY[i]) {
                    double turn = 2 * Math.PI * ((now - BOB_DELAY[i]) % BOB_MS[i]) / BOB_MS[i];
                    bob = -dp(BOB_RISE) * (float) ((1 - Math.cos(turn)) * .5);
                }
            }
            bandPaint.setColor(bandColors[i]);
            canvas.save();
            canvas.translate(scroll, swell + bob);
            canvas.drawPath(bands[i], bandPaint);
            canvas.restore();
        }
    }

    private float dp(float value) {
        return value * density;
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)),
            Color.red(color), Color.green(color), Color.blue(color));
    }
}
