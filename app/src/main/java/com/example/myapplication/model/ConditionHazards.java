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
     * Everything worth watching for at this beach, best source first: what
     * somebody standing on the sand reported today, then what the beach has on
     * record, then what today's readings imply.
     *
     * <p>At most one hazard per type comes out, and the earlier source wins. A
     * hand-written description of the rip currents at this beach beats a derived
     * one, and an eyewitness report of a stranded seal this morning beats the
     * beach's standing note that marine life is sometimes about. That ordering
     * is also what keeps {@link BeachAlert#getId()} unique — it is built from
     * the beach and the hazard type, so two hazards of one type at one beach
     * would collapse into a single dismissible alert.
     */
    public static List<Hazard> merged(Context context, Beach beach) {
        List<Hazard> merged = new ArrayList<>(ReportHazards.forBeach(context, beach));
        // Today's air and water readings outrank anything written down about
        // this beach in advance, for the same reason an eyewitness does: a
        // runoff advisory that started with last night's rain is a fact about
        // now, and the beach's standing water-quality note is not.
        for (Hazard measured : EnvironmentHazards.forBeach(context, beach)) {
            if (!containsType(merged, measured.getType())) {
                merged.add(measured);
            }
        }
        for (Hazard recorded : beach.getHazards()) {
            if (!containsType(merged, recorded.getType())) {
                merged.add(recorded.localized(context));
            }
        }
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
