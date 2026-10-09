package com.example.myapplication;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.StringRes;

/**
 * One reading's place on a screen: a {@link SwellLoaderView} while it is on its
 * way, then the value, faded in where the loader was.
 *
 * <p>The value keeps its room while it is hidden — invisible rather than gone —
 * so the line it sits on is already the height the number will need, and
 * nothing below it moves when the reading lands. The loader sits over that
 * room in the same frame (see the stat tiles in activity_beach_detail.xml).
 *
 * <p>The fade is a view property animation, which "Remove animations" already
 * shortens to nothing, so it needs no check of its own.
 */
final class ReadingSlot {

    private static final long REVEAL_MS = 220L;

    private final TextView value;
    private final View loader;

    ReadingSlot(TextView value, View loader) {
        this.value = value;
        this.loader = loader;
    }

    /** Shows the loader in the value's place. */
    void loading() {
        value.animate().cancel();
        value.setVisibility(View.INVISIBLE);
        loader.setVisibility(View.VISIBLE);
    }

    boolean isLoading() {
        return loader.getVisibility() == View.VISIBLE;
    }

    void show(CharSequence text) {
        value.setText(text);
        reveal();
    }

    void show(@StringRes int text) {
        value.setText(text);
        reveal();
    }

    /** The value view itself, for a caller that styles it as well as filling it. */
    TextView value() {
        return value;
    }

    private void reveal() {
        if (!isLoading()) {
            value.setVisibility(View.VISIBLE);
            return;
        }
        loader.setVisibility(View.GONE);
        value.setAlpha(0f);
        value.setVisibility(View.VISIBLE);
        value.animate().alpha(1f).setDuration(REVEAL_MS).start();
    }
}
