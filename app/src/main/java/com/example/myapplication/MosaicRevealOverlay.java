package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * A solid-color plate made of a grid of small squares. As {@link #setProgress}
 * advances from 0 to 1, the squares shrink and fade away - tiles nearest the
 * center go first, and the effect ripples outward in a circular wave - so the
 * app underneath is revealed through a "shattering" mosaic instead of a plain
 * fade or a single expanding hole.
 */
public class MosaicRevealOverlay extends View {
    private static final float TILE_DP = 20f;
    private static final float FADE_WINDOW = 0.35f; // fraction of total progress a single tile takes to fade
    private static final float JITTER = 0.12f; // slight randomization so the wave doesn't look too mechanical

    // No anti-aliasing: adjacent tiles butt up exactly against each other while
    // fully covered, and AA softens/blends those shared edges into visible
    // hairline seams between them.
    private final Paint tilePaint = new Paint();
    private final float tileSizePx;
    private int fillColor = Color.TRANSPARENT;
    private float progress = 0f; // 0 = fully covered, 1 = fully revealed

    private float[] tileCenterX;
    private float[] tileCenterY;
    private float[] tileDelay; // normalized start point in [0, 1 - FADE_WINDOW]

    public MosaicRevealOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        // Snap to a whole pixel so every tile's edges land on the same
        // integer boundaries - fractional-pixel edges are what actually
        // caused the seams, not just anti-aliasing.
        tileSizePx = Math.round(TILE_DP * context.getResources().getDisplayMetrics().density);
    }

    public void setFillColor(int color) {
        fillColor = color;
        invalidate();
    }

    public void setProgress(float progress) {
        this.progress = progress;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        buildTiles(w, h);
    }

    private void buildTiles(int w, int h) {
        if (w <= 0 || h <= 0 || tileSizePx <= 0f) return;

        int cols = (int) Math.ceil(w / tileSizePx);
        int rows = (int) Math.ceil(h / tileSizePx);
        int count = cols * rows;

        tileCenterX = new float[count];
        tileCenterY = new float[count];
        tileDelay = new float[count];

        float centerX = w / 2f;
        float centerY = h / 2f;
        float maxDistance = (float) Math.hypot(centerX, centerY);
        Random random = new Random(1); // fixed seed: stable layout across relayouts

        int i = 0;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                float cx = col * tileSizePx + tileSizePx / 2f;
                float cy = row * tileSizePx + tileSizePx / 2f;
                float distance = (float) Math.hypot(cx - centerX, cy - centerY);
                float normalized = maxDistance > 0 ? distance / maxDistance : 0f;
                float jitter = (random.nextFloat() - 0.5f) * JITTER;

                tileCenterX[i] = cx;
                tileCenterY[i] = cy;
                tileDelay[i] = clamp(normalized + jitter, 0f, 1f - FADE_WINDOW);
                i++;
            }
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (tileCenterX == null) return;

        int baseAlpha = Color.alpha(fillColor);
        int red = Color.red(fillColor);
        int green = Color.green(fillColor);
        int blue = Color.blue(fillColor);

        for (int i = 0; i < tileCenterX.length; i++) {
            float local = clamp((progress - tileDelay[i]) / FADE_WINDOW, 0f, 1f);
            if (local >= 1f) continue; // fully faded away, nothing to draw

            float alpha = 1f - local;
            float scale = 0.25f + 0.75f * alpha; // shrinks as it fades, reads as "splitting off"

            tilePaint.setColor(Color.argb((int) (baseAlpha * alpha), red, green, blue));

            float half = (tileSizePx / 2f) * scale;
            float cx = tileCenterX[i];
            float cy = tileCenterY[i];
            canvas.drawRect(cx - half, cy - half, cx + half, cy + half, tilePaint);
        }
    }
}
