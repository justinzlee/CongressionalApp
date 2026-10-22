package com.example.myapplication;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.example.myapplication.model.SeagullRating;
import com.google.android.material.bottomsheet.BottomSheetDialog;

/**
 * The sheet behind the seagulls card on the beach screen: one row per step of
 * {@link SeagullRating}, the current one ticked.
 *
 * <p>A sheet rather than a screen like the rocks rating, because there is
 * nothing to calibrate against. Five words are their own reference, where a
 * number out of ten needed pictures beside it. Like {@link LanguageSheet}, it
 * only collects the choice and hands it back.
 */
public final class SeagullSheet {

    public interface Choice {
        void onRated(int rating);
        void onCleared();
    }

    private SeagullSheet() {
    }

    /** @param current the beach's rating, or {@link SeagullRating#UNRATED}. */
    public static void show(Context context, int current, Choice choice) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        View sheet = LayoutInflater.from(context).inflate(R.layout.sheet_seagulls, null);
        dialog.setContentView(sheet);

        ViewGroup levels = sheet.findViewById(R.id.gullLevels);
        LayoutInflater inflater = LayoutInflater.from(context);
        for (int rating = SeagullRating.NONE; rating <= SeagullRating.MAX; rating++) {
            View row = inflater.inflate(R.layout.item_gull_level, levels, false);
            ((TextView) row.findViewById(R.id.gullLevelLabel))
                .setText(SeagullRating.labelRes(rating));
            ((TextView) row.findViewById(R.id.gullLevelAdvice))
                .setText(SeagullRating.adviceRes(rating));
            row.findViewById(R.id.gullLevelTick)
                .setVisibility(rating == current ? View.VISIBLE : View.GONE);
            int chosen = rating;
            row.setOnClickListener(v -> {
                dialog.dismiss();
                choice.onRated(chosen);
            });
            levels.addView(row);
        }

        View clear = sheet.findViewById(R.id.gullClear);
        clear.setVisibility(SeagullRating.isRated(current) ? View.VISIBLE : View.GONE);
        clear.setOnClickListener(v -> {
            dialog.dismiss();
            choice.onCleared();
        });

        dialog.show();
    }
}
