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
import com.example.myapplication.network.LifeguardClient;
import com.example.myapplication.util.UnitConverter;
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
     * @param beach     the beach the phone is on or beside, used to name where
     *                  the user is and to find the nearest help; null when
     *                  the phone is not near one, or its position is unknown
     * @param latitude  the phone's own position, sent as the map pin; null
     *                  when there is no fix
     * @param longitude as above
     */
    public static void show(Context context, @Nullable Beach beach,
                            @Nullable Double latitude, @Nullable Double longitude) {
        boolean located = latitude != null && longitude != null;
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

        HelpLocation help = nearestHelp(context, beach, latitude, longitude);
        TextView directionsBody = sheet.findViewById(R.id.sosDirectionsBody);
        View directions = sheet.findViewById(R.id.sosDirections);
        if (help == null) {
            directionsBody.setText(R.string.sos_directions_none);
            directions.setEnabled(false);
            // Off the sea tint, so an action that can't be taken doesn't look
            // like one that can.
            directions.setBackgroundResource(R.drawable.bg_surface_card);
            directions.setAlpha(0.5f);
        } else {
            String helpName = helpLabel(context, help);
            directionsBody.setText(helpName);
            directions.setOnClickListener(v -> {
                dialog.dismiss();
                String uri = String.format(Locale.US, "geo:%f,%f?q=%f,%f(%s)",
                    help.getLatitude(), help.getLongitude(),
                    help.getLatitude(), help.getLongitude(),
                    Uri.encode(helpName));
                safeStart(context, new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
            });
        }

        sheet.findViewById(R.id.sosShare).setOnClickListener(v -> {
            dialog.dismiss();
            // The coordinates are written in Locale.US, not left to the
            // string: getString formats in the phone's locale, and on a phone
            // set to Spain, Germany or anywhere else with a decimal comma that
            // made the link "?q=32,715700,-117,161100", which a map reads as
            // somewhere else or nowhere at all.
            //
            // The pin is the phone's own fix wherever there is one - a
            // swimmer in trouble a kilometre down the sand is not at the
            // beach's pin - and the beach is named alongside it when the
            // phone is on one. With no fix the message no longer claims to
            // be "sending my location": there is none to send.
            String message;
            if (located) {
                String pin = String.format(Locale.US, "%f,%f", latitude, longitude);
                message = beach != null
                    ? context.getString(R.string.sos_share_message, beach.getName(), pin)
                    : context.getString(R.string.sos_share_position, pin);
            } else if (beach != null) {
                message = context.getString(R.string.sos_share_message, beach.getName(),
                    String.format(Locale.US, "%f,%f", beach.getLatitude(), beach.getLongitude()));
            } else {
                message = context.getString(R.string.sos_share_unknown);
            }
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT, message);
            safeStart(context, Intent.createChooser(share,
                context.getString(R.string.sos_share_title)));
        });

        dialog.show();
    }

    /**
     * Where to send someone for help: the lifeguard station on record for the
     * beach, else the nearest of the towers OpenStreetMap has around it (when
     * its page has looked them up), else whatever help is listed first.
     *
     * <p>Only two beaches carry a station of their own, so without the mapped
     * towers this row was greyed out almost everywhere - including at beaches
     * whose map, one screen away, showed a row of towers along the sand.
     */
    @Nullable
    private static HelpLocation nearestHelp(Context context, @Nullable Beach beach,
                                            @Nullable Double latitude, @Nullable Double longitude) {
        if (beach == null) return null;
        List<HelpLocation> help = beach.getNearestHelp();
        for (HelpLocation h : help) {
            if (h.getType() == HelpType.LIFEGUARD_TOWER) return h;
        }

        List<HelpLocation> mapped = new LifeguardClient(context).cachedTowers(beach.getId());
        if (mapped != null && !mapped.isEmpty()) {
            double fromLat = latitude != null ? latitude : beach.getLatitude();
            double fromLon = longitude != null ? longitude : beach.getLongitude();
            HelpLocation closest = null;
            double best = Double.MAX_VALUE;
            for (HelpLocation tower : mapped) {
                double miles = UnitConverter.calculateDistance(
                    fromLat, fromLon, tower.getLatitude(), tower.getLongitude());
                if (miles < best) {
                    best = miles;
                    closest = tower;
                }
            }
            return closest;
        }
        return help.isEmpty() ? null : help.get(0);
    }

    /**
     * What to call a help point. Mapped towers often carry only the number
     * painted on their side, or nothing, so those are named as towers.
     */
    private static String helpLabel(Context context, HelpLocation help) {
        String name = help.getName() == null ? "" : help.getName().trim();
        if (name.isEmpty()) return context.getString(R.string.sos_help_tower);
        if (name.length() <= 4) return context.getString(R.string.sos_help_tower_named, name);
        return name;
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
