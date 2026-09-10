package com.example.myapplication.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.Headers;
import com.bumptech.glide.load.model.LazyHeaders;
import com.example.myapplication.SettingsActivity;
import com.example.myapplication.model.Beach;

import java.io.File;

/**
 * Holds the photographs people take of a beach themselves.
 *
 * <p>Most beaches in this app have no photograph of their own — the compiled-in
 * list only carries one for the handful with a usable picture on Wikimedia
 * Commons, and everything else falls back to the drawn shoreline. Someone
 * standing on the sand has the missing photograph in their pocket, so this lets
 * them put it in, and from then on theirs is what the list row and the detail
 * hero show for that beach instead of the default.
 *
 * <p>The photo stays on the phone it was added from. There is no server behind
 * this app — the beach list is compiled in and the only network calls are to
 * public read-only forecast APIs — so a submitted photo is copied into the
 * app's own private storage and its path recorded in the same preferences file
 * the rest of the app uses. Nothing is uploaded, and no other user sees it.
 *
 * <p>The scaling, turning and re-encoding all happen in {@link PhotoImporter},
 * which the rock ratings and wildlife sightings share; this class is only the
 * record of which file is the current photo for which beach.
 */
public class BeachPhotoStore {

    /** One preference key per beach, holding the absolute path of its photo. */
    private static final String KEY_PREFIX = "beach_photo_";

    /** Where the re-encoded copies live, under the app's private files dir. */
    private static final String PHOTO_DIR = "beach_photos";

    /** Delivered on the main thread once a submitted photo is stored, or is not. */
    public interface SaveCallback {
        void onSaved(File photo);
        void onFailed(Exception e);
    }

    private final Context app;
    private final SharedPreferences prefs;
    private final PhotoImporter importer;

    /**
     * Who is asking, sent with every curated photo. Wikimedia throttles
     * requests that carry a generic client string, which is what Glide sends
     * unless told otherwise ("Dalvik/2.1.0 ..."): in a quick run through the
     * curated photos a third came back 429 Too Many Requests and those rows
     * fell back to the drawing, where the same run naming the app loaded every
     * one. See https://meta.wikimedia.org/wiki/User-Agent_policy
     *
     * <p>Built per instance rather than statically: Glide's header builder
     * reads the platform the first time it is touched, and a static field
     * would do that while the class was still loading — which takes the whole
     * class down in a plain JVM test, where the framework is only a stub that
     * throws. {@link PhotoImporter} holds its main-thread Handler per instance
     * for the same reason.
     */
    private final Headers remoteHeaders;

    public BeachPhotoStore(Context context) {
        this.app = context.getApplicationContext();
        this.prefs = app.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
        this.importer = new PhotoImporter(app);
        this.remoteHeaders = new LazyHeaders.Builder()
            .setHeader("User-Agent", "Sandbar/1.0 (https://github.com/justinzlee/CongressionalApp) Android")
            .build();
    }

    /** The photo submitted for this beach, or null if nobody has added one. */
    @Nullable
    public File photoFor(String beachId) {
        String path = prefs.getString(KEY_PREFIX + beachId, null);
        if (path == null) return null;
        File photo = new File(path);
        // The file can be gone without the key being: cleared app storage, or a
        // restored backup carrying preferences that name files it left behind.
        // Forgetting it here drops the beach straight back to its default
        // picture instead of flashing a failed load on the way.
        if (!photo.exists()) {
            prefs.edit().remove(KEY_PREFIX + beachId).apply();
            return null;
        }
        return photo;
    }

    public boolean hasPhoto(String beachId) {
        return photoFor(beachId) != null;
    }

    /**
     * What this beach should be drawn as, ready to hand to Glide: the submitted
     * photo if there is one, the curated photo URL otherwise, and null when
     * there is neither — which is the drawn shoreline's cue.
     */
    @Nullable
    public Object imageSourceFor(Beach beach) {
        File submitted = photoFor(beach.getId());
        if (submitted != null) return submitted;
        String url = beach.getImageUrl();
        return url == null ? null : new GlideUrl(url, remoteHeaders);
    }

    /**
     * Copies the picked image in as this beach's photo, replacing any earlier
     * one, and reports back on the main thread.
     */
    public void submit(String beachId, Uri source, SaveCallback callback) {
        File dir = new File(app.getFilesDir(), PHOTO_DIR);
        importer.importInto(dir, beachId, source, new PhotoImporter.Callback() {
            @Override
            public void onSaved(File photo) {
                // Point the beach at the new file before deleting the old one,
                // so a process death between the two leaves a beach with a photo
                // rather than a preference naming a file that is gone.
                File previous = photoFor(beachId);
                prefs.edit().putString(KEY_PREFIX + beachId, photo.getAbsolutePath()).apply();
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

    /** Drops the submitted photo, so the beach goes back to its default. */
    public void remove(String beachId) {
        File photo = photoFor(beachId);
        prefs.edit().remove(KEY_PREFIX + beachId).apply();
        if (photo != null) {
            photo.delete();
        }
    }
}
