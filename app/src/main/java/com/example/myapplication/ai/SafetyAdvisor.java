package com.example.myapplication.ai;

import com.example.myapplication.model.Beach;
import com.example.myapplication.model.BeachConditions;
import com.example.myapplication.model.Hazard;
import com.example.myapplication.model.HelpLocation;
import java.util.List;
import java.util.stream.Collectors;

public class SafetyAdvisor {
    public String getBeachSummary(Beach beach) {
        StringBuilder summary = new StringBuilder();
        summary.append("AI Safety Summary for ").append(beach.getName()).append(":\n\n");

        BeachConditions conditions = beach.getConditions();
        if (conditions != null) {
            summary.append("Weather: The air temperature is ")
                   .append(conditions.getAirTemperature()).append("°F with a UV index of ")
                   .append(conditions.getUvIndex()).append(". ");
            summary.append("Sea conditions show ")
                   .append(conditions.getWaveHeight()).append("m waves and wind at ")
                   .append(conditions.getWindSpeed()).append("km/h. ");
            summary.append("Visibility is currently at ")
                   .append(conditions.getVisibility()).append("km.\n\n");
        }

        List<Hazard> hazards = beach.getHazards();
        if (hazards == null || hazards.isEmpty()) {
            summary.append("Hazards: No major hazards reported. Conditions look favorable for swimming.\n\n");
        } else {
            String hazardNames = hazards.stream()
                .map(h -> h.getType().name().toLowerCase().replace("_", " "))
                .collect(Collectors.joining(", "));
            summary.append("Hazards: Active alerts for ").append(hazardNames).append(". ");
            
            Hazard first = hazards.get(0);
            if (!first.getTreatmentSteps().isEmpty()) {
                summary.append("Recommended action: ").append(String.join(". ", first.getTreatmentSteps())).append(".\n\n");
            } else {
                summary.append("\n\n");
            }
        }

        List<HelpLocation> help = beach.getNearestHelp();
        if (help != null && !help.isEmpty()) {
            summary.append("Nearby Help: Assistance is available at ")
                   .append(help.get(0).getName()).append(" (")
                   .append(help.get(0).getType()).append(").");
        } else {
            summary.append("Nearby Help: No specific emergency services listed nearby. Always have a charged phone.");
        }

        return summary.toString();
    }

    // Keep the old method signature for compatibility if needed elsewhere, but mark as deprecated or update it
    public String getAdvice(List<Hazard> hazards) {
        if (hazards == null || hazards.isEmpty()) {
            return "Conditions look great! Still, always swim between the flags.";
        }
        return "Multiple hazards detected. Please check the beach details for a full AI safety summary.";
    }
}
