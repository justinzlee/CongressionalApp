package com.example.myapplication.model;

public class HelpLocation {
    private final String name;
    private final HelpType type;
    private final double latitude;
    private final double longitude;

    public HelpLocation(String name, HelpType type, double latitude, double longitude) {
        this.name = name;
        this.type = type;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getName() { return name; }
    public HelpType getType() { return type; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
}
