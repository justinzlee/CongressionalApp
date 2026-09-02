package com.example.myapplication;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.example.myapplication.data.AlertSeenStore;
import com.example.myapplication.data.BeachRepository;
import com.example.myapplication.model.BeachAlert;

/**
 * Wires up the shared bottom tab bar on a top-level screen. Each tab is its
 * own activity rather than a fragment, so switching tabs reuses the existing
 * instance instead of stacking copies.
 */
public final class TabBar {

    public enum Tab { NOW, ALERTS, SAVED, LEARN, MORE }

    private TabBar() {
    }

    /**
     * @param activity screen hosting the bar
     * @param current  which tab this screen represents, highlighted and inert
     */
    public static void setup(Activity activity, Tab current) {
        bind(activity, current, Tab.NOW, R.id.tabNow, R.id.tabNowIcon, R.id.tabNowLabel,
            R.drawable.ic_wave, MainActivity.class);
        bind(activity, current, Tab.ALERTS, R.id.tabAlerts, R.id.tabAlertsIcon, R.id.tabAlertsLabel,
            R.drawable.ic_warning_circle, AlertsActivity.class);
        bind(activity, current, Tab.SAVED, R.id.tabSaved, R.id.tabSavedIcon, R.id.tabSavedLabel,
            R.drawable.ic_bookmark, SavedActivity.class);
        bind(activity, current, Tab.LEARN, R.id.tabLearn, R.id.tabLearnIcon, R.id.tabLearnLabel,
            R.drawable.ic_book, LearnActivity.class);
        bind(activity, current, Tab.MORE, R.id.tabMore, R.id.tabMoreIcon, R.id.tabMoreLabel,
            R.drawable.ic_dots, SettingsActivity.class);

        refreshAlertBadge(activity);
    }

    private static void bind(Activity activity, Tab current, Tab tab,
                             int rowId, int iconId, int labelId,
                             int iconRes, Class<? extends Activity> target) {
        View row = activity.findViewById(rowId);
        if (row == null) return;

        ImageView icon = activity.findViewById(iconId);
        TextView label = activity.findViewById(labelId);

        boolean selected = tab == current;
        int color = ContextCompat.getColor(activity,
            selected ? R.color.primary : R.color.text_secondary);
        icon.setImageResource(iconRes);
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(color));
        label.setTextColor(color);

        if (selected) {
            // Already here: tapping again should do nothing.
            row.setOnClickListener(null);
            row.setClickable(false);
            return;
        }

        row.setOnClickListener(v -> {
            Intent intent = new Intent(activity, target);
            // SINGLE_TOP matters: CLEAR_TOP on its own destroys and recreates
            // a standard-launchMode activity, which restarted the home screen
            // (and replayed its intro) every time the Now tab was tapped.
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(intent);
            activity.overridePendingTransition(0, 0);
        });
    }

    /**
     * Shows how many hazards the user has not read yet. Tabs are brought back
     * to the front rather than recreated, so every top-level screen calls this
     * again from onResume to pick up alerts that have since been read.
     */
    public static void refreshAlertBadge(Activity activity) {
        TextView badge = activity.findViewById(R.id.tabAlertsBadge);
        if (badge == null) return;

        int count = new AlertSeenStore(activity)
            .countUnseen(BeachAlert.from(new BeachRepository().getBeaches()));
        if (count == 0) {
            badge.setVisibility(View.GONE);
        } else {
            badge.setVisibility(View.VISIBLE);
            badge.setText(String.valueOf(count));
        }
    }
}
