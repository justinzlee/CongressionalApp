package com.example.myapplication.util;

import android.view.View;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Android 15 (targetSdk 35) enforces edge-to-edge, so system bars overlay
 * content by default. These helpers pad specific views by the system bar
 * insets instead of the whole screen, keeping hero banners full-bleed.
 */
public final class EdgeToEdge {
    private EdgeToEdge() {}

    public static void applyTopInset(View target) {
        int basePadding = target.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(target, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), basePadding + bars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
    }

    public static void applyBottomInset(View target) {
        int basePadding = target.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(target, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), basePadding + bars.bottom);
            return insets;
        });
    }
}
