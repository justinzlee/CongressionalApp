package com.example.myapplication.model;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import com.example.myapplication.R;

/**
 * What someone can say they saw at a beach.
 *
 * <p>Deliberately groups rather than species. An earlier version of this screen
 * offered dolphin, seal and sea lion, which asked the user to make an
 * identification they are usually not in a position to make — most animals
 * reported as seals on this coast are sea lions — and quietly refused every
 * other report a beach actually produces: the shark that closes the water, the
 * jellyfish bloom, the bird caught in line, the thing nobody could name. A
 * group the reporter can be confident about, plus a free-text note for the
 * detail, gets more true reports filed than a species list gets right.
 *
 * <p>The groups are cut where the <em>advice</em> differs, not where biology
 * does. Shark and ray are apart not because they are hard to confuse but
 * because a shark in the water raises a warning on this beach and a ray does
 * not, and one card could not tell them apart. Everything a beach can throw up
 * that is none of these — seals and sea lions included — lands in {@link
 * #OTHER}, so the advice a stranding gives has to hold for any animal at all:
 * see {@code ReportHazards}.
 *
 * <p>The declaration order is the order the cards are laid out in, three to a
 * row, so {@link #OTHER} last puts the none-of-these answer in the bottom right
 * where it belongs.
 */
public enum SightingSpecies {

    SHARK(R.string.sighting_species_shark,
        R.string.sighting_noun_shark, R.drawable.ic_shark),
    RAY(R.string.sighting_species_ray,
        R.string.sighting_noun_ray, R.drawable.ic_stingray),
    FISH(R.string.sighting_species_fish,
        R.string.sighting_noun_fish, R.drawable.ic_fish),
    JELLYFISH(R.string.sighting_species_jellyfish,
        R.string.sighting_noun_jellyfish, R.drawable.ic_jellyfish),
    BIRD(R.string.sighting_species_bird,
        R.string.sighting_noun_bird, R.drawable.ic_bird),
    OTHER(R.string.sighting_species_other,
        R.string.sighting_noun_other, R.drawable.ic_animal_other);

    @StringRes private final int labelRes;
    @StringRes private final int nounRes;
    @DrawableRes private final int iconRes;

    SightingSpecies(@StringRes int labelRes, @StringRes int nounRes, @DrawableRes int iconRes) {
        this.labelRes = labelRes;
        this.nounRes = nounRes;
        this.iconRes = iconRes;
    }

    /** The card label, capitalised: "Jellyfish", "Something else". */
    @StringRes
    public int getLabelRes() {
        return labelRes;
    }

    /**
     * The same thing as a bare noun for the middle of a sentence — "jellyfish",
     * "animal" — because the label does not always survive being lower-cased
     * into one. "Something else in the water at Moonlight Beach" is not a
     * sentence, and a Spanish label cannot be turned into a mid-sentence noun by
     * {@code toLowerCase} at all.
     *
     * <p>Every sentence built from this avoids "a"/"an": the article would have
     * to agree with a noun chosen at runtime, in every language the app ships.
     */
    @StringRes
    public int getNounRes() {
        return nounRes;
    }

    @DrawableRes
    public int getIconRes() {
        return iconRes;
    }

    /**
     * Stored in preferences by {@code name()}, so a value written by an older
     * version — or a newer one — must not take the screen down.
     *
     * <p>Anything unrecognised becomes {@link #OTHER}, which is also where the
     * {@code DOLPHIN}, {@code SEAL} and {@code SEA_LION} written by older builds
     * land. That is the honest answer rather than a lossy one: those reports
     * really were animals of a kind this build no longer names, and {@code
     * OTHER} is the only value that claims nothing false about them.
     */
    public static SightingSpecies parse(String name) {
        if (name != null) {
            for (SightingSpecies species : values()) {
                if (species.name().equals(name)) {
                    return species;
                }
            }
        }
        return OTHER;
    }
}
