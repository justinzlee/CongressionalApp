package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.example.myapplication.util.Motion;

/**
 * The Flagline mark on the launch screen: a warning flag on its pole, drawn at
 * the size the identity sheet gives the launch screen.
 *
 * <p>The pole draws itself downward, the flag unfurls from where it meets the
 * pole, and from then on it flutters as if in light wind. The waterline the
 * mark carries elsewhere is left off here - the surf along the bottom of the
 * screen already does that job.
 *
 * <p>Geometry is the sheet's own 64 x 64 mark box, scaled to whatever size the
 * view is given, and the path data below is its own.
 */
public class FlaglineMarkView extends View {

    /** The sheet's mark box; every number below is in its units. */
    private static final float BOX = 64f;

    // Pole: M20 5v54, stroke 5, round cap.
    private static final float POLE_X = 20f;
    private static final float POLE_TOP = 5f;
    private static final float POLE_BOTTOM = 59f;
    private static final float POLE_WIDTH = 5f;

    // Flag: M22 8h28l-10 12 10 12H22z - a rectangle off the pole with a
    // swallowtail notch bitten out of its flying edge.
    private static final float FLAG_LEFT = 22f;
    private static final float FLAG_TOP = 8f;
    private static final float FLAG_WIDTH = 28f;
    private static final float FLAG_HEIGHT = 24f;
    private static final float FLAG_NOTCH = 10f;

    // From mount, in milliseconds.
    private static final long POLE_DELAY = 250, POLE_DURATION = 650;
    private static final long FLAG_DELAY = 780, FLAG_DURATION = 620;
    /** The flag holds still for a beat once open, then catches the wind. */
    private static final long FLUTTER_DELAY = 1500;
    private static final long FLUTTER_PERIOD = 3600;
    private static final float FLUTTER_SKEW = 1.6f;
    private static final float FLUTTER_SKEW_BACK = -1.4f;
    private static final float FLUTTER_PINCH = .965f;
    private static final float FLUTTER_PINCH_BACK = .975f;

    /**
     * A point on the clock past every entry animation, used to paint the
     * settled frame before the screen plays and when motion is turned off.
     */
    private static final long SETTLED = 10_000L;

    private final Paint polePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flagPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path flag = new Path();
    private final Matrix wind = new Matrix();

    private float scale = 1f;
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

    public FlaglineMarkView(Context context, AttributeSet attrs) {
        super(context, attrs);

        polePaint.setStyle(Paint.Style.STROKE);
        polePaint.setStrokeCap(Paint.Cap.ROUND);
        polePaint.setColor(ContextCompat.getColor(context, R.color.brand_sand));
        // In mark-box units: onDraw scales the canvas, not the geometry.
        polePaint.setStrokeWidth(POLE_WIDTH);

        flagPaint.setStyle(Paint.Style.FILL);
        flagPaint.setColor(ContextCompat.getColor(context, R.color.brand_flag));

        flag.moveTo(FLAG_LEFT, FLAG_TOP);
        flag.lineTo(FLAG_LEFT + FLAG_WIDTH, FLAG_TOP);
        flag.lineTo(FLAG_LEFT + FLAG_WIDTH - FLAG_NOTCH, FLAG_TOP + FLAG_HEIGHT * .5f);
        flag.lineTo(FLAG_LEFT + FLAG_WIDTH, FLAG_TOP + FLAG_HEIGHT);
        flag.lineTo(FLAG_LEFT, FLAG_TOP + FLAG_HEIGHT);
        flag.close();
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
        scale = Math.min(w, h) / BOX;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getWidth() == 0 || getHeight() == 0) return;
        long now = startedAt == 0L || reduced
            ? SETTLED : SystemClock.uptimeMillis() - startedAt;

        canvas.save();
        // Centre the mark box inside whatever square the layout gave us.
        canvas.translate((getWidth() - BOX * scale) * .5f, (getHeight() - BOX * scale) * .5f);
        canvas.scale(scale, scale);
        drawPole(canvas, now);
        drawFlag(canvas, now);
        canvas.restore();
    }

    /** The pole draws itself downward from the masthead. */
    private void drawPole(Canvas canvas, long now) {
        float drawn = Motion.DRAW.getInterpolation(
            Motion.phase(now, POLE_DELAY, POLE_DURATION));
        if (drawn <= 0f) return;
        canvas.drawLine(POLE_X, POLE_TOP, POLE_X,
            POLE_TOP + (POLE_BOTTOM - POLE_TOP) * drawn, polePaint);
    }

    private void drawFlag(Canvas canvas, long now) {
        // The easing overshoots past 1 and comes back, which is the snap of a
        // flag catching wind; the width it opens to needs no keyframe of its own.
        float open = Motion.UNFURL.getInterpolation(
            Motion.phase(now, FLAG_DELAY, FLAG_DURATION));
        if (open <= 0f) return;

        // The flag swings from where it meets the pole, not from its own middle.
        float pivotX = POLE_X + 1f;
        float pivotY = FLAG_TOP + FLAG_HEIGHT * .5f;

        float stretch = open;
        float skew = 0f;
        if (!reduced && now > FLUTTER_DELAY) {
            // The loop starts and ends at rest, so the handover from the
            // unfurl is invisible.
            double turn = 2 * Math.PI * ((now - FLUTTER_DELAY) % FLUTTER_PERIOD) / FLUTTER_PERIOD;
            float wave = (float) Math.sin(turn);
            skew = (float) Math.toRadians(
                wave >= 0 ? FLUTTER_SKEW * wave : -FLUTTER_SKEW_BACK * wave);
            float pinch = wave >= 0 ? FLUTTER_PINCH : FLUTTER_PINCH_BACK;
            stretch = 1f - (1f - pinch) * Math.abs(wave);
        }

        wind.setScale(stretch, 1f, pivotX, pivotY);
        wind.postSkew(0f, (float) Math.tan(skew), pivotX, pivotY);

        canvas.save();
        canvas.concat(wind);
        canvas.drawPath(flag, flagPaint);
        canvas.restore();
    }
}
