package com.example.myapplication.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.example.myapplication.SettingsActivity;
import com.example.myapplication.model.RockRating;

import java.io.File;

/**
 * Remembers how rocky each beach was the last time somebody rated it.
 *
 * <p>One rating per beach, not a history. The question this answers is "what
 * will I be standing on if I go today", and for that the most recent answer is
 * the only one that matters — a rating from two summers ago, before the winter
 * storms moved the shingle, would be worse than none. Re-rating a beach
 * overwrites what was there.
 *
 * <p>The optional photograph is the rating's evidence: it is what lets the next
 * person see whether they would have called it the same number. It goes through
 * {@link PhotoImporter} like every other picture in the app and stays on this
 * phone.
 */
public class RockRatingStore {

    private static final String KEY_RATING_PREFIX = "rock_rating_";
    private static final String KEY_PHOTO_PREFIX = "rock_photo_";
    private static final String KEY_RATED_AT_PREFIX = "rock_rated_at_";

    /** Where the rating photographs live, under the app's private files dir. */
    private static final String PHOTO_DIR = "rock_photos";

    private final Context app;
    private final SharedPreferences prefs;

    /**
     * Made on first use. A store is built every time a hazard list is worked
     * out - for every row of the home list, every alert and the tab badge -
     * and nearly all of those only read a rating, so an importer (and the
     * main-thread handler it holds) per store was spent for nothing.
     */
    @Nullable private PhotoImporter importer;

    public RockRatingStore(Context context) {
        this.app = context.getApplicationContext();
        this.prefs = app.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * This beach's rating, or {@link RockRating#UNRATED} if nobody has given it
     * one. Anything stored outside the scale — by a restored backup, or a
     * version of this app that had a different scale — reads back as unrated
     * rather than being clamped into a number nobody actually chose.
     */
    public int ratingFor(String beachId) {
        int stored = prefs.getInt(KEY_RATING_PREFIX + beachId, RockRating.UNRATED);
        return RockRating.isRated(stored) ? stored : RockRating.UNRATED;
    }

    public boolean isRated(String beachId) {
        return RockRating.isRated(ratingFor(beachId));
    }

    /** When the rating was given, for the "rated in March" line under it. */
    public long ratedAt(String beachId) {
        return prefs.getLong(KEY_RATED_AT_PREFIX + beachId, 0L);
    }

    /** Records the rating, stamped with now. */
    public void save(String beachId, int rating) {
        prefs.edit()
            .putInt(KEY_RATING_PREFIX + beachId, RockRating.clamp(rating))
            .putLong(KEY_RATED_AT_PREFIX + beachId, System.currentTimeMillis())
            .apply();
    }

    /** The photograph backing this beach's rating, or null. */
    @Nullable
    public File photoFor(String beachId) {
        String path = prefs.getString(KEY_PHOTO_PREFIX + beachId, null);
        if (path == null) return null;
        File photo = new File(path);
        // Same as the beach hero: a path can outlive its file when storage is
        // cleared or a backup lands on a phone that never held the picture.
        if (!photo.exists()) {
            prefs.edit().remove(KEY_PHOTO_PREFIX + beachId).apply();
            return null;
        }
        return photo;
    }

    /**
     * Copies in a picture of the rocks, replacing any earlier one, and reports
     * back on the main thread.
     */
    public void submitPhoto(String beachId, Uri source, PhotoImporter.Callback callback) {
        File dir = new File(app.getFilesDir(), PHOTO_DIR);
        if (importer == null) importer = new PhotoImporter(app);
        importer.importInto(dir, beachId, source, new PhotoImporter.Callback() {
            @Override
            public void onSaved(File photo) {
                File previous = photoFor(beachId);
                prefs.edit().putString(KEY_PHOTO_PREFIX + beachId, photo.getAbsolutePath()).apply();
                if (previous != null) {
                    previous.delete();
                }
                callback.onSaved(photo);
            }

            @Override
            public void onFailed(Exception e) {
                callback.onFailed(e);
            }
        });
    }

    public void removePhoto(String beachId) {
        File photo = photoFor(beachId);
        prefs.edit().remove(KEY_PHOTO_PREFIX + beachId).apply();
        if (photo != null) {
            photo.delete();
        }
    }

    /** Takes the whole rating back off the beach, photograph included. */
    public void clear(String beachId) {
        removePhoto(beachId);
        prefs.edit()
            .remove(KEY_RATING_PREFIX + beachId)
            .remove(KEY_RATED_AT_PREFIX + beachId)
            .apply();
    }
}
