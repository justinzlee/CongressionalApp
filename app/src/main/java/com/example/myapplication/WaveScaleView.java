package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.example.myapplication.util.UnitConverter;


/**
 * The ruler down the side of the wave-height card: ticks every foot, labelled
 * every two, with a ring marking where today's swell falls on it. It shares its
 * top and height with {@link WaveHeightView}, so a position on this scale and
 * the waterline next to it mean the same thing.
 *
 * Labels follow the units set in Settings, so the same ten-foot scale reads
 * 0 to 3 in metric.
 */
public class WaveScaleView extends View {

    /**
     * One tick per foot, every other one labelled — so a ten-foot scale is
     * labelled 0, 2, 4, 6, 8, 10. Taken from the scale's own range rather than
     * written down again here, so the ruler cannot end up finer or coarser than
     * the card it is measuring.
     */
    private static final int STEPS = Math.round(WaveScale.MAX_FT);

    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final float density;
    private final int majorTick;
    private final int minorTick;
    private final int haloColor;

    private float heightFt;
    private boolean hasReading;

    public WaveScaleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        majorTick = ContextCompat.getColor(context, R.color.divider);
        minorTick = (majorTick & 0x00FFFFFF) | 0x80000000;
        haloColor = ContextCompat.getColor(context, R.color.surface);

        tickPaint.setStrokeWidth(density);
        labelPaint.setColor(ContextCompat.getColor(context, R.color.text_secondary));
        labelPaint.setTextSize(10f * density);
        labelPaint.setTextAlign(Paint.Align.RIGHT);
        labelPaint.setFontFeatureSettings("tnum");
    }

    /**
     * @param feet       where to put the marker
     * @param hasReading false hides the marker: an unread scale should not
     *                   point at a number
     */
    public void setReading(float feet, boolean hasReading) {
        this.heightFt = feet;
        this.hasReading = hasReading;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        float majorLen = 9f * density;
        float minorLen = 4f * density;
        float labelRight = w - majorLen - 5f * density;
        Paint.FontMetrics metrics = labelPaint.getFontMetrics();

        float radius = 6f * density;
        float halo = radius + 4f * density;
        // Worked out before the ticks are drawn, because a label the ring is
        // about to sit on is left out entirely: the halo erases whatever is
        // under it, and half a number reads as a rendering fault rather than
        // as a covered label. The reading itself is on the card in full.
        float ratio = Math.max(0f, Math.min(1f, heightFt / WaveScale.MAX_FT));
        // Kept a marker's width inside the view so it never draws half a ring.
        float markerY = Math.max(halo, Math.min(h - halo, (1f - ratio) * h));

        for (int i = 0; i <= STEPS; i++) {
            float fromTop = (float) i / STEPS;
            float y = fromTop * h;
            boolean labelled = i % 2 == 0;
            tickPaint.setColor(labelled ? majorTick : minorTick);
            float length = labelled ? majorLen : minorLen;
            canvas.drawLine(w - length, y, w, y, tickPaint);
            if (!labelled) continue;
            if (hasReading && Math.abs(y - markerY) < halo) continue;

            // Centred on its tick, nudged in at the two ends so the top and
            // bottom labels are not sliced by the edge of the view.
            float baseline = Math.max(-metrics.ascent,
                Math.min(h - metrics.descent, y - (metrics.ascent + metrics.descent) / 2f));
            canvas.drawText(
                UnitConverter.waveHeightTick(getContext(), WaveScale.MAX_FT * (1f - fromTop)),
                labelRight, baseline, labelPaint);
        }

        if (!hasReading) return;

        float y = markerY;
        float x = w - halo;

        // The halo punches the ticks out from under the ring.
        markerPaint.setStyle(Paint.Style.FILL);
        markerPaint.setColor(haloColor);
        canvas.drawCircle(x, y, halo, markerPaint);
        markerPaint.setStyle(Paint.Style.STROKE);
        markerPaint.setStrokeWidth(2f * density);
        markerPaint.setColor(ContextCompat.getColor(getContext(),
            WaveScale.levelFor(heightFt).getColorRes()));
        canvas.drawCircle(x, y, radius, markerPaint);
    }
}
