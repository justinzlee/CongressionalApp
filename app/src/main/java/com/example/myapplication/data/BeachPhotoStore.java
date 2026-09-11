package com.example.myapplication.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import androidx.exifinterface.media.ExifInterface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.Headers;
import com.bumptech.glide.load.model.LazyHeaders;
import com.example.myapplication.SettingsActivity;
import com.example.myapplication.model.Beach;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
 * <p>Incoming images are re-encoded rather than copied byte for byte. A phone
 * camera writes a twelve-megapixel JPEG, which is two orders of magnitude more
 * picture than a 168dp hero can show, and a gallery Uri may not outlive the
 * photo being deleted from the library or the permission grant expiring.
 * Decoding down to {@link #MAX_EDGE} and keeping our own copy fixes both, and
 * drops the EXIF block on the way — which is why the orientation tag has to be
 * read and baked into the pixels here, or every photo held in portrait would
 * come back on its side.
 */
public class BeachPhotoStore {

    /** One preference key per beach, holding the absolute path of its photo. */
    private static final String KEY_PREFIX = "beach_photo_";

    /** Where the re-encoded copies live, under the app's private files dir. */
    private static final String PHOTO_DIR = "beach_photos";

    /**
     * The longest edge kept, in pixels. The largest a photo is ever drawn is
     * the detail hero, the full width of the screen; 1600px covers that on the
     * densest phones with room to spare, and keeps each file to a few hundred
     * kilobytes rather than several megabytes.
     */
    private static final int MAX_EDGE = 1600;

    private static final int JPEG_QUALITY = 88;

    /**
     * Decoding and re-encoding a full-size photo takes long enough to drop
     * frames, so it happens off the main thread. One thread is enough: this
     * runs once, when someone picks a picture.
     */
    private static final ExecutorService IMPORT = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "beach-photo-import");
        thread.setDaemon(true);
        return thread;
    });

    /** Delivered on the main thread once a submitted photo is stored, or is not. */
    public interface SaveCallback {
        void onSaved(File photo);
        void onFailed(Exception e);
    }

    private final Context app;
    private final SharedPreferences prefs;

    /**
     * Per instance rather than static, so that loading this class does not by
     * itself reach for the main looper. The scaling arithmetic below is covered
     * by plain JVM tests, where the framework is only a stub that throws, and a
     * static handler would take the whole class down with it on first touch.
     */
    private final Handler main;

    /**
     * Who is asking, sent with every curated photo. Wikimedia throttles
     * requests that carry a generic client string, which is what Glide sends
     * unless told otherwise ("Dalvik/2.1.0 ..."): in a quick run through the
     * curated photos a third came back 429 Too Many Requests and those rows
     * fell back to the drawing, where the same run naming the app loaded every
     * one. See https://meta.wikimedia.org/wiki/User-Agent_policy
     *
     * <p>Built per instance for the same reason as {@link #main}: Glide's
     * header builder reads the platform the first time it is touched.
     */
    private final Headers remoteHeaders;

    public BeachPhotoStore(Context context) {
        this.app = context.getApplicationContext();
        this.prefs = app.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE);
        this.main = new Handler(Looper.getMainLooper());
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
        IMPORT.execute(() -> {
            try {
                File saved = importPhoto(beachId, source);
                main.post(() -> callback.onSaved(saved));
            } catch (Exception e) {
                // Broad by design: BitmapFactory and the content resolver throw
                // unchecked on a file that is not an image at all, and the
                // picker hands over whatever the user chose.
                main.post(() -> callback.onFailed(e));
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

    private File importPhoto(String beachId, Uri source) throws IOException {
        Bitmap bitmap = decodeUpright(source);
        if (bitmap == null) {
            throw new IOException("Not an image this device can read: " + source);
        }

        File dir = new File(app.getFilesDir(), PHOTO_DIR);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Could not create " + dir);
        }

        // A fresh filename each time rather than one per beach. Glide caches by
        // the model it was handed, so writing a replacement over the same path
        // would go on showing the old picture until that cache turned over.
        File photo = new File(dir, String.format(Locale.US, "%s-%d.jpg",
            fileSafe(beachId), System.currentTimeMillis()));

        try (OutputStream out = new FileOutputStream(photo)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                throw new IOException("Could not encode the photo");
            }
        } catch (IOException e) {
            photo.delete();
            throw e;
        } finally {
            bitmap.recycle();
        }

        File previous = photoFor(beachId);
        prefs.edit().putString(KEY_PREFIX + beachId, photo.getAbsolutePath()).apply();
        if (previous != null) {
            previous.delete();
        }
        return photo;
    }

    /**
     * The picked image, scaled down and turned the right way up.
     *
     * <p>Read three times over: once for its dimensions alone, once for the
     * orientation tag, and once for the pixels. That is cheaper than it looks —
     * the first two passes read only the header — and it is the only way to
     * choose a sample size before committing to an allocation, which is what
     * keeps a fifty-megapixel photo from being decoded at full size and taking
     * the process down with it.
     */
    @Nullable
    private Bitmap decodeUpright(Uri source) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream in = open(source)) {
            BitmapFactory.decodeStream(in, null, options);
        }

        int degrees = rotationFor(orientationOf(source));

        options.inSampleSize = sampleSizeFor(options.outWidth, options.outHeight, MAX_EDGE);
        options.inJustDecodeBounds = false;
        Bitmap decoded;
        try (InputStream in = open(source)) {
            decoded = BitmapFactory.decodeStream(in, null, options);
        }
        if (decoded == null || degrees == 0) return decoded;

        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        Bitmap upright = Bitmap.createBitmap(
            decoded, 0, 0, decoded.getWidth(), decoded.getHeight(), matrix, true);
        if (upright != decoded) {
            decoded.recycle();
        }
        return upright;
    }

    private InputStream open(Uri source) throws IOException {
        InputStream in = app.getContentResolver().openInputStream(source);
        if (in == null) throw new IOException("Could not open " + source);
        return in;
    }

    private int orientationOf(Uri source) {
        try (InputStream in = open(source)) {
            return new ExifInterface(in)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (Exception e) {
            // No EXIF block, or one this device cannot parse. Assume upright: a
            // photo shown the way it was stored is a better failure than no
            // photo at all.
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    /**
     * The power-of-two shrink that brings the longest edge down to
     * {@code maxEdge}, which is the only kind {@code BitmapFactory} honours.
     *
     * <p>Deliberately never leaves the result larger than {@code maxEdge}:
     * stopping a step earlier would keep more detail but decode an image up to
     * twice as wide and twice as tall — four times the memory — for a picture
     * that is about to be centre-cropped into a card.
     */
    @VisibleForTesting
    static int sampleSizeFor(int width, int height, int maxEdge) {
        int longest = Math.max(width, height);
        int sample = 1;
        while (longest / sample > maxEdge) {
            sample *= 2;
        }
        return sample;
    }

    /**
     * Degrees clockwise the decoded pixels must be turned to match the EXIF
     * orientation tag. The mirrored orientations are treated as upright: they
     * are vanishingly rare off a camera, and flipping a beach photo that did
     * not need it is more visible than leaving one that did.
     */
    @VisibleForTesting
    static int rotationFor(int exifOrientation) {
        switch (exifOrientation) {
            case ExifInterface.ORIENTATION_ROTATE_90: return 90;
            case ExifInterface.ORIENTATION_ROTATE_180: return 180;
            case ExifInterface.ORIENTATION_ROTATE_270: return 270;
            default: return 0;
        }
    }

    /** Beach ids are short codes like "CA1", but a filename must not trust that. */
    private static String fileSafe(String beachId) {
        return beachId == null ? "beach" : beachId.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
