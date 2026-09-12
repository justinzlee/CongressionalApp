package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.core.content.ContextCompat;

import com.example.myapplication.util.UnitConverter;

import java.util.Locale;

/**
 * The water half of the wave-height card: a live sea whose surface sits at the
 * beach's own swell height, read against the scale beside it.
 *
 * Three swells roll across at different speeds so the water has depth, the
 * whole sheet breathes up and down, and the front crest carries a foam line
 * that runs along it. The colour of the water is the reading, blending from
 * green through amber to red as the surf builds (see {@link WaveScale}).
 *
 * The sky and the sea follow the app's theme, like the card around them: the
 * design's night sea on the dark ground, a daylight one on the light ground.
 * Both sets are colour resources, dark in values-night and light in values.
 *
 * Geometry is transcribed from the "Wave Height Card" design, which draws into
 * a 224x250 box stretched to fill the panel. Rather than convert every number,
 * the canvas is scaled to that box and the sea below is drawn in the design's
 * own units; the reading and the crest guide are HTML rather than SVG in the
 * design, so those are drawn in view pixels instead.
 */
public class WaveHeightView extends View {

    private static final float VB_W = 224f;
    private static final float VB_H = 250f;

    // Back, middle and front swell: where each sits at rest, how far it rises,
    // where its crest starts, and how long it takes to roll one wavelength.
    private static final float[] LAYER_BASE = {34f, 46f, 60f};
    private static final float[] LAYER_AMP = {14f, 17f, 20f};
    private static final float[] LAYER_PEAK_X = {40f, 208f, 56f};
    private static final float[] LAYER_PERIOD = {15f, 10f, 7f};
    private static final int FRONT = 2;

    /** The foam's dash and its repeat along the crest, in design units. */
    private static final float FOAM_DASH = 3f;
    private static final float FOAM_PERIOD = 12f;

    /** The front crest at rest, and so the point the sea is levelled against. */
    private static final float CREST_REST_Y = LAYER_BASE[FRONT] - LAYER_AMP[FRONT];
    /** A flat sea still has to read as water, so the crest never sinks past here. */
    private static final float CREST_FLOOR = 208f;

    // Light on the water is white by day and by night alike.
    private static final int CREST_STROKE = 0xCCE8F1F8;
    private static final int FOAM_STROKE = 0x80FFFFFF;
    private static final int SOFT_BAND = 0x12FFFFFF;
    private static final int SPARKLE = 0xFFEEF5FA;

    private final int skyTop;
    private final int skyBottom;
    private final int backTop;
    private final int backBottom;
    private final int midTop;
    private final int midBottom;
    private final int midEdge;
    private final int frontShadow;
    private final int frontMid;
    private final int frontDeep;
    private final int frontBed;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint panelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint numberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path wavePath = new Path();
    private final float density;
    private final float cornerPx;

    private Shader backShader;
    private Shader midShader;
    private Shader frontShader;
    private int shaderCrest;
    private int shaderBody;
    private float shaderGradEnd = -1f;

    private long startNanos;

    private float heightFt;
    private boolean hasReading;

    public WaveHeightView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        cornerPx = 10f * density;

