package com.example.myapplication.ai;

import android.content.Context;

import com.example.myapplication.model.Beach;
import com.example.myapplication.model.BeachConditions;
import com.example.myapplication.model.Hazard;
import com.example.myapplication.model.SafetyLevel;

import java.util.List;
import java.util.Locale;

/**
 * Writes the one-paragraph plain-language note under the swim verdict — the
 * "why" behind the headline. Everything it says is drawn from the beach's
 * live conditions and the hazards on record; it never invents detail.
 */
public class SafetyNote {

    public String describe(Context context, Beach beach, SafetyLevel level) {
        StringBuilder note = new StringBuilder();
        note.append(describeSurf(beach.getConditions()));

        String hazardText = describeHazards(beach.getHazards());
        if (hazardText != null) {
            note.append(' ').append(hazardText);
        }

        String uvText = describeUv(beach.getConditions());
        if (uvText != null) {
            note.append(' ').append(uvText);
        }

        note.append(' ').append(advice(level));
        return note.toString();
    }

    private String describeSurf(BeachConditions conditions) {
        if (conditions == null || conditions.getWaveHeight() == null) {
            return "Live surf readings for this beach aren't in yet.";
        }
        double feet = conditions.getWaveHeight();
        if (feet < 1.0) return "The water is close to flat right now.";
        if (feet < 3.0) return "Small, manageable waves are running.";
        if (feet < 6.0) return "There is a moderate swell running.";
        return "A heavy swell is running.";
    }

    private String describeHazards(List<Hazard> hazards) {
        if (hazards.isEmpty()) return null;
        // Locale.ROOT: these are internal enum names, not user language.
        String names = hazards.stream()
            .map(h -> h.getType().name().toLowerCase(Locale.ROOT).replace('_', ' '))
            .reduce((a, b) -> a + " and " + b)
            .orElse("");
        return "Lifeguards have flagged " + names + " here.";
    }

    private String describeUv(BeachConditions conditions) {
        if (conditions == null || conditions.getUvIndex() == null) return null;
        if (conditions.getUvIndex() < 8) return null;
        return "UV is " + conditions.getUvLabel().toLowerCase(Locale.getDefault())
            + " — cover up and reapply sunscreen.";
    }

    private String advice(SafetyLevel level) {
        switch (level) {
            case DANGER:
                return "Stay out of the water and follow the flags and signs on the beach.";
            case CAUTION:
                return "Swim between the flags and keep a close eye on anyone in the water.";
            case SAFE:
            default:
                return "Still swim between the flags where a patrol is on duty.";
        }
    }
}
