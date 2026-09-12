package com.example.myapplication;

import android.content.Context;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.util.MapTileIndex;

import java.io.File;

/**
 * The imagery under the beach map, and the one-time setup the map library needs
 * before it can fetch any.
 *
 * <p>Both looks are Esri's, as in the design: World Imagery for satellite, and
 * the Light Gray Canvas with its separate label layer for plain. Neither needs a
 * key, which rules out Google's and Mapbox's for the reason
 * {@link com.example.myapplication.network.ParkingClient} gives — anything
 * shipped in the APK can be pulled out of it.
 */
final class MapTiles {

    private static final String ESRI = "https://server.arcgisonline.com/ArcGIS/rest/services/";

    /** The deepest zoom each look goes to, the same two the design stops at. */
    static final int SATELLITE_MAX_ZOOM = 18;
    static final int PLAIN_MAX_ZOOM = 16;

    static final OnlineTileSourceBase SATELLITE =
        new Esri("EsriWorldImagery", "World_Imagery", SATELLITE_MAX_ZOOM, ".jpg");
    static final OnlineTileSourceBase PLAIN =
        new Esri("EsriLightGrayBase", "Canvas/World_Light_Gray_Base", PLAIN_MAX_ZOOM, ".png");
    static final OnlineTileSourceBase PLAIN_LABELS =
        new Esri("EsriLightGrayReference", "Canvas/World_Light_Gray_Reference",
            PLAIN_MAX_ZOOM, ".png");

    /**
     * Tiles are kept in the app's cache folder, which the system may clear, and
     * kept small: the library's default is 600 MB, sized for apps that are
     * mostly map.
     */
    private static final long CACHE_MAX_BYTES = 40L * 1024 * 1024;
    private static final long CACHE_TRIM_BYTES = 30L * 1024 * 1024;

    private static boolean configured;

    private MapTiles() {
    }

    /**
     * Tells the map library who is asking for tiles and where to keep them,
     * before the first map is built. Left to itself it picks a folder on shared
     * storage, which this app has no permission to write to.
     */
    static void configure(Context context) {
        if (configured) return;
        Context app = context.getApplicationContext();
        IConfigurationProvider config = Configuration.getInstance();
        config.load(app, app.getSharedPreferences("osmdroid", Context.MODE_PRIVATE));
        config.setUserAgentValue(app.getPackageName());
        File base = new File(app.getCacheDir(), "osmdroid");
        config.setOsmdroidBasePath(base);
        config.setOsmdroidTileCache(new File(base, "tiles"));
        config.setTileFileSystemCacheMaxBytes(CACHE_MAX_BYTES);
        config.setTileFileSystemCacheTrimBytes(CACHE_TRIM_BYTES);
        configured = true;
    }

    /**
     * The design's treatment of the satellite photograph — CSS
     * {@code saturate(.8) brightness(.82) contrast(1.06)} — as one colour
     * matrix, in the same order. It knocks the imagery back far enough for the
     * markers and controls on top of it to carry.
     */
    static ColorMatrixColorFilter satelliteFilter() {
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(0.8f);
        matrix.postConcat(brightness(0.82f));
        matrix.postConcat(contrast(1.06f));
        return new ColorMatrixColorFilter(matrix);
    }

    /** The plain map's street and place names: CSS {@code contrast(1.35) saturate(0)}. */
    static ColorMatrixColorFilter labelFilter() {
        ColorMatrix matrix = contrast(1.35f);
        ColorMatrix grey = new ColorMatrix();
        grey.setSaturation(0f);
        matrix.postConcat(grey);
        return new ColorMatrixColorFilter(matrix);
    }

    private static ColorMatrix brightness(float amount) {
        ColorMatrix matrix = new ColorMatrix();
        matrix.setScale(amount, amount, amount, 1f);
        return matrix;
    }

    /** CSS contrast: every channel pushed away from mid-grey by the factor. */
    private static ColorMatrix contrast(float amount) {
        float offset = 127.5f * (1f - amount);
        return new ColorMatrix(new float[] {
            amount, 0, 0, 0, offset,
            0, amount, 0, 0, offset,
            0, 0, amount, 0, offset,
            0, 0, 0, 1, 0,
        });
    }

    /** Esri's tile addresses put the row before the column: …/tile/{z}/{y}/{x}. */
    private static final class Esri extends OnlineTileSourceBase {
        Esri(String name, String service, int maxZoom, String extension) {
            super(name, 0, maxZoom, 256, extension,
                new String[] {ESRI + service + "/MapServer/tile/"});
        }

        @Override
        public String getTileURLString(long index) {
            return getBaseUrl() + MapTileIndex.getZoom(index)
                + "/" + MapTileIndex.getY(index) + "/" + MapTileIndex.getX(index);
        }
    }
}
