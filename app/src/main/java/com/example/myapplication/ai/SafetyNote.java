package com.example.myapplication.ai;

import android.content.Context;

import com.example.myapplication.R;
import com.example.myapplication.model.Beach;
import com.example.myapplication.model.BeachConditions;
import com.example.myapplication.model.ConditionHazards;
import com.example.myapplication.model.Hazard;
import com.example.myapplication.model.HazardType;
import com.example.myapplication.model.SafetyLevel;
import com.example.myapplication.model.UvLevel;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Writes the one-paragraph plain-language note under the swim verdict — the
 * "why" behind the headline. Everything it says is drawn from the beach's
 * live conditions and the hazards on record; it never invents detail.
 *
 * <p>Every sentence is a whole string resource rather than fragments glued
 * together, so a translation can order its own words: "hay un aviso por" takes
 * the hazard where English takes it in the middle of "There is a … warning".
 */
public class SafetyNote {

    public String describe(Context context, Beach beach, SafetyLevel level) {
        StringBuilder note = new StringBuilder();
        note.append(describeSurf(context, beach.getConditions()));

        // The same hazards the verdict is judged on, so a shark reported this
        // morning is named here rather than leaving a red verdict unexplained.
        // Surf and UV are left out: the sentences either side already say them.
        List<Hazard> hazards = ConditionHazards.merged(context, beach).stream()
            .filter(h -> h.getType() != HazardType.HIGH_SURF && h.getType() != HazardType.UV_INDEX)
            .collect(Collectors.toList());
        String hazardText = describeHazards(context, hazards);
        if (hazardText != null) {
            note.append(' ').append(hazardText);
        }

        String uvText = describeUv(context, beach.getConditions());
        if (uvText != null) {
            note.append(' ').append(uvText);
        }

        note.append(' ').append(context.getString(adviceRes(level)));
        return note.toString();
    }

    private String describeSurf(Context context, BeachConditions conditions) {
        if (conditions == null || conditions.getWaveHeight() == null) {
            return context.getString(R.string.note_surf_unknown);
        }
        // Read off SafetyLevel's own thresholds rather than copied numbers, so
        // the wording can never call a swell "heavy" while the card above it
        // says "Moderate" and the verdict says "Take care". Copies of these
        // two numbers are exactly what drifted apart before.
        double feet = conditions.getWaveHeight();
        if (feet < 1.0) return context.getString(R.string.note_surf_flat);
        if (feet < SafetyLevel.CAUTION_WAVE_FEET) return context.getString(R.string.note_surf_small);
        if (feet < SafetyLevel.DANGER_WAVE_FEET) return context.getString(R.string.note_surf_moderate);
        return context.getString(R.string.note_surf_heavy);
    }

    private String describeHazards(Context context, List<Hazard> hazards) {
        if (hazards.isEmpty()) return null;
        // Each type carries its own written label; deriving one from the enum
        // name produced "uv index" mid-sentence.
        String names = hazards.stream()
            .map(h -> context.getString(h.getType().getInlineLabelRes()))
            .reduce((a, b) -> context.getString(R.string.note_hazard_join, a, b))
            .orElse("");
        return context.getString(hazards.size() == 1
            ? R.string.note_hazard_one : R.string.note_hazard_many, names);
    }

    private String describeUv(Context context, BeachConditions conditions) {
        if (conditions == null || conditions.getUvIndex() == null) return null;
        // "Very high" and above, read off the band table rather than a copied
        // number, so this line appears at exactly the reading the UV lesson
        // calls very high.
        if (UvLevel.of(conditions.getUvIndex()).compareTo(UvLevel.VERY_HIGH) < 0) return null;
        return context.getString(R.string.note_uv,
            conditions.getUvLabel(context).toLowerCase(Locale.getDefault()));
    }

    private int adviceRes(SafetyLevel level) {
        switch (level) {
            case DANGER:
                return R.string.note_advice_danger;
            case CAUTION:
                return R.string.note_advice_caution;
            case SAFE:
            default:
                return R.string.note_advice_safe;
        }
    }
}
