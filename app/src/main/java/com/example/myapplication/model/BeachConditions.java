package com.example.myapplication.model;

import android.content.Context;

import androidx.annotation.ColorRes;

import com.example.myapplication.R;

public class BeachConditions {
    private final Double airTemperature;   // °F
    private final Double waterTemperature; // °F
    private final Integer uvIndex;
    private final Double windSpeed;        // mph
    private final Double waveHeight;       // feet
    private final Double visibility;       // miles
    private final long fetchedAtMillis;

    /** Readings that have just come in. */
    public BeachConditions(Double airTemperature, Integer uvIndex, Double windSpeed, Double waveHeight, Double visibility) {
        this(airTemperature, null, uvIndex, windSpeed, waveHeight, visibility, System.currentTimeMillis());
    }

    /**
     * @param fetchedAtMillis when the readings came in, so a screen can say how
     *                        old they are instead of calling them current
     */
    public BeachConditions(Double airTemperature, Integer uvIndex, Double windSpeed, Double waveHeight, Double visibility, long fetchedAtMillis) {
        this(airTemperature, null, uvIndex, windSpeed, waveHeight, visibility, fetchedAtMillis);
    }

    public BeachConditions(Double airTemperature, Double waterTemperature, Integer uvIndex, Double windSpeed, Double waveHeight, Double visibility) {
        this(airTemperature, waterTemperature, uvIndex, windSpeed, waveHeight, visibility, System.currentTimeMillis());
    }

    public BeachConditions(Double airTemperature, Double waterTemperature, Integer uvIndex, Double windSpeed, Double waveHeight, Double visibility, long fetchedAtMillis) {
        this.airTemperature = airTemperature;
        this.waterTemperature = waterTemperature;
        this.uvIndex = uvIndex;
        this.windSpeed = windSpeed;
        this.waveHeight = waveHeight;
        this.visibility = visibility;
        this.fetchedAtMillis = fetchedAtMillis;
    }

    public Double getAirTemperature() { return airTemperature; }
    public Double getWaterTemperature() { return waterTemperature; }
    public Integer getUvIndex() { return uvIndex; }
    public Double getWindSpeed() { return windSpeed; }
    public Double getWaveHeight() { return waveHeight; }
    public Double getVisibility() { return visibility; }
    public long getFetchedAtMillis() { return fetchedAtMillis; }

    public String getFormattedUvIndex(Context context) {
        if (uvIndex == null) return context.getString(R.string.placeholder_dashes);
        return context.getString(R.string.uv_reading, getUvLabel(context), uvIndex);
    }

    /**
     * What this reading is called — "Very high" and the rest. The band names
     * live in {@link UvLevel} so this reading and the UV lesson in the Learn
     * tab always use the same word for the same number.
     */
    public String getUvLabel(Context context) {
        if (uvIndex == null) return context.getString(R.string.uv_level_unknown);
        return context.getString(UvLevel.of(uvIndex).getLabelRes());
    }

    @ColorRes
    public int getUvColorResId() {
        if (uvIndex == null) return android.R.color.darker_gray;
        return UvLevel.of(uvIndex).getColorRes();
    }
}
