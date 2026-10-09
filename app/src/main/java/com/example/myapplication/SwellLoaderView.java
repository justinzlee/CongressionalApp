package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.myapplication.util.FrameLoop;

/**
 * What a reading looks like while it is still on its way: a small pill of sea
 * with a lifebuoy riding the swell.
 *
 * <p>It stands where the number will land — in a stat tile, beside a card's
 * title, in the corner of the wave card — so a screen that is still asking
 * reads as busy rather than as a set of empty boxes and dashes. Two swells roll
 * through at different speeds so the water has depth, the whole sea breathes up
 * and down, and the buoy sits on the front swell and leans with its slope, the
 * same ring that marks a lifeguard post everywhere else in the app.
 *
 * <p>Small on purpose: a few dozen points of path per frame, drawn only while
 * the view is on screen (see {@link FrameLoop}), and a single held frame under
 * "Remove animations". It is a picture, not a progress bar — it says nothing
 * about how long is left, because nothing here knows.
 *
 * <p>Every colour is a resource with a night variant, like the wave card's, so
 * it sits on a light tile as daylight water and on a dark one as night water.
 */
public class SwellLoaderView extends View {

    private static final float DEFAULT_WIDTH_DP = 60f;
    private static final float DEFAULT_HEIGHT_DP = 24f;

    // The two swells, as fractions of the view: where each sits at rest, how
    // far it rises, how long one wavelength is, and the seconds it takes to
    // roll one wavelength by. The front one is shorter and quicker, which is
    // what makes the back one read as further away.
    private static final float BACK_BASE = 0.50f;
    private static final float BACK_AMP = 0.09f;
    private static final float BACK_LENGTH = 0.95f;
    private static final float BACK_PERIOD_S = 2.6f;

    private static final float FRONT_BASE = 0.64f;
    private static final float FRONT_AMP = 0.11f;
    private static final float FRONT_LENGTH = 0.72f;
    private static final float FRONT_PERIOD_S = 1.7f;

    /** The whole sea rises and falls this much, over this many seconds. */
    private static final float BREATHE = 0.04f;
    private static final float BREATHE_PERIOD_S = 3.2f;

    /** The buoy: its outer radius against the view's height, and where it floats. */
    private static final float BUOY_RADIUS = 0.24f;
    private static final float BUOY_X = 0.46f;
    /** How far its centre rides above the water line, in buoy radii. */
    private static final float BUOY_FREEBOARD = 0.3f;
    /** A buoy rocks less than the water under it: some of the slope, not all. */
    private static final float BUOY_LEAN = 0.75f;

    /** The sun, low in the corner of the sky. */
    private static final float SUN_X = 0.83f;
    private static final float SUN_Y = 0.27f;
    private static final float SUN_RADIUS = 0.12f;

    /** Points per swell. Enough to read as a curve at this size, and no more. */
    private static final int STEPS = 28;

    /** A still frame partway through a roll, so the held frame still has a crest. */
    private static final double HELD_FRAME_S = 0.45;

    /**
     * One clock for every loader in the process, so all the loaders on a
     * screen roll in step - one sea seen through several windows - however
     * far apart they were shown.
     */
    private static final long EPOCH_NANOS = System.nanoTime();

    private final int sky;
    private final int sun;
    private final int swellBack;
    private final int swellFront;
    private final int swellDeep;
    private final int buoy;
    private final int buoyBand;
    private final int buoyRim;
    private final int foam;
    private final float density;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Square-ended, so the buoy's bands meet edge to edge instead of overlapping. */
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path wave = new Path();
    private final RectF ring = new RectF();
    private final FrameLoop loop;

    private Shader frontShader;

    public SwellLoaderView(Context context) {
        this(context, null);
    }

    public SwellLoaderView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;

        sky = ContextCompat.getColor(context, R.color.loader_sky);
        sun = ContextCompat.getColor(context, R.color.loader_sun);
        swellBack = ContextCompat.getColor(context, R.color.loader_swell_back);
        swellFront = ContextCompat.getColor(context, R.color.loader_swell_front);
        swellDeep = ContextCompat.getColor(context, R.color.loader_swell_deep);
        buoy = ContextCompat.getColor(context, R.color.loader_buoy);
        buoyBand = ContextCompat.getColor(context, R.color.loader_buoy_band);
        buoyRim = ContextCompat.getColor(context, R.color.loader_buoy_rim);
        foam = ContextCompat.getColor(context, R.color.loader_foam);

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeCap(Paint.Cap.BUTT);

        loop = new FrameLoop(this);

        // Read out as what it stands for, unless the layout says otherwise -
        // a loader beside a line that already says "Checking..." is left out.
        if (getContentDescription() == null) {
            setContentDescription(context.getString(R.string.loading));
        }
        if (getImportantForAccessibility() == IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        }

