package com.example.myapplication.model;

import androidx.annotation.StringRes;

import com.example.myapplication.R;

public enum HazardType {
    HIGH_SURF(R.string.hazard_high_surf, R.string.hazard_high_surf_inline),
    RIP_CURRENT(R.string.hazard_rip_current, R.string.hazard_rip_current_inline),
    MARINE_LIFE(R.string.hazard_marine_life, R.string.hazard_marine_life_inline),
    WATER_QUALITY(R.string.hazard_water_quality, R.string.hazard_water_quality_inline),
    AIR_QUALITY(R.string.hazard_air_quality, R.string.hazard_air_quality_inline),
    POLLUTION(R.string.hazard_pollution, R.string.hazard_pollution_inline),
    ROCKS_AND_STRUCTURES(R.string.hazard_rocks, R.string.hazard_rocks_inline),
    UV_INDEX(R.string.hazard_uv_index, R.string.hazard_uv_index_inline);

    @StringRes private final int labelRes;
    @StringRes private final int inlineLabelRes;

    HazardType(@StringRes int labelRes, @StringRes int inlineLabelRes) {
        this.labelRes = labelRes;
        this.inlineLabelRes = inlineLabelRes;
    }

    /**
     * Title-case name, for headings and list rows. Derived enum names read
     * badly once lower-cased ("Uv index"), so each carries a written label.
     */
    @StringRes
    public int getLabelRes() {
        return labelRes;
    }

    /**
     * Mid-sentence form. Most hazards lower-case here ("a rip current
     * warning"), but initialisms must not ("a UV index warning"), so the two
     * forms cannot be derived from one another.
     */
    @StringRes
    public int getInlineLabelRes() {
        return inlineLabelRes;
    }
}
