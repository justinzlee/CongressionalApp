package com.example.myapplication.ai;

import com.example.myapplication.model.Hazard;
import java.util.List;
import java.util.stream.Collectors;

public class SafetyAdvisor {
    public String getAdvice(List<Hazard> hazards) {
        if (hazards == null || hazards.isEmpty()) {
            return "Conditions look great! Still, always swim between the flags.";
        }

        String hazardNames = hazards.stream()
            .map(h -> h.getType().name().toLowerCase().replace("_", " "))
            .collect(Collectors.joining(", "));

        Hazard firstHazard = hazards.get(0);
        String advice = "AI Advisor: I see there are " + hazardNames + " today. ";
        if (!firstHazard.getTreatmentSteps().isEmpty()) {
            advice += "Specifically, for " + firstHazard.getType().name().toLowerCase().replace("_", " ") + 
                      ", you should: " + String.join(". ", firstHazard.getTreatmentSteps()) + ".";
        }
        return advice;
    }
}
