package com.example.myapplication.model;

public class BeachConditions {
    private final Double airTemperature; // °F
    private final Integer uvIndex;
    private final Double windSpeed;      // mph
    private final Double waveHeight;     // feet
    private final Double visibility;     // miles

    public BeachConditions(Double airTemperature, Integer uvIndex, Double windSpeed, Double waveHeight, Double visibility) {
        this.airTemperature = airTemperature;
        this.uvIndex = uvIndex;
        this.windSpeed = windSpeed;
        this.waveHeight = waveHeight;
        this.visibility = visibility;
    }

    public Double getAirTemperature() { return airTemperature; }
    public Integer getUvIndex() { return uvIndex; }
    public Double getWindSpeed() { return windSpeed; }
    public Double getWaveHeight() { return waveHeight; }
    public Double getVisibility() { return visibility; }
}
