package com.example.myapplication.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Beach {
    private final String id;
    private final String name;
    private final double latitude;
    private final double longitude;
    private final List<Hazard> hazards;
    private final BeachConditions conditions;
    private final List<HelpLocation> nearestHelp;
    private final String imageUrl;

    public Beach(String id, String name, double latitude, double longitude, List<Hazard> hazards, BeachConditions conditions, List<HelpLocation> nearestHelp, String imageUrl) {
        this.id = id;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.hazards = Objects.requireNonNullElse(hazards, Collections.emptyList());
        this.conditions = conditions;
        this.nearestHelp = Objects.requireNonNullElse(nearestHelp, Collections.emptyList());
        this.imageUrl = imageUrl;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public List<Hazard> getHazards() { return hazards; }
    public BeachConditions getConditions() { return conditions; }
    public List<HelpLocation> getNearestHelp() { return nearestHelp; }
    public String getImageUrl() { return imageUrl; }

    public Beach withConditions(BeachConditions newConditions) {
        return new Beach(id, name, latitude, longitude, hazards, newConditions, nearestHelp, imageUrl);
    }
}
