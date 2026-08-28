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

import com.example.myapplication.data.BeachRepository;
import com.example.myapplication.model.Beach;
import com.example.myapplication.model.BeachAlert;
import com.example.myapplication.model.Severity;
import com.example.myapplication.util.EdgeToEdge;

import java.util.List;
import java.util.Locale;

/**
 * Every hazard on record across all beaches, worst first, so a swimmer can
 * see what is going on nearby without opening each beach in turn.
 */
public class AlertsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alerts);

        EdgeToEdge.applyTopInset(findViewById(R.id.alertsHeader));
        EdgeToEdge.applyBottomInset(findViewById(R.id.tabBar));

        TabBar.setup(this, TabBar.Tab.ALERTS);
        bindAlerts();
    }

    private void bindAlerts() {
        List<BeachAlert> alerts = BeachAlert.from(new BeachRepository().getBeaches());

        View hero = findViewById(R.id.alertHero);
        View empty = findViewById(R.id.alertsEmpty);
        TextView heading = findViewById(R.id.alertsHeading);
        ViewGroup list = findViewById(R.id.alertList);
        list.removeAllViews();

        if (alerts.isEmpty()) {
            hero.setVisibility(View.GONE);
            empty.setVisibility(View.VISIBLE);
            heading.setVisibility(View.GONE);
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

        LayoutInflater inflater = LayoutInflater.from(this);
        for (BeachAlert alert : alerts) {
            list.addView(buildRow(inflater, list, alert));
        }
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
