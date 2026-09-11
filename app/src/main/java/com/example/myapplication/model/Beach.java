package com.example.myapplication.model;

import androidx.annotation.Nullable;

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
    private final String tideStation;

    /** A beach with no tide station to read from. */
    public Beach(String id, String name, double latitude, double longitude, List<Hazard> hazards, BeachConditions conditions, List<HelpLocation> nearestHelp, String imageUrl) {
        this(id, name, latitude, longitude, hazards, conditions, nearestHelp, imageUrl, null);
    }

    public Beach(String id, String name, double latitude, double longitude, List<Hazard> hazards, BeachConditions conditions, List<HelpLocation> nearestHelp, String imageUrl, @Nullable String tideStation) {
        this.id = id;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.hazards = Objects.requireNonNullElse(hazards, Collections.emptyList());
        this.conditions = conditions;
        this.nearestHelp = Objects.requireNonNullElse(nearestHelp, Collections.emptyList());
        this.imageUrl = imageUrl;
        this.tideStation = tideStation;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public List<Hazard> getHazards() { return hazards; }
    public BeachConditions getConditions() { return conditions; }
    public List<HelpLocation> getNearestHelp() { return nearestHelp; }
    public String getImageUrl() { return imageUrl; }

    /**
     * The NOAA tide-prediction station this beach's tides are read from, or
     * null where there is no tide to speak of — the Great Lakes.
     */
    @Nullable
    public String getTideStation() { return tideStation; }

    public Beach withConditions(BeachConditions newConditions) {
        return new Beach(id, name, latitude, longitude, hazards, newConditions, nearestHelp, imageUrl, tideStation);
    }

    public Beach withImageUrl(String newImageUrl) {
        return new Beach(id, name, latitude, longitude, hazards, conditions, nearestHelp, newImageUrl, tideStation);
    }
}
