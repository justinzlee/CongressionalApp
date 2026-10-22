package com.example.myapplication.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.example.myapplication.SettingsActivity;
import com.example.myapplication.model.Sighting;
import com.example.myapplication.model.SightingSpecies;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The log of animals people have reported at each beach.
 *
 * <p>Unlike a rock rating, which is one current answer per beach, these are
 * events and every one of them is kept: a dolphin pod on Tuesday and a stranded
 * seal on Thursday are both true, and collapsing them would lose the thing that
 * makes the log worth reading. Newest first, because the only sighting anyone
 * scrolls for is the most recent one.
 *
 * <p>Stored as a JSON array per beach in the preferences file the rest of the
 * app already uses, through the same Gson tree API the forecast clients parse
 * with. A relational store would be the textbook answer and is far more
 * machinery than a list that realistically runs to single figures per beach.
 *
 * <p>Nothing here leaves the phone. A report is visible to whoever holds this
 * device — see {@code SightingActivity}, which is where a warning is actually
 * passed to other people, through the system share sheet and the stranding
 * hotline rather than through a server this app does not have.
 */
public class SightingStore {

    private static final String KEY_PREFIX = "sightings_";

    /** Where sighting photographs live, under the app's private files dir. */
    private static final String PHOTO_DIR = "sighting_photos";

    /**
     * How many reports are kept per beach. Well above what a beach realistically
     * collects, and there only so that a stuck finger on the save button cannot
     * grow a preferences file without limit. The oldest go first.
     */
    @VisibleForTesting
    static final int MAX_PER_BEACH = 40;

    private static final String FIELD_ID = "id";
    private static final String FIELD_SPECIES = "species";
    private static final String FIELD_STRANDED = "stranded";
    private static final String FIELD_DEAD = "dead";
    private static final String FIELD_NOTE = "note";
    private static final String FIELD_PHOTO = "photo";
    private static final String FIELD_AT = "at";

    private final Context app;
    private final SharedPreferences prefs;

