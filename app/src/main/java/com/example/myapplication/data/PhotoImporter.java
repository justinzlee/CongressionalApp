package com.example.myapplication.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Takes an image a user picked or photographed and puts a copy of it in the
 * app's own private storage, scaled down and the right way up.
 *
 * <p>Three features now point a camera at a beach — the photograph of the beach
 * itself, the picture backing a rock rating, and the picture attached to a
 * wildlife sighting — and every one of them faces the same four problems, so
 * they are solved once here rather than three times over.
 *
 * <p>Incoming images are re-encoded rather than copied byte for byte. A phone
 * camera writes a twelve-megapixel JPEG, which is two orders of magnitude more
 * picture than any view in this app can show, and a gallery Uri may not outlive
 * the photo being deleted from the library or the permission grant expiring.
 * Decoding down to {@link #MAX_EDGE} and keeping our own copy fixes both, and
 * drops the EXIF block on the way — which is why the orientation tag has to be
 * read and baked into the pixels here, or every photo held in portrait would
 * come back on its side.
 *
 * <p>Nothing here uploads anything. There is no server behind this app; every
 * copy this class writes stays in the private files directory of the phone it
 * was taken on.
 */
public final class PhotoImporter {

    /**
     * The longest edge kept, in pixels. The largest a photo is ever drawn is the
     * beach detail hero, the full width of the screen; 1600px covers that on the
     * densest phones with room to spare, and keeps each file to a few hundred
     * kilobytes rather than several megabytes.
     */
    @VisibleForTesting
    static final int MAX_EDGE = 1600;

    private static final int JPEG_QUALITY = 88;

    /**
     * Decoding and re-encoding a full-size photo takes long enough to drop
     * frames, so it happens off the main thread. One thread is enough, and is
     * also what keeps two imports started in quick succession from each holding
     * a full-size bitmap at the same time.
     */
    private static final ExecutorService IMPORT = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "photo-import");
        thread.setDaemon(true);
        return thread;
    });

    /** Delivered on the main thread once a submitted photo is stored, or is not. */
    public interface Callback {
        void onSaved(File photo);
        void onFailed(Exception e);
    }

    private final Context app;

    /**
     * Per instance rather than static, so that loading this class does not by
     * itself reach for the main looper. The scaling arithmetic below is covered
     * by plain JVM tests, where the framework is only a stub that throws, and a
     * static handler would take the whole class down with it on first touch.
     */
    private final Handler main;

    public PhotoImporter(Context context) {
        this.app = context.getApplicationContext();
        this.main = new Handler(Looper.getMainLooper());
    }

    /**
     * Copies the image at {@code source} into {@code dir}, reporting back on the
     * main thread.
     *
     * @param dir        created if it is not there yet.
     * @param namePrefix leads the filename, so files on disk can be told apart
     *                   by what they belong to. Sanitised here — a beach id is a
     *                   short code like "CA1", but a filename must not trust
     *                   that.
     */
    public void importInto(File dir, String namePrefix, Uri source, Callback callback) {
        IMPORT.execute(() -> {
            try {
                File saved = writeScaledCopy(dir, namePrefix, source);
                main.post(() -> callback.onSaved(saved));
            } catch (Exception e) {
                // Broad by design: BitmapFactory and the content resolver throw
                // unchecked on a file that is not an image at all, and the
                // picker hands over whatever the user chose.
                main.post(() -> callback.onFailed(e));
            }
        });
    }

    private File writeScaledCopy(File dir, String namePrefix, Uri source) throws IOException {
        Bitmap bitmap = decodeUpright(source);
        if (bitmap == null) {
            throw new IOException("Not an image this device can read: " + source);
        }

        if (!dir.isDirectory() && !dir.mkdirs()) {
            bitmap.recycle();
            throw new IOException("Could not create " + dir);
        }

        // A fresh filename each time rather than one per subject. Glide caches by
        // the model it was handed, so writing a replacement over the same path
        // would go on showing the old picture until that cache turned over.
        File photo = new File(dir, String.format(Locale.US, "%s-%d.jpg",
            fileSafe(namePrefix), System.currentTimeMillis()));

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

    /** Ids are short codes like "CA1", but a filename must not trust that. */
    @VisibleForTesting
    static String fileSafe(String raw) {
        return raw == null ? "photo" : raw.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
