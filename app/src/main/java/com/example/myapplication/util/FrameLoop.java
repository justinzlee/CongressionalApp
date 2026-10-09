package com.example.myapplication.util;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewTreeObserver;

/**
 * Keeps a hand-drawn animation asking for frames only while somebody could be
 * looking at it.
 *
 * <p>A view that ends every {@code onDraw} with {@code postInvalidateOnAnimation}
 * redraws sixty times a second for as long as it is attached — scrolled off the
 * top of a long page included, because scrolling a page moves its children
 * without redrawing them. The beach screen has a live sea at the top and a
 * stack of loaders below it, so this checks, before each next frame is asked
 * for, that the view is shown and has some part of itself on screen. When it
 * has not, the loop parks, and the next scroll that brings it back in view (or
 * the screen coming back to the front) starts it again.
 *
 * <p>Under "Remove animations" nothing loops at all: each view draws the one
 * frame it is asked for and holds it, as the launch screen does.
 *
 * <p>The owning view forwards three calls: {@link #attach()} and {@link
 * #detach()} from its window callbacks, and {@link #onVisibilityAggregated}
 * from its own override of the same name. {@link #next()} goes at the end of
 * its {@code onDraw}.
 */
public final class FrameLoop implements ViewTreeObserver.OnScrollChangedListener {

    private final View view;
    private final boolean reduced;
    private final Rect onScreen = new Rect();
    /** Stopped because nobody could see the frame it would have drawn. */
    private boolean parked;

    public FrameLoop(View view) {
        this.view = view;
        this.reduced = !view.isInEditMode() && Motion.isReduced(view.getContext());
    }

    /** True under "Remove animations": draw one settled frame and stop. */
    public boolean isReduced() {
        return reduced;
    }

    /** Asks for the next frame if it would be seen; parks the loop if not. */
    public void next() {
        if (reduced) return;
        if (canBeSeen()) {
            parked = false;
            view.postInvalidateOnAnimation();
        } else {
            parked = true;
        }
    }

    public void attach() {
        view.getViewTreeObserver().addOnScrollChangedListener(this);
    }

    public void detach() {
        view.getViewTreeObserver().removeOnScrollChangedListener(this);
    }

    /** The screen came back to the front, or the view was shown again. */
    public void onVisibilityAggregated(boolean isVisible) {
        if (isVisible) resume();
    }

    @Override
    public void onScrollChanged() {
        resume();
    }

    private void resume() {
        if (parked && !reduced && canBeSeen()) {
            parked = false;
            view.invalidate();
        }
    }

    private boolean canBeSeen() {
        return view.isShown() && view.getGlobalVisibleRect(onScreen);
    }
}