        skyTop = ContextCompat.getColor(context, R.color.wave_card_sky_top);
        skyBottom = ContextCompat.getColor(context, R.color.wave_card_sky_bottom);
        backTop = ContextCompat.getColor(context, R.color.wave_card_back_top);
        backBottom = ContextCompat.getColor(context, R.color.wave_card_back_bottom);
        midTop = ContextCompat.getColor(context, R.color.wave_card_mid_top);
        midBottom = ContextCompat.getColor(context, R.color.wave_card_mid_bottom);
        midEdge = ContextCompat.getColor(context, R.color.wave_card_mid_edge);
        frontShadow = ContextCompat.getColor(context, R.color.wave_card_front_shadow);
        frontMid = ContextCompat.getColor(context, R.color.wave_card_front_mid);
        frontDeep = ContextCompat.getColor(context, R.color.wave_card_front_deep);
        frontBed = ContextCompat.getColor(context, R.color.wave_card_front_bed);
        int glow = ContextCompat.getColor(context, R.color.wave_card_number_glow);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);

        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(density);
        edgePaint.setColor(ContextCompat.getColor(context, R.color.wave_card_edge));

        guidePaint.setStyle(Paint.Style.STROKE);
        guidePaint.setStrokeWidth(density);
        guidePaint.setColor(ContextCompat.getColor(context, R.color.wave_card_guide));

        // The number keeps a soft halo of the sky's own tone - dark at night,
        // light by day - so it stays legible where it overlaps the back swells.
        numberPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        numberPaint.setColor(ContextCompat.getColor(context, R.color.wave_card_number));
        numberPaint.setLetterSpacing(-0.03f);
        numberPaint.setFontFeatureSettings("tnum");
        numberPaint.setShadowLayer(12f * density, 0f, 2f * density, glow);
        unitPaint.setColor(ContextCompat.getColor(context, R.color.wave_card_unit));
        unitPaint.setShadowLayer(10f * density, 0f, 2f * density, glow);

        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerPx);
            }
        });
        setClipToOutline(true);
    }

    /**
     * @param feet       swell height to level the sea at
     * @param hasReading false while no reading has arrived: the sea stays calm
     *                   and the number reads "--" rather than implying a value
     */
    public void setReading(float feet, boolean hasReading) {
        this.heightFt = feet;
        this.hasReading = hasReading;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (h > 0) {
            panelPaint.setShader(new LinearGradient(
                0f, 0f, 0f, h, skyTop, skyBottom, Shader.TileMode.CLAMP));
        }
        invalidateOutline();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        long now = System.nanoTime();
        if (startNanos == 0L) startNanos = now;
        float t = (now - startNanos) / 1_000_000_000f;

        float ratio = Math.max(0f, Math.min(1f, heightFt / WaveScale.MAX_FT));
        float crest = Math.min(VB_H * (1f - ratio), CREST_FLOOR);
        float crestPx = crest / VB_H * h;
        // The sea breathes between +3 and -8 design units over eleven seconds.
        float breathe = -2.5f + 5.5f * (float) Math.cos(2 * Math.PI * t / 11f);

        canvas.drawRect(0f, 0f, w, h, panelPaint);

        canvas.save();
        canvas.scale(w / VB_W, h / VB_H);
        canvas.translate(0f, crest - CREST_REST_Y + breathe);
        drawSwell(canvas, t, crest);
        canvas.restore();

        // The guide points at a height on the scale, so like the marker beside
        // it, it stays away until there is a reading to point at.
        if (hasReading) drawGuide(canvas, w, crestPx);
        drawReading(canvas, h, crestPx);
        canvas.drawRoundRect(0.5f * density, 0.5f * density,
            w - 0.5f * density, h - 0.5f * density, cornerPx, cornerPx, edgePaint);

        if (isShown()) postInvalidateOnAnimation();
    }

    // -- the sea ------------------------------------------------------------

    /** Draws with the canvas already in design units and lifted to the crest. */
    private void drawSwell(Canvas canvas, float t, float crest) {
        ensureShaders(crest);

        fillLayer(canvas, 0, t, backShader);
        fillLayer(canvas, 1, t, midShader);
        strokeLayer(canvas, 1, t, midEdge, 1.2f);

        float shift = shiftFor(FRONT, t);

        // Sunlight hanging behind the crest. Two haloes a wavelength apart, so
        // one is always in frame as the swell rolls.
        for (int i = 0; i < 2; i++) {
            canvas.save();
            canvas.translate(56f - shift + i * VB_W, CREST_REST_Y);
            canvas.scale(86f, 28f);
            canvas.drawCircle(0f, 0f, 1f, glowPaint);
            canvas.restore();
        }

        // The shadow the front wave throws on the water behind it.
        buildWave(wavePath, LAYER_BASE[FRONT] + 6f, LAYER_AMP[FRONT],
            LAYER_PEAK_X[FRONT], shift, true);
        fillPaint.setShader(null);
        fillPaint.setColor(frontShadow);
        canvas.drawPath(wavePath, fillPaint);

        buildWave(wavePath, LAYER_BASE[FRONT], LAYER_AMP[FRONT],
            LAYER_PEAK_X[FRONT], shift, true);
        fillPaint.setColor(Color.WHITE);
        fillPaint.setShader(frontShader);
        canvas.drawPath(wavePath, fillPaint);

        // Lit edge, the foam running along it, then a soft band of spray just
        // under the surface.
        strokeLayer(canvas, FRONT, t, CREST_STROKE, 2.2f);
        drawFoam(canvas, t, shift);
        buildWave(wavePath, LAYER_BASE[FRONT] + 12f, LAYER_AMP[FRONT],
            LAYER_PEAK_X[FRONT], shift, false);
        strokePaint.setColor(SOFT_BAND);
        strokePaint.setStrokeWidth(6f);
        canvas.drawPath(wavePath, strokePaint);

        // Glints on the water, tiled with the swell like the haloes.
        fillPaint.setShader(null);
        fillPaint.setColor(SPARKLE);
        for (int i = 0; i < 2; i++) {
            float dx = i * VB_W - shift;
            fillPaint.setAlpha(217);
            canvas.drawCircle(46f + dx, 42f, 1.6f, fillPaint);
            fillPaint.setAlpha(153);
            canvas.drawCircle(66f + dx, 45f, 1.1f, fillPaint);
        }
        fillPaint.setAlpha(255);
    }

    private void fillLayer(Canvas canvas, int layer, float t, Shader shader) {
        buildWave(wavePath, LAYER_BASE[layer], LAYER_AMP[layer], LAYER_PEAK_X[layer],
            shiftFor(layer, t), true);
        fillPaint.setColor(Color.WHITE);
        fillPaint.setShader(shader);
        canvas.drawPath(wavePath, fillPaint);
    }

    private void strokeLayer(Canvas canvas, int layer, float t, int color, float width) {
        buildWave(wavePath, LAYER_BASE[layer], LAYER_AMP[layer], LAYER_PEAK_X[layer],
            shiftFor(layer, t), false);
        strokePaint.setColor(color);
        strokePaint.setStrokeWidth(width);
        canvas.drawPath(wavePath, strokePaint);
    }

    /**
     * The broken line of foam along the front crest. It rolls with the swell
     * and runs along it as well, five dash periods every three and a half
     * seconds.
     *
     * The dashes are drawn a segment at a time rather than with a
     * {@link DashPathEffect}: a hardware-accelerated canvas ignores path
     * effects on lines, which would leave the foam a solid stroke.
     */
    private void drawFoam(Canvas canvas, float t, float shift) {
        strokePaint.setColor(FOAM_STROKE);
        strokePaint.setStrokeWidth(1.2f);
        float base = LAYER_BASE[FRONT];
        float amp = LAYER_AMP[FRONT];
        float peakX = LAYER_PEAK_X[FRONT];
        // Where the first dash of the pattern falls on screen, once the crest's
        // own roll and the foam's travel along it are both accounted for.
        float run = t / 3.5f * 60f;
        float start = -wrap(shift - run, FOAM_PERIOD);
        for (float x = start; x < VB_W; x += FOAM_PERIOD) {
            float from = Math.max(0f, x);
            float to = Math.min(VB_W, x + FOAM_DASH);
            if (to <= from) continue;
            canvas.drawLine(from, waveY(from, base, amp, peakX, shift),
                to, waveY(to, base, amp, peakX, shift), strokePaint);
        }
    }

    /** Dashed guide across the panel at the height the sea has been set to. */
    private void drawGuide(Canvas canvas, int w, float y) {
        float dash = 3f * density;
        for (float x = 0f; x < w; x += dash * 2f) {
            canvas.drawLine(x, y, Math.min(w, x + dash), y, guidePaint);
        }
    }

    /** {@code v} folded into [0, m), unlike {@code %} for negative values. */
    private static float wrap(float v, float m) {
        return ((v % m) + m) % m;
    }

    /** How far this layer has rolled: one wavelength per its own period. */
    private static float shiftFor(int layer, float t) {
        return (t % LAYER_PERIOD[layer]) / LAYER_PERIOD[layer] * VB_W;
    }

    /**
     * One swell, as a cosine of exactly one wavelength across the panel - the
     * shape the design's chained quadratics draw. Filled layers close well
     * below the panel so they stay full however far the sea has been lifted.
     */
    private void buildWave(Path path, float base, float amp, float peakX,
                           float shift, boolean closed) {
        path.reset();
        float step = VB_W / 96f;
        path.moveTo(0f, waveY(0f, base, amp, peakX, shift));
        for (float x = step; x < VB_W; x += step) {
            path.lineTo(x, waveY(x, base, amp, peakX, shift));
        }
        path.lineTo(VB_W, waveY(VB_W, base, amp, peakX, shift));
        if (closed) {
            path.lineTo(VB_W, VB_H * 3f);
            path.lineTo(0f, VB_H * 3f);
            path.close();
        }
    }

    private static float waveY(float x, float base, float amp, float peakX, float shift) {
        return base - amp * (float) Math.cos(2 * Math.PI * (x + shift - peakX) / VB_W);
    }

    /**
     * The back and middle swells never change colour; the front one and its
     * halo are the reading, so they are rebuilt whenever the height moves.
     */
    private void ensureShaders(float crest) {
        if (backShader == null) {
            backShader = new LinearGradient(0f, 20f, 0f, 110f,
                backTop, backBottom, Shader.TileMode.CLAMP);
            midShader = new LinearGradient(0f, 28f, 0f, 120f,
                midTop, midBottom, Shader.TileMode.CLAMP);
        }
        int crestColor = WaveScale.crestColor(heightFt);
        int bodyColor = WaveScale.bodyColor(heightFt);
        // The ramp spans the crest down to the visible bed, wherever it sits.
        float gradEnd = Math.max(110f, 298f - crest);
        if (frontShader != null && crestColor == shaderCrest
            && bodyColor == shaderBody && gradEnd == shaderGradEnd) {
            return;
        }
        shaderCrest = crestColor;
        shaderBody = bodyColor;
        shaderGradEnd = gradEnd;
        frontShader = new LinearGradient(0f, 34f, 0f, gradEnd,
            new int[] {crestColor, bodyColor, frontMid, frontDeep, frontBed},
            new float[] {0f, 0.13f, 0.4f, 0.72f, 1f}, Shader.TileMode.CLAMP);
        glowPaint.setShader(new RadialGradient(0f, 0f, 1f,
            (crestColor & 0x00FFFFFF) | 0x80000000, crestColor & 0x00FFFFFF,
            Shader.TileMode.CLAMP));
    }

    // -- the reading over the water -----------------------------------------

    private void drawReading(Canvas canvas, int h, float crestPx) {
        numberPaint.setTextSize(0.172f * h);
        unitPaint.setTextSize(0.085f * h);
        // The number rides just above the crest, stopping short of either edge.
        float baseline = Math.max(0.15f * h, Math.min(0.89f * h, crestPx - 0.048f * h));
        String value = hasReading
            ? String.format(Locale.US, "%.1f",
                UnitConverter.waveHeightValue(getContext(), heightFt))
            : "--";
        float x = 12f * density;
        canvas.drawText(value, x, baseline, numberPaint);
        canvas.drawText(UnitConverter.waveHeightUnit(getContext()),
            x + numberPaint.measureText(value) + 4f * density, baseline, unitPaint);
    }
}
