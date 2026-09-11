package com.example.myapplication;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.lifecycle.Lifecycle;

import com.example.myapplication.data.AlertScope;
import com.example.myapplication.data.AlertSeenStore;
import com.example.myapplication.data.BeachRepository;
import com.example.myapplication.model.Beach;
import com.example.myapplication.model.BeachAlert;
import com.example.myapplication.model.Severity;
import com.example.myapplication.util.EdgeToEdge;

import java.util.List;
import java.util.Locale;

/**
 * The hazards on record at the beaches this user has reason to care about,
 * worst first, so a swimmer can see what is going on without opening each
 * beach in turn. Which beaches those are is {@link AlertScope}'s decision.
 */
public class AlertsActivity extends AppCompatActivity {

    private AlertSeenStore seenStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alerts);

        EdgeToEdge.applyTopInset(findViewById(R.id.alertsHeader));
        EdgeToEdge.applyBottomInset(findViewById(R.id.tabBar));

        seenStore = new AlertSeenStore(this);

        TabBar.setup(this, TabBar.Tab.ALERTS);
        // The list itself is built in onResume, which runs on the way in as
        // well as on the way back, so there is one place that draws it.
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Coming back here after saving a beach, or after changing the
        // saved-only setting, changes which beaches are in scope, so the list
        // is rebuilt rather than left as it was drawn on the way in.
        bindAlerts();
        // Reading this screen is what clears the badge: every hazard in scope
        // right now counts as seen, and the tab bar redraws without its count.
        // The same scope as the list, or the badge would keep counting warnings
        // this screen never showed and reading it would never clear them.
        seenStore.markSeen(BeachAlert.from(this, alertBeaches()));
        TabBar.refreshAlertBadge(this);

        // Nothing else refreshes the readings while this tab is in front, and
        // half of these alerts are worked out from them, so ask on the way in
        // (free while they are fresh). Rows that land later only count as read
        // if this screen is still the one in front when they do.
        BeachRepository.getInstance().refreshAllData(() -> runOnUiThread(() -> {
            if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) return;
            bindAlerts();
            seenStore.markSeen(BeachAlert.from(this, alertBeaches()));
            TabBar.refreshAlertBadge(this);
        }));
    }

    /** How many alert rows the screen will draw, worst first. */
    private static final int MAX_LISTED_ALERTS = 40;

    /**
     * The beaches this tab speaks for: the ones near the user and the ones they
     * have saved, or only the saved ones if they have asked for that.
     */
    private List<Beach> alertBeaches() {
        return AlertScope.inScope(this, BeachRepository.getInstance().getBeaches());
    }

    private void bindAlerts() {
        List<Beach> scoped = alertBeaches();
        List<BeachAlert> alerts = BeachAlert.from(this, scoped);

        View hero = findViewById(R.id.alertHero);
        View empty = findViewById(R.id.alertsEmpty);
        TextView heading = findViewById(R.id.alertsHeading);
        ViewGroup list = findViewById(R.id.alertList);
        list.removeAllViews();

        if (alerts.isEmpty()) {
            hero.setVisibility(View.GONE);
            empty.setVisibility(View.VISIBLE);
            heading.setVisibility(View.GONE);
            bindEmptyState(scoped);
            return;
        }

        empty.setVisibility(View.GONE);
        heading.setVisibility(View.VISIBLE);
        heading.setText(getResources().getQuantityString(
            R.plurals.alert_count, alerts.size(), alerts.size()));

        // Lead with the worst one, but only when it is genuinely severe.
        BeachAlert worst = alerts.get(0);
        boolean heroWorthy = worst.getSeverity() == Severity.HIGH
            || worst.getSeverity() == Severity.EXTREME;
        if (heroWorthy) {
            hero.setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.alertHeroTitle)).setText(
                getString(R.string.alert_hero_title,
                    getString(worst.getHazard().getType().getLabelRes()),
                    worst.getBeach().getName()));
            ((TextView) findViewById(R.id.alertHeroBody))
                .setText(worst.getHazard().getDescription());
            findViewById(R.id.alertHeroButton).setOnClickListener(
                v -> openBeach(worst.getBeach()));
        } else {
            hero.setVisibility(View.GONE);
        }

        // The heading counts every alert, but only the worst of them are drawn.
        // These rows go into a plain container inside the page scroll, so each
        // one is inflated and kept - and now that the app covers every coast, a
        // hot, high-UV afternoon can raise a hazard at most beaches at once.
        // The list is worst-first, so the tail this drops is the mildest.
        LayoutInflater inflater = LayoutInflater.from(this);
        for (BeachAlert alert : alerts.subList(0, Math.min(alerts.size(), MAX_LISTED_ALERTS))) {
            list.addView(buildRow(inflater, list, alert));
        }
    }

    /**
     * Says why the screen is empty, which is not one thing.
     *
     * <p>Watching beaches and finding nothing wrong at them is good news, and
     * gets the green all-clear. Watching no beaches at all is not news — it
     * means the tab cannot tell the user anything — so it gets a muted mark and
     * says what would fix it. Reading the same blank screen either way would
     * let someone with location off believe the coast was clear.
     */
    private void bindEmptyState(List<Beach> scoped) {
        boolean watchingSomething = !scoped.isEmpty();
        boolean savedOnly = AlertScope.savedOnly(this);

        int message;
        if (watchingSomething) {
            message = savedOnly ? R.string.alerts_empty_saved : R.string.alerts_empty;
        } else if (savedOnly) {
            message = R.string.alerts_empty_no_saved;
        } else if (AlertScope.knowsWhereYouAre(this)) {
            // We know where they are; there is simply no beach within range and
            // none saved, which is the same all-clear as having nothing wrong.
            message = R.string.alerts_empty;
            watchingSomething = true;
        } else {
            message = R.string.alerts_empty_no_location;
        }

        ((TextView) findViewById(R.id.alertsEmptyText)).setText(message);

        ImageView icon = findViewById(R.id.alertsEmptyIcon);
        icon.setImageResource(watchingSomething
            ? R.drawable.ic_check_circle : R.drawable.ic_bookmark);
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(
            ContextCompat.getColor(this,
                watchingSomething ? R.color.safe : R.color.text_secondary)));
    }

    private View buildRow(LayoutInflater inflater, ViewGroup parent, BeachAlert alert) {
        View row = inflater.inflate(R.layout.item_alert, parent, false);

        int color = ContextCompat.getColor(this, severityColorRes(alert.getSeverity()));
        int tint = ContextCompat.getColor(this, severityTintRes(alert.getSeverity()));

        ImageView icon = row.findViewById(R.id.alertIcon);
        icon.setImageResource(severityIconRes(alert.getSeverity()));
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(color));

        ((TextView) row.findViewById(R.id.alertTitle))
            .setText(alert.getHazard().getType().getLabelRes());
        ((TextView) row.findViewById(R.id.alertBody))
            .setText(alert.getHazard().getDescription());

        TextView severity = row.findViewById(R.id.alertSeverity);
        severity.setText(prettySeverity(alert.getSeverity()));
        severity.setTextColor(color);

        TextView where = row.findViewById(R.id.alertWhere);
        where.setText(alert.getBeach().getName());
        where.setTextColor(color);
        Drawable background = where.getBackground();
        if (background instanceof GradientDrawable) {
            ((GradientDrawable) background.mutate()).setColor(tint);
        }

        row.setOnClickListener(v -> openBeach(alert.getBeach()));
        return row;
    }

    private void openBeach(Beach beach) {
        Intent intent = new Intent(this, BeachDetailActivity.class);
        intent.putExtra(BeachDetailActivity.EXTRA_BEACH_ID, beach.getId());
        startActivity(intent);
    }

    private static String prettySeverity(Severity severity) {
        String raw = severity.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    private static int severityColorRes(Severity severity) {
        if (severity == Severity.HIGH || severity == Severity.EXTREME) return R.color.danger;
        if (severity == Severity.MODERATE) return R.color.caution;
        return R.color.safe;
    }

    private static int severityTintRes(Severity severity) {
        if (severity == Severity.HIGH || severity == Severity.EXTREME) return R.color.danger_tint;
        if (severity == Severity.MODERATE) return R.color.caution_tint;
        return R.color.safe_tint;
    }

    private static int severityIconRes(Severity severity) {
        if (severity == Severity.HIGH || severity == Severity.EXTREME) return R.drawable.ic_x_circle;
        return R.drawable.ic_warning_circle;
    }
}
