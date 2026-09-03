package com.example.myapplication.model;

import android.content.Context;

import com.example.myapplication.R;
import com.example.myapplication.util.UnitConverter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Turns the live conditions the app already fetches into the same kind of
 * {@link Hazard} a beach can have on record.
 *
 * <p>Without this the "What to watch for" section only ever showed the handful
 * of hazards hand-written into the beach list, so a beach running six-foot
 * surf reported nothing at all while the wave card above it said "Hazardous".
 * The thresholds here are {@link SafetyLevel}'s own, so a beach the verdict
 * calls unsafe always says why.
 */
public final class ConditionHazards {

    private ConditionHazards() {
    }

    /**
     * Hazards on record for the beach, followed by anything today's readings
     * add. A reported hazard always wins: if someone has written up the rip
     * currents at this beach, that description is better than a derived one,
     * so no second hazard of the same type is added underneath it.
     */
    public static List<Hazard> merged(Context context, Beach beach) {
        List<Hazard> merged = new ArrayList<>(beach.getHazards());
        for (Hazard derived : derive(context, beach.getConditions())) {
            if (!containsType(merged, derived.getType())) {
                merged.add(derived);
            }
        }
        return merged;
    }

    /** Just the hazards today's readings imply, in severity order. */
    public static List<Hazard> derive(Context context, BeachConditions conditions) {
        List<Hazard> derived = new ArrayList<>();
        if (conditions == null) {
            return derived;
        }

        Double waveHeight = conditions.getWaveHeight();
        if (waveHeight != null && waveHeight >= SafetyLevel.CAUTION_WAVE_FEET) {
            boolean dangerous = waveHeight >= SafetyLevel.DANGER_WAVE_FEET;
            String height = UnitConverter.formatWaveHeight(context, waveHeight);
            derived.add(new Hazard(
                HazardType.HIGH_SURF,
                dangerous ? Severity.HIGH : Severity.MODERATE,
                context.getString(dangerous
                    ? R.string.hazard_surf_high_body : R.string.hazard_surf_moderate_body, height),
                dangerous
                    ? Arrays.asList(
                        context.getString(R.string.hazard_surf_step_stay_out),
                        context.getString(R.string.hazard_surf_step_check_flags))
                    : Arrays.asList(
                        context.getString(R.string.hazard_surf_step_shallow),
                        context.getString(R.string.hazard_surf_step_check_flags))));
        }

        Integer uvIndex = conditions.getUvIndex();
        if (uvIndex != null && uvIndex >= SafetyLevel.CAUTION_UV) {
            derived.add(new Hazard(
                HazardType.UV_INDEX,
                // Moderate, not high: extreme UV only ever moves the verdict to
                // "take care", and a red hazard card under an amber verdict is
                // the same contradiction this class exists to remove. It is a
                // sun-exposure hazard, not a reason the water is unsafe.
                Severity.MODERATE,
                context.getString(R.string.hazard_uv_extreme_body, uvIndex),
                Arrays.asList(
                    context.getString(R.string.hazard_uv_step_cover),
                    context.getString(R.string.hazard_uv_step_shade))));
        }

        return derived;
    }

    private static boolean containsType(List<Hazard> hazards, HazardType type) {
        for (Hazard hazard : hazards) {
            if (hazard.getType() == type) {
                return true;
            }
        }
        return false;
    }
}
