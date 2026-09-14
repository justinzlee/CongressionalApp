package com.example.myapplication;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

/**
 * The sheet behind the Language row in Settings.
 *
 * <p>Like {@link BeachPhotoSheet} it only collects the choice. Switching
 * language tears down and rebuilds every activity on the stack, so the work
 * itself belongs to whoever opened the sheet, after this has closed.
 */
public final class LanguageSheet {

    public interface Choice {
        /** @param tag a BCP 47 tag, or {@link SettingsActivity#LANGUAGE_SYSTEM} for the phone's own. */
        void onLanguageChosen(String tag);
    }

    private LanguageSheet() {
    }

    /**
     * @param current the tag in force, so the row already in use is ticked
     *                rather than the sheet opening with nothing chosen.
     */
    public static void show(Context context, String current, Choice choice) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        View sheet = LayoutInflater.from(context).inflate(R.layout.sheet_language, null);
        dialog.setContentView(sheet);

        bind(dialog, sheet, R.id.languageSystem, R.id.languageSystemTick,
            SettingsActivity.LANGUAGE_SYSTEM, current, choice);
        bind(dialog, sheet, R.id.languageEnglish, R.id.languageEnglishTick,
            SettingsActivity.LANGUAGE_ENGLISH, current, choice);
        bind(dialog, sheet, R.id.languageSpanish, R.id.languageSpanishTick,
            SettingsActivity.LANGUAGE_SPANISH, current, choice);

        dialog.show();
    }

    private static void bind(BottomSheetDialog dialog, View sheet, int rowId, int tickId,
                             String tag, String current, Choice choice) {
        ImageView tick = sheet.findViewById(tickId);
        tick.setVisibility(tag.equals(current) ? View.VISIBLE : View.GONE);
        sheet.findViewById(rowId).setOnClickListener(v -> {
            dialog.dismiss();
            choice.onLanguageChosen(tag);
        });
    }
}
