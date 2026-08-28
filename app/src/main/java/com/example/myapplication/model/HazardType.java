package com.example.myapplication.model;

import androidx.annotation.StringRes;

import com.example.myapplication.R;

public enum HazardType {
    RIP_CURRENT(R.string.hazard_rip_current),
    MARINE_LIFE(R.string.hazard_marine_life),
    WATER_QUALITY(R.string.hazard_water_quality),
    AIR_QUALITY(R.string.hazard_air_quality),
    POLLUTION(R.string.hazard_pollution),
    ROCKS_AND_STRUCTURES(R.string.hazard_rocks),
    UV_INDEX(R.string.hazard_uv_index);

    @StringRes private final int labelRes;

    HazardType(@StringRes int labelRes) {
        this.labelRes = labelRes;
    }

    /**
     * Display name for this hazard. Derived enum names read badly once
     * lower-cased ("Uv index"), so each carries its own written label.
     */
    @StringRes
    public int getLabelRes() {
        return labelRes;
    }
}
