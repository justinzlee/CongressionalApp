package com.example.myapplication.model;

public class BeachConditions {
    private final Double waterTemperature;
    private final Double airTemperature;
    private final Integer uvIndex;
    private final Double windSpeed;
    private final Double waveHeight;

    public BeachConditions(Double waterTemperature, Double airTemperature, Integer uvIndex, Double windSpeed, Double waveHeight) {
        this.waterTemperature = waterTemperature;
        this.airTemperature = airTemperature;
        this.uvIndex = uvIndex;
        this.windSpeed = windSpeed;
        this.waveHeight = waveHeight;
    }

    public Double getWaterTemperature() { return waterTemperature; }
    public Double getAirTemperature() { return airTemperature; }
    public Integer getUvIndex() { return uvIndex; }
    public Double getWindSpeed() { return windSpeed; }
    public Double getWaveHeight() { return waveHeight; }
}
