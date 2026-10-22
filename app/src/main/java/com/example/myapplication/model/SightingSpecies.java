package com.example.myapplication.model;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import com.example.myapplication.R;

/**
 * What someone can say they saw at a beach.
 *
 * <p>Mostly groups rather than species. A list of species asks the user to make
 * an identification they are usually not in a position to make, and quietly
 * refuses every report that fits none of them: the jellyfish bloom, the bird
 * caught in line, the thing nobody could name. A group the reporter can be
 * confident about, plus a free-text note for the detail, gets more true reports
 * filed than a species list gets right.
 *
 * <p>The groups are cut where the <em>advice</em> differs, not where biology
 * does. Shark and ray are apart not because they are hard to confuse but
 * because a shark in the water raises a warning on this beach and a ray does
 * not. {@link #SEAL} covers sea lions too — most animals reported as seals on
 * this coast are sea lions, and the advice for the two is the same — and is the
 * one group that also asks whether the animal was alive or dead, because a
 * dead one is a disease risk rather than an animal to give room to. {@link
 * #SEAGULL} sits apart from {@link #BIRD} because gulls are the bird every
 * beach has and the one that takes food out of people's hands; everything else
 * with feathers is a bird. Whatever is none of these lands in {@link #OTHER},
 * so the advice a stranding gives has to hold for any animal at all: see
 * {@code ReportHazards}.
 *
 * <p>The declaration order is the order the cards are laid out in, three to a
 * row, which puts {@link #OTHER} last.
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
    SEAL(R.string.sighting_species_seal,
        R.string.sighting_noun_seal, R.drawable.ic_seal),
    SEAGULL(R.string.sighting_species_seagull,
        R.string.sighting_noun_seagull, R.drawable.ic_seagull),
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
     * {@code DOLPHIN} written by older builds lands: that report really was an
     * animal of a kind this build does not name, and {@code OTHER} is the only
     * value that claims nothing false about it. The {@code SEA_LION} those
     * builds wrote reads back as {@link #SEAL}, the card that now covers it.
     */
    public static SightingSpecies parse(String name) {
        if ("SEA_LION".equals(name)) return SEAL;
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
