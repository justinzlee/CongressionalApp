package com.example.myapplication.model;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.example.myapplication.R;
import com.example.myapplication.data.LiveReadings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Turns the air and water readings into hazards, so they reach the Alerts tab
 * rather than living only on the beach screen that fetched them.
 *
 * <p>This is the same move {@link ReportHazards} makes for what people report,
 * and for the same reason. A cross-border sewage advisory shown only to
 * somebody who had already opened Imperial Beach is a diary entry; routed
 * through here it turns up in the Alerts tab, in the unread badge, and in the
 * hero card at the top of that screen — which is where a person deciding
 * between beaches actually looks.
 *
 * <p>Only the bands worth interrupting somebody over produce a hazard. Clean
 * water and ordinary air produce nothing at all, because an app that posts an
 * alert saying everything is fine teaches people to stop reading alerts.
 */
public final class EnvironmentHazards {

    /**
     * The AQI at which the air becomes worth an alert rather than a number on
     * the beach screen. The EPA's "unhealthy for sensitive groups" boundary —
     * the first band where the official advice is to change what you do.
     */
    @VisibleForTesting
    static final int AIR_ALERT_FROM = AirQuality.MODERATE_MAX + 1;

    private EnvironmentHazards() {
    }

    /** Whatever the latest readings for this beach warrant, worst first. */
    public static List<Hazard> forBeach(Context context, Beach beach) {
        List<Hazard> hazards = new ArrayList<>();

        Hazard water = waterHazard(context, LiveReadings.waterFor(beach.getId()));
        if (water != null) hazards.add(water);

        Hazard air = airHazard(context, LiveReadings.airFor(beach.getId()));
        if (air != null) hazards.add(air);

        return hazards;
    }

    /**
     * A runoff or sewage advisory.
     *
     * <p>Rain alone and a standing cross-border advisory are both {@link
     * Severity#MODERATE}: real reasons not to swim, but conditions rather than
     * emergencies, and the south county sits under one for most of the year —
     * a permanent red card there would bleach out every genuine warning around
     * it. The two together are {@link Severity#HIGH}, because that is the
     * combination that actually closes those beaches.
     */
    @VisibleForTesting
    @Nullable
    static Hazard waterHazard(Context context, @Nullable WaterQuality water) {
        if (water == null || !water.isAdvisory()) return null;

        boolean severe = water.getStatus() == WaterQuality.Status.SEWAGE_AND_RAIN;
        return new Hazard(
            HazardType.WATER_QUALITY,
            severe ? Severity.HIGH : Severity.MODERATE,
            context.getString(water.getStatus().bodyRes),
            Arrays.asList(
                context.getString(R.string.hazard_water_step_stay_out),
                context.getString(R.string.hazard_water_step_wash),
                context.getString(R.string.hazard_water_step_check)));
    }

    /**
     * Smoke or ozone worth changing plans over.
     *
     * <p>Capped at {@link Severity#MODERATE} below the "very unhealthy" band
     * for the same reason the UV hazard is: this is a reason to cut a session
     * short, not a reason the water is dangerous, and a red card beside a green
     * swim verdict is the contradiction the hazard list exists to avoid.
     */
    @VisibleForTesting
    @Nullable
    static Hazard airHazard(Context context, @Nullable AirQuality air) {
        if (air == null || air.getUsAqi() < AIR_ALERT_FROM) return null;

        boolean severe = air.getUsAqi() > AirQuality.UNHEALTHY_MAX;
        return new Hazard(
            HazardType.AIR_QUALITY,
            severe ? Severity.HIGH : Severity.MODERATE,
            context.getString(R.string.hazard_air_body,
                air.getUsAqi(), context.getString(air.getLabelRes())),
            Arrays.asList(
                context.getString(air.getAdviceRes()),
                context.getString(R.string.hazard_air_step_indoors)));
    }
}
