package com.example.myapplication;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.example.myapplication.model.Beach;
import com.example.myapplication.model.HelpLocation;
import com.example.myapplication.model.HelpType;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.List;
import java.util.Locale;

/**
 * The emergency sheet. Every row here hands off to a real system app — the
 * dialler, maps, the share sheet — because a control that only looks like it
 * summons help is worse than no control at all.
 */
public final class SosSheet {

    private SosSheet() {
    }

    /**
     * @param beach the beach the user is nearest to, used for the location
     *              pin and the nearest listed help; may be null.
     */
    public static void show(Context context, @Nullable Beach beach) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        View sheet = LayoutInflater.from(context).inflate(R.layout.sheet_sos, null);
        dialog.setContentView(sheet);

        TextView where = sheet.findViewById(R.id.sosWhere);
        where.setText(beach == null
            ? context.getString(R.string.sos_where_unknown)
            : context.getString(R.string.sos_where, beach.getName()));

        // Opens the dialler pre-filled rather than placing the call, so the
        // user always makes the final decision.
        sheet.findViewById(R.id.sosCall).setOnClickListener(v -> {
            dialog.dismiss();
            Intent dial = new Intent(Intent.ACTION_DIAL,
                Uri.parse("tel:" + context.getString(R.string.emergency_number)));
            safeStart(context, dial);
        });

        HelpLocation help = nearestHelp(beach);
        TextView directionsBody = sheet.findViewById(R.id.sosDirectionsBody);
        View directions = sheet.findViewById(R.id.sosDirections);
        if (help == null) {
            directionsBody.setText(R.string.sos_directions_none);
            directions.setEnabled(false);
            directions.setAlpha(0.5f);
        } else {
            directionsBody.setText(help.getName());
            directions.setOnClickListener(v -> {
                dialog.dismiss();
                String uri = String.format(Locale.US, "geo:%f,%f?q=%f,%f(%s)",
                    help.getLatitude(), help.getLongitude(),
                    help.getLatitude(), help.getLongitude(),
                    Uri.encode(help.getName()));
                safeStart(context, new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
            });
        }

        sheet.findViewById(R.id.sosShare).setOnClickListener(v -> {
            dialog.dismiss();
            String message = beach == null
                ? context.getString(R.string.sos_share_unknown)
                : context.getString(R.string.sos_share_message, beach.getName(),
                    beach.getLatitude(), beach.getLongitude());
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT, message);
            safeStart(context, Intent.createChooser(share,
                context.getString(R.string.sos_share_title)));
        });

        dialog.show();
    }

    /** Prefers a lifeguard tower, else whatever help is listed first. */
    @Nullable
    private static HelpLocation nearestHelp(@Nullable Beach beach) {
        if (beach == null) return null;
        List<HelpLocation> help = beach.getNearestHelp();
        return help.stream()
            .filter(h -> h.getType() == HelpType.LIFEGUARD_TOWER)
            .findFirst()
            .orElse(help.isEmpty() ? null : help.get(0));
    }

    private static void safeStart(Context context, Intent intent) {
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            // A device with no dialler or maps app: say so rather than
            // failing silently in an emergency.
            Toast.makeText(context, R.string.sos_no_app, Toast.LENGTH_LONG).show();
        }
    }
}
