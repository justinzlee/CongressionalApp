package com.example.myapplication;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.example.myapplication.model.Beach;
import com.example.myapplication.model.Sighting;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.Locale;

/**
 * The sheet behind a reported sighting on the beach detail screen: pass it on,
 * or take it back.
 *
 * <p>Like {@link BeachPhotoSheet}, this only collects the choice. Deleting a
 * report has to redraw the list it was on, and sharing one starts another app,
 * so both are handed back to the screen that owns them.
 */
public final class SightingSheet {

    public interface Choice {
        void onShare();
        void onDelete();
    }

    private SightingSheet() {
    }

    public static void show(Context context, Beach beach, Sighting sighting, Choice choice) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        View sheet = LayoutInflater.from(context).inflate(R.layout.sheet_sighting, null);
        dialog.setContentView(sheet);

        String species = context.getString(sighting.getLabelRes());
        String where = context.getString(sighting.isStranded()
            ? R.string.sighting_tag_stranded : R.string.sighting_tag_water);
        ((TextView) sheet.findViewById(R.id.sightingSheetTitle)).setText(
            String.format(Locale.getDefault(), "%s — %s",
                species, where.toLowerCase(Locale.getDefault())));

        ((TextView) sheet.findViewById(R.id.sightingSheetWhen)).setText(
            String.format(Locale.getDefault(), "%s · %s",
                relativeTime(context, sighting.getTimeMillis()), beach.getName()));

        // Only a report that warns people is worth passing on: a stranding, or
        // a shark still in the water. A dolphin off the break is a nice thing to
        // have seen, not a message somebody needs.
        View share = sheet.findViewById(R.id.sightingSheetShare);
        share.setVisibility(sighting.raisesWarning() ? View.VISIBLE : View.GONE);
        share.setOnClickListener(v -> {
            dialog.dismiss();
            choice.onShare();
        });

        sheet.findViewById(R.id.sightingSheetDelete).setOnClickListener(v -> {
            dialog.dismiss();
            choice.onDelete();
        });

        dialog.show();
    }

    /**
     * "2 hours ago", "yesterday". Relative rather than a clock time because the
     * only question anyone asks of a report is how stale it is, and working
     * that out from "14:05" needs the reader to know what day it was.
     *
     * <p>The first minute is special-cased. {@code getRelativeTimeSpanString}
     * renders anything under its resolution as "0 min. ago", which is the first
     * thing a user sees on the beach screen immediately after saving a rating —
     * and reads like the app got it wrong rather than like it just happened.
     */
    static CharSequence relativeTime(Context context, long timeMillis) {
        long now = System.currentTimeMillis();
        if (now - timeMillis < DateUtils.MINUTE_IN_MILLIS) {
            return context.getString(R.string.time_just_now);
        }
        return DateUtils.getRelativeTimeSpanString(timeMillis, now,
            DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE);
    }
}
