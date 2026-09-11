package com.example.myapplication.model;

import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.example.myapplication.R;

/**
 * The air over a beach: the US AQI and the pollutants behind it.
 *
 * <p>Worth its own reading on a beach app rather than being left to a weather
 * widget. The two things that actually drive it on a coast are wildfire smoke,
 * which can put a beach into the unhealthy bands on an otherwise perfect blue
 * day, and ozone, which peaks in the same hot still afternoons that look best
 * for a picnic. Both are invisible, so a number is the only way anyone knows.
 *
 * <p>The bands are the EPA's own breakpoints, unchanged. They are not a scale
 * this app invented and must not drift from the official one, because people
 * with asthma read the same categories off every other source.
 */
public final class AirQuality {

    /** EPA band boundaries on the 0-500 US AQI scale. */
    public static final int GOOD_MAX = 50;
    public static final int MODERATE_MAX = 100;
    public static final int SENSITIVE_MAX = 150;
    public static final int UNHEALTHY_MAX = 200;
    public static final int VERY_UNHEALTHY_MAX = 300;

    private final int usAqi;
    @Nullable private final Double pm25;
    @Nullable private final Double pm10;
    @Nullable private final Double ozone;

    public AirQuality(int usAqi, @Nullable Double pm25, @Nullable Double pm10,
                      @Nullable Double ozone) {
        this.usAqi = usAqi;
        this.pm25 = pm25;
        this.pm10 = pm10;
        this.ozone = ozone;
    }

    public int getUsAqi() { return usAqi; }
    @Nullable public Double getPm25() { return pm25; }
    @Nullable public Double getPm10() { return pm10; }
    @Nullable public Double getOzone() { return ozone; }

    /** "Good", "Moderate", "Unhealthy for sensitive groups"... */
    @StringRes
    public int getLabelRes() {
        if (usAqi <= GOOD_MAX) return R.string.aqi_good;
        if (usAqi <= MODERATE_MAX) return R.string.aqi_moderate;
        if (usAqi <= SENSITIVE_MAX) return R.string.aqi_sensitive;
        if (usAqi <= UNHEALTHY_MAX) return R.string.aqi_unhealthy;
        if (usAqi <= VERY_UNHEALTHY_MAX) return R.string.aqi_very_unhealthy;
        return R.string.aqi_hazardous;
    }

    /**
     * What it means for someone about to spend an afternoon outside. The
     * sensitive-groups band is the one people misread as "fine for me", so its
     * line names who it is actually about.
     */
    @StringRes
    public int getAdviceRes() {
        if (usAqi <= GOOD_MAX) return R.string.aqi_advice_good;
        if (usAqi <= MODERATE_MAX) return R.string.aqi_advice_moderate;
        if (usAqi <= SENSITIVE_MAX) return R.string.aqi_advice_sensitive;
        if (usAqi <= UNHEALTHY_MAX) return R.string.aqi_advice_unhealthy;
        return R.string.aqi_advice_hazardous;
    }

    /**
     * Reuses the app's UV ramp rather than the EPA's own green-to-maroon.
     * Introducing a second colour scale would mean two different meanings for
     * amber on one screen; the safety palette already says "fine / take care /
     * do not" and that is the same question being asked here.
     */
    @ColorRes
    public int getColorRes() {
        if (usAqi <= GOOD_MAX) return R.color.safe;
        if (usAqi <= MODERATE_MAX) return R.color.caution;
        if (usAqi <= SENSITIVE_MAX) return R.color.uv_high;
        return R.color.danger;
    }

    /**
     * 0-100, how much this air helps or hurts a day spent outdoors, for the
     * activity scores. Deliberately not a straight inversion of the AQI: the
     * whole 0-50 band is genuinely unremarkable air and should not separate two
     * beaches at all, while everything past 150 is bad enough that no amount of
     * good surf makes up for it.
     */
    public int outdoorScore() {
        if (usAqi <= GOOD_MAX) return 100;
        if (usAqi >= UNHEALTHY_MAX) return 0;
        // Linear from the top of Good down to Unhealthy.
        int span = UNHEALTHY_MAX - GOOD_MAX;
        return Math.max(0, Math.round(100f * (UNHEALTHY_MAX - usAqi) / span));
    }
}
