package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * A light-blue "ocean" plate that pulls back like an outgoing tide. The water
 * line is a travelling swell that sweeps down the screen, trailing a few foam
 * crests that thin out behind it, and the whole sheet fades as it goes -
 * handing the app over with a beach-flavoured transition rather than a plain
 * fade.
 */
public class WaveRevealOverlay extends View {
    /** Foam lines left behind above the water's edge. */
    private static final int FOAM_CRESTS = 3;
    private static final float SWELL_HEIGHT_DP = 15f;
    private static final float WAVELENGTH_DP = 240f;
    private static final float CREST_GAP_DP = 24f;
    /** Sheet is solid until this point, then dissolves over the remainder. */
    private static final float FADE_START = 0.45f;
    /** Matches the near-opaque look the welcome sheet had before. */
    private static final int WATER_ALPHA = 250;
    private static final float SAMPLE_STEP_PX = 6f;

    private final Paint waterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint foamPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path wavePath = new Path();

    private final float swellHeightPx;
    private final float wavelengthPx;
    private final float crestGapPx;

    private int deepColor = Color.TRANSPARENT;
    private int shallowColor = Color.TRANSPARENT;
    private float progress = 0f; // 0 = fully covered, 1 = fully revealed

    public WaveRevealOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = context.getResources().getDisplayMetrics().density;
        swellHeightPx = SWELL_HEIGHT_DP * density;
        wavelengthPx = WAVELENGTH_DP * density;
        crestGapPx = CREST_GAP_DP * density;

        foamPaint.setStyle(Paint.Style.STROKE);
        foamPaint.setStrokeWidth(3f * density);
        foamPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    /**
     * @param deepColor    water colour at the top of the screen
     * @param shallowColor water colour at the bottom, nearest "shore"
     * @param foamColor    colour of the surf lines along each crest
     */
    public void setWaterColors(int deepColor, int shallowColor, int foamColor) {
        this.deepColor = deepColor;
        this.shallowColor = shallowColor;
        foamPaint.setColor(foamColor);
        updateGradient();
        invalidate();
    }

    public void setProgress(float progress) {
        this.progress = progress;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateGradient();
    }

    private void updateGradient() {
        int h = getHeight();
        if (h <= 0) return;
        waterPaint.setShader(new LinearGradient(
            0f, 0f, 0f, h, deepColor, shallowColor, Shader.TileMode.CLAMP));
    }

    /** Two summed sines so the swell reads as water rather than a clean sine. */
    private float waveY(float x, float baseY, float phase) {
        double primary = Math.sin(2 * Math.PI * x / wavelengthPx + phase);
        double ripple = Math.sin(2 * Math.PI * x / (wavelengthPx * 0.45f) + phase * 1.7f);
        return baseY + swellHeightPx * (float) (primary + 0.35 * ripple);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        float fade = 1f;
        if (progress > FADE_START) {
            fade = 1f - (progress - FADE_START) / (1f - FADE_START);
        }
        fade = Math.max(0f, Math.min(1f, fade));
        if (fade <= 0f) return;

        // Water line starts above the screen (covering everything) and travels
        // off the bottom edge; the phase shift makes the swell move sideways
        // as it recedes.
        float waterLine = -swellHeightPx * 3f + progress * (h + swellHeightPx * 6f);
        float phase = (float) (progress * 5f * Math.PI);

        wavePath.reset();
        wavePath.moveTo(0f, waveY(0f, waterLine, phase));
        for (float x = SAMPLE_STEP_PX; x <= w; x += SAMPLE_STEP_PX) {
            wavePath.lineTo(x, waveY(x, waterLine, phase));
        }
        wavePath.lineTo(w, h);
        wavePath.lineTo(0f, h);
        wavePath.close();
        waterPaint.setAlpha((int) (WATER_ALPHA * fade));
        canvas.drawPath(wavePath, waterPaint);

        // Surf along the water's edge, then progressively fainter crests
        // trailing behind it over the already-revealed area.
        drawCrest(canvas, w, waterLine, phase, (int) (205 * fade));
        for (int i = 1; i <= FOAM_CRESTS; i++) {
            float crestY = waterLine - i * crestGapPx;
            if (crestY < -swellHeightPx * 2f) continue;
            int alpha = (int) (135 * fade * (1f - (float) i / (FOAM_CRESTS + 1)));
            drawCrest(canvas, w, crestY, phase + i * 0.8f, alpha);
        }
    }

    private void drawCrest(Canvas canvas, int w, float baseY, float phase, int alpha) {
        if (alpha <= 0) return;
        wavePath.reset();
        wavePath.moveTo(0f, waveY(0f, baseY, phase));
        for (float x = SAMPLE_STEP_PX; x <= w; x += SAMPLE_STEP_PX) {
            wavePath.lineTo(x, waveY(x, baseY, phase));
        }
        foamPaint.setAlpha(alpha);
        canvas.drawPath(wavePath, foamPaint);
    }
}