    public SightingStore(Context context) {
        this.app = context.getApplicationContext();
        this.prefs = app.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** The directory sighting photographs are imported into before being saved. */
    public File photoDir() {
        return new File(app.getFilesDir(), PHOTO_DIR);
    }

    /** Everything reported at this beach, newest first. */
    public List<Sighting> forBeach(String beachId) {
        return decode(beachId, prefs.getString(KEY_PREFIX + beachId, null));
    }

    /**
     * The stranding that should be warning people at this beach right now, or
     * null. The newest wins if there is more than one: two reports of the same
     * animal are far likelier than two animals, and the newer one carries the
     * better photograph and the fresher timestamp.
     */
    @Nullable
    public Sighting activeWarningFor(String beachId, long now) {
        for (Sighting sighting : forBeach(beachId)) {
            if (sighting.isActiveWarning(now)) {
                return sighting;
            }
        }
        return null;
    }

    /**
     * Files a report and returns it.
     *
     * @param photo a picture already imported into {@link #photoDir()}, or null.
     *              Importing happens before this call so the user can see the
     *              photograph on the form before committing to the report.
     */
    public Sighting add(String beachId, SightingSpecies species, boolean stranded,
                        boolean dead, @Nullable String note, @Nullable File photo) {
        Sighting sighting = new Sighting(
            UUID.randomUUID().toString(),
            beachId,
            species,
            stranded,
            dead,
            emptyToNull(note),
            photo == null ? null : photo.getAbsolutePath(),
            System.currentTimeMillis());

        List<Sighting> all = new ArrayList<>(forBeach(beachId));
        all.add(sighting);
        write(beachId, all);
        return sighting;
    }

    /** Deletes a report and the photograph that went with it. */
    public void remove(String beachId, String sightingId) {
        List<Sighting> kept = new ArrayList<>();
        for (Sighting sighting : forBeach(beachId)) {
            if (sighting.getId().equals(sightingId)) {
                deletePhoto(sighting);
            } else {
                kept.add(sighting);
            }
        }
        write(beachId, kept);
    }

    /**
     * Writes the list back, newest first and trimmed to {@link #MAX_PER_BEACH}.
     *
     * <p>Photographs belonging to trimmed-off reports are deleted here. Nothing
     * else ever will: the report was the only record of where that file was, so
     * dropping it without the delete would leave the picture on disk forever.
     */
    private void write(String beachId, List<Sighting> sightings) {
        List<Sighting> ordered = new ArrayList<>(sightings);
        ordered.sort(Comparator.comparingLong(Sighting::getTimeMillis).reversed());

        if (ordered.size() > MAX_PER_BEACH) {
            for (Sighting dropped : ordered.subList(MAX_PER_BEACH, ordered.size())) {
                deletePhoto(dropped);
            }
            ordered = new ArrayList<>(ordered.subList(0, MAX_PER_BEACH));
        }

        if (ordered.isEmpty()) {
            prefs.edit().remove(KEY_PREFIX + beachId).apply();
        } else {
            prefs.edit().putString(KEY_PREFIX + beachId, encode(ordered).toString()).apply();
        }
    }

    private void deletePhoto(Sighting sighting) {
        File photo = sighting.getPhoto();
        if (photo != null) {
            photo.delete();
        }
    }

    @VisibleForTesting
    static JsonArray encode(List<Sighting> sightings) {
        JsonArray array = new JsonArray();
        for (Sighting sighting : sightings) {
            JsonObject json = new JsonObject();
            json.addProperty(FIELD_ID, sighting.getId());
            json.addProperty(FIELD_SPECIES, sighting.getSpecies().name());
            json.addProperty(FIELD_STRANDED, sighting.isStranded());
            if (sighting.isDead()) {
                json.addProperty(FIELD_DEAD, true);
            }
            json.addProperty(FIELD_AT, sighting.getTimeMillis());
            if (sighting.getNote() != null) {
                json.addProperty(FIELD_NOTE, sighting.getNote());
            }
            // The raw path, not getPhoto(): a picture that is temporarily
            // unreadable must not be silently dropped from the record on the
            // next write.
            String photoPath = sighting.rawPhotoPath();
            if (photoPath != null) {
                json.addProperty(FIELD_PHOTO, photoPath);
            }
            array.add(json);
        }
        return array;
    }

    /**
     * Reads a stored array back, newest first.
     *
     * <p>Forgiving on purpose. This parses a string out of a preferences file
     * that a restored backup or an older build of the app may have written, and
     * a malformed entry must cost that one report rather than the beach screen:
     * a bad element is skipped, and a bad array reads back as no reports at all.
     */
    @VisibleForTesting
    static List<Sighting> decode(String beachId, @Nullable String stored) {
        if (stored == null || stored.isEmpty()) return Collections.emptyList();

        JsonArray array;
        try {
            array = JsonParser.parseString(stored).getAsJsonArray();
        } catch (RuntimeException e) {
            return Collections.emptyList();
        }

        List<Sighting> sightings = new ArrayList<>();
        for (JsonElement element : array) {
            try {
                JsonObject json = element.getAsJsonObject();
                sightings.add(new Sighting(
                    json.get(FIELD_ID).getAsString(),
                    beachId,
                    SightingSpecies.parse(optString(json, FIELD_SPECIES)),
                    json.has(FIELD_STRANDED) && json.get(FIELD_STRANDED).getAsBoolean(),
                    json.has(FIELD_DEAD) && json.get(FIELD_DEAD).getAsBoolean(),
                    optString(json, FIELD_NOTE),
                    optString(json, FIELD_PHOTO),
                    json.get(FIELD_AT).getAsLong()));
            } catch (RuntimeException e) {
                // Missing id or timestamp: there is no usable report here.
            }
        }
        sightings.sort(Comparator.comparingLong(Sighting::getTimeMillis).reversed());
        return sightings;
    }

    @Nullable
    private static String optString(JsonObject json, String field) {
        JsonElement element = json.get(field);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    @Nullable
    private static String emptyToNull(@Nullable String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
