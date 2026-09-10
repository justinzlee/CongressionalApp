package com.example.myapplication.model;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * One animal somebody logged at a beach: what it was, when, optionally a
 * photograph, and whether it was washed up on the shore rather than out
 * swimming.
 *
 * <p>That last flag is the whole difference between the two things this record
 * does. A dolphin off the break is good news and belongs on the beach screen as
 * a note. A seal lying on the sand is a stranded animal — it may be sick,
 * injured, or simply resting, and a member of the public cannot tell which —
 * and it needs to reach the people about to walk their dog past it. Same record,
 * same photograph, different urgency, so the flag rides on the sighting rather
 * than splitting into two parallel types that would drift apart.
 *
 * <p>Immutable, and carries its own timestamp. Reports age: see {@link
 * #isActiveWarning(long)}.
 */
public final class Sighting {

    /**
     * How long a stranding stays up as an active warning.
     *
     * <p>Two days. A stranded animal is resolved one way or the other well
     * inside that — the rescue network collects it, the tide takes it back, or
     * it moves off on its own — and a warning that outlives the animal is worse
     * than no warning, because the next real one gets read as stale too. The
     * report itself is kept and still shows on the beach screen with its date;
     * it just stops raising a hazard.
     */
    private static final long WARNING_LIFETIME_MILLIS = TimeUnit.DAYS.toMillis(2);

    private final String id;
    private final String beachId;
    private final SightingSpecies species;
    private final boolean stranded;
    @Nullable private final String note;
    @Nullable private final String photoPath;
    private final long timeMillis;

    public Sighting(String id, String beachId, SightingSpecies species, boolean stranded,
                    @Nullable String note, @Nullable String photoPath, long timeMillis) {
        this.id = id;
        this.beachId = beachId;
        this.species = species;
        this.stranded = stranded;
        this.note = note;
        this.photoPath = photoPath;
        this.timeMillis = timeMillis;
    }

    public String getId() { return id; }
    public String getBeachId() { return beachId; }
    public SightingSpecies getSpecies() { return species; }
    public long getTimeMillis() { return timeMillis; }

    /** On the sand rather than in the water: the case that warrants a warning. */
    public boolean isStranded() { return stranded; }

    @Nullable public String getNote() { return note; }

    /**
     * The photograph taken with the report, or null if there is none or the
     * file has since gone. Checked on every read rather than trusted, because
     * the path outlives the file when app storage is cleared or a backup is
     * restored onto a phone that never held the picture.
     */
    @Nullable
    public File getPhoto() {
        if (photoPath == null) return null;
        File photo = new File(photoPath);
        return photo.exists() ? photo : null;
    }

    /**
     * The stored path as written, whether or not a file is there now.
     *
     * <p>{@link #getPhoto()} is what screens should draw; this is for the store
     * writing the record back out. Re-encoding from {@code getPhoto()} would
     * quietly drop the path of any picture that happened to be unreadable at
     * that moment — an SD card not yet mounted, a restore still running — and
     * the record would lose its photograph permanently.
     */
    @Nullable
    public String rawPhotoPath() {
        return photoPath;
    }

    /**
     * Whether this report should still be warning people as of {@code now}.
     *
     * <p>Only strandings ever warn — a dolphin sighting is never a hazard, no
     * matter how recent — and only for {@link #WARNING_LIFETIME_MILLIS}. A
     * report timestamped in the future is treated as active: phone clocks are
     * wrong often enough that discarding it would silently drop a real warning,
     * and the failure that matters here is the one that stays quiet.
     */
    public boolean isActiveWarning(long now) {
        if (!stranded) return false;
        return now - timeMillis < WARNING_LIFETIME_MILLIS;
    }
}
