package com.example.myapplication.ai;

import com.example.myapplication.model.Beach;
import com.example.myapplication.model.BeachConditions;
import com.example.myapplication.model.Hazard;
import com.example.myapplication.model.HelpLocation;
import com.example.myapplication.util.UnitConverter;
import android.content.Context;
import java.util.List;
import java.util.stream.Collectors;

public class SafetyAdvisor {
    public String getBeachSummary(Context context, Beach beach) {
        StringBuilder summary = new StringBuilder();
        summary.append("Safety Summary for ").append(beach.getName()).append(":\n\n");

        BeachConditions conditions = beach.getConditions();
        if (conditions != null) {
            String tempText = UnitConverter.formatTemperature(context, conditions.getAirTemperature());
            String windText = UnitConverter.formatWindSpeed(context, conditions.getWindSpeed());
            String waveText = UnitConverter.formatWaveHeight(context, conditions.getWaveHeight());
            String visibilityText = UnitConverter.formatVisibility(context, conditions.getVisibility());
            
            summary.append("Weather: The air temperature is ")
                   .append(tempText).append(" with a UV index of ")
                   .append(conditions.getFormattedUvIndex()).append(". ");
            summary.append("Sea conditions show ")
                   .append(waveText).append(" waves and wind at ")
                   .append(windText).append(". ");
            summary.append("Visibility is currently at ")
                   .append(visibilityText).append(".\n\n");
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

    /** Builds a multi-beach digest for a "beaches near me" style report. */
    public String getNearbyBeachesReport(Context context, List<Beach> nearbyBeaches, double userLat, double userLon) {
        if (nearbyBeaches.isEmpty()) {
            return "No beaches found near your current location.";
        }

        StringBuilder report = new StringBuilder();
        report.append(nearbyBeaches.size())
              .append(nearbyBeaches.size() == 1 ? " beach found near you:\n\n" : " beaches found near you:\n\n");

        for (Beach beach : nearbyBeaches) {
            double distance = UnitConverter.calculateDistance(userLat, userLon, beach.getLatitude(), beach.getLongitude());
            report.append("- ").append(beach.getName())
                  .append(" (").append(UnitConverter.formatDistance(context, distance)).append(")\n");

            BeachConditions conditions = beach.getConditions();
            if (conditions != null) {
                report.append("   Air: ").append(UnitConverter.formatTemperature(context, conditions.getAirTemperature()))
                      .append(" | Waves: ").append(UnitConverter.formatWaveHeight(context, conditions.getWaveHeight()))
                      .append(" | UV: ").append(conditions.getFormattedUvIndex()).append("\n");
            }

            List<Hazard> hazards = beach.getHazards();
            if (hazards == null || hazards.isEmpty()) {
                report.append("   No major hazards reported.\n\n");
            } else {
                String hazardNames = hazards.stream()
                    .map(h -> h.getType().name().toLowerCase().replace("_", " "))
                    .collect(Collectors.joining(", "));
                report.append("   Active alerts: ").append(hazardNames).append("\n\n");
            }
        }

        return report.toString().trim();
    }
}
