package com.example.myapplication.model;

import androidx.annotation.StringRes;

import com.example.myapplication.R;

public enum Severity {
    LOW(R.string.severity_low),
    MODERATE(R.string.severity_moderate),
    HIGH(R.string.severity_high),
    EXTREME(R.string.severity_extreme);

    @StringRes private final int labelRes;

    Severity(@StringRes int labelRes) {
        this.labelRes = labelRes;
    }

    /**
     * How the Alerts rows name this. Written out rather than tidied up from the
     * enum name, which only ever reads as English.
     */
    @StringRes
    public int getLabelRes() {
        return labelRes;
    }
}