        // A pill, clipped by its outline: rounded-rect outlines are clipped
        // with anti-aliased edges, where a clip path would leave them jagged.
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                    view.getHeight() / 2f);
            }
        });
        setClipToOutline(true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(
            resolveSize(Math.round(DEFAULT_WIDTH_DP * density), widthMeasureSpec),
            resolveSize(Math.round(DEFAULT_HEIGHT_DP * density), heightMeasureSpec));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        frontShader = h > 0
            ? new LinearGradient(0f, h * (FRONT_BASE - FRONT_AMP), 0f, h,
                swellFront, swellDeep, Shader.TileMode.CLAMP)
            : null;
        invalidateOutline();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        loop.attach();
    }

    @Override
    protected void onDetachedFromWindow() {
        loop.detach();
        super.onDetachedFromWindow();
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        loop.onVisibilityAggregated(isVisible);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        double t = secondsIn();
        float breathe = BREATHE * h * (float) Math.sin(2 * Math.PI * t / BREATHE_PERIOD_S);

        fill.setShader(null);
        fill.setColor(sky);
        canvas.drawRect(0f, 0f, w, h, fill);

        fill.setColor(sun);
        canvas.drawCircle(SUN_X * w, SUN_Y * h, SUN_RADIUS * h, fill);

        // The far swell, then the buoy, then the near swell over the buoy's
        // lower half, so it floats in the water rather than on top of a line.
        float backShift = roll(t, BACK_PERIOD_S, BACK_LENGTH * w);
        buildWave(w, h, BACK_BASE * h + breathe, BACK_AMP * h, BACK_LENGTH * w, backShift, true);
        fill.setColor(swellBack);
        canvas.drawPath(wave, fill);

        float frontShift = roll(t, FRONT_PERIOD_S, FRONT_LENGTH * w);
        float frontBase = FRONT_BASE * h + breathe;
        drawBuoy(canvas, w, h, frontBase, frontShift);

        buildWave(w, h, frontBase, FRONT_AMP * h, FRONT_LENGTH * w, frontShift, true);
        fill.setColor(swellFront);
        fill.setShader(frontShader);
        canvas.drawPath(wave, fill);
        fill.setShader(null);

        // A thin line of light along the near crest.
        buildWave(w, h, frontBase, FRONT_AMP * h, FRONT_LENGTH * w, frontShift, false);
        stroke.setColor(foam);
        stroke.setStrokeWidth(Math.max(1f, 0.9f * density));
        canvas.drawPath(wave, stroke);

        loop.next();
    }

    /**
     * A ring buoy, banded in the flag colour, riding the near swell at its
     * fixed spot along the pill and leaning with the slope of the water under
     * it.
     */
    private void drawBuoy(Canvas canvas, int w, int h, float frontBase, float frontShift) {
        float radius = BUOY_RADIUS * h;
        float x = BUOY_X * w;
        float amp = FRONT_AMP * h;
        float length = FRONT_LENGTH * w;
        float surface = waveY(x, frontBase, amp, length, frontShift);
        float y = surface - radius * BUOY_FREEBOARD;

        // The water's slope under the buoy, from the derivative of the swell.
        double phase = 2 * Math.PI * (x + frontShift) / length;
        double slope = amp * (2 * Math.PI / length) * Math.sin(phase);
        float lean = (float) Math.toDegrees(Math.atan(slope)) * BUOY_LEAN;

        float band = radius * 0.5f;
        float centreLine = radius - band / 2f;
        ring.set(x - centreLine, y - centreLine, x + centreLine, y + centreLine);

        // A darker ring a hair wider than the buoy outlines it, so the white
        // bands still read against a pale sky.
        ringPaint.setColor(buoyRim);
        ringPaint.setStrokeWidth(band + Math.max(1f, 0.9f * density));
        canvas.drawOval(ring, ringPaint);

        ringPaint.setColor(buoyBand);
        ringPaint.setStrokeWidth(band);
        canvas.drawOval(ring, ringPaint);

        // Four coloured eighths between the white ones, the classic pattern.
        ringPaint.setColor(buoy);
        for (int quarter = 0; quarter < 4; quarter++) {
            canvas.drawArc(ring, lean + quarter * 90f, 45f, false, ringPaint);
        }
    }

    /** The swell as a cosine, filled down to the bottom edge or left open. */
    private void buildWave(int w, int h, float base, float amp, float length,
                           float shift, boolean closed) {
        wave.reset();
        float step = w / (float) STEPS;
        wave.moveTo(0f, waveY(0f, base, amp, length, shift));
        for (int i = 1; i <= STEPS; i++) {
            float x = i * step;
            wave.lineTo(x, waveY(x, base, amp, length, shift));
        }
        if (closed) {
            wave.lineTo(w, h);
            wave.lineTo(0f, h);
            wave.close();
        }
    }

    private static float waveY(float x, float base, float amp, float length, float shift) {
        return base - amp * (float) Math.cos(2 * Math.PI * (x + shift) / length);
    }

    /**
     * How far a swell has rolled, one wavelength per period, wrapped. Worked
     * in double: the shared clock runs as long as the process does, and a
     * float of seconds that size has too little left after the point to
     * move smoothly.
     */
    private static float roll(double t, float periodS, float length) {
        return (float) ((t % periodS) / periodS) * length;
    }

    /** Seconds on the shared clock, or a fixed moment when held still. */
    private double secondsIn() {
        if (isInEditMode() || loop.isReduced()) return HELD_FRAME_S;
        return (System.nanoTime() - EPOCH_NANOS) / 1_000_000_000.0;
    }
}
