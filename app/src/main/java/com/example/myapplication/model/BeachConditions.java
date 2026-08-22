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

    public String getFormattedUvIndex() {
        if (uvIndex == null) return "--";
        return String.format(java.util.Locale.US, "%s (%d)", getUvLabel(), uvIndex);
    }

    public String getUvLabel() {
        if (uvIndex == null) return "Unknown";
        if (uvIndex <= 2) return "Low";
        if (uvIndex <= 5) return "Moderate";
        if (uvIndex <= 7) return "Higher";
        if (uvIndex <= 10) return "Very High";
        return "Extreme";
    }

    public int getUvColorResId() {
        if (uvIndex == null) return android.R.color.darker_gray;
        if (uvIndex <= 2) return com.example.myapplication.R.color.uv_low;
        if (uvIndex <= 5) return com.example.myapplication.R.color.uv_moderate;
        if (uvIndex <= 7) return com.example.myapplication.R.color.uv_high;
        if (uvIndex <= 10) return com.example.myapplication.R.color.uv_very_high;
        return com.example.myapplication.R.color.uv_extreme;
    }
}
