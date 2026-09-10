package com.example.myapplication.model;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import com.example.myapplication.R;

/**
 * The marine mammals this app lets someone log from the beach.
 *
 * <p>Deliberately three, not thirty. This is a safety app, not a wildlife
 * survey: the list covers what turns up on a public swimming beach often enough
 * to matter, and every one of them is protected under the Marine Mammal
 * Protection Act, so the same "keep your distance" rule applies to all three
 * and the app never has to decide which sighting is the legally sensitive one.
 *
 * <p>Sea lions are here as their own entry rather than folded into seals
 * because on the Pacific coast most animals reported as seals are sea lions,
 * and the two behave differently when approached — a hauled-out sea lion will
 * come at you and can outrun a person over sand.
 */
public enum SightingSpecies {

    DOLPHIN(R.string.sighting_species_dolphin, R.drawable.ic_dolphin),
    SEAL(R.string.sighting_species_seal, R.drawable.ic_seal),
    SEA_LION(R.string.sighting_species_sea_lion, R.drawable.ic_seal);

    @StringRes private final int labelRes;
    @DrawableRes private final int iconRes;

    SightingSpecies(@StringRes int labelRes, @DrawableRes int iconRes) {
        this.labelRes = labelRes;
        this.iconRes = iconRes;
    }

    @StringRes
    public int getLabelRes() {
        return labelRes;
    }

    /**
     * Seals and sea lions share a drawing. They share a silhouette hauled out on
     * sand too — which is exactly why the app offers both names — and drawing a
     * distinction the icon cannot carry would only suggest the app can tell them
     * apart from a phone in a pocket.
     */
    @DrawableRes
    public int getIconRes() {
        return iconRes;
    }

    /**
     * Stored in preferences by {@code name()}, so a value written by an older
     * version — or a newer one — must not take the screen down. Anything
     * unrecognised reads back as a seal, the most common report and the one
     * whose advice is safe to give about any of the three.
     */
    public static SightingSpecies parse(String name) {
        if (name != null) {
            for (SightingSpecies species : values()) {
                if (species.name().equals(name)) {
                    return species;
                }
            }
        }
        return SEAL;
    }
}
