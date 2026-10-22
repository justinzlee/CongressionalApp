package com.example.myapplication.network;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;

/**
 * The single HTTP client and worker pool the network clients share.
 *
 * <p>Each client used to hold its own {@link OkHttpClient} and its own
 * single-thread executor. That cost twice over. OkHttp carries a connection
 * pool and its own dispatcher threads per instance, so separate clients meant
 * no connection reuse between the weather, tide and photo calls that all go to
 * the same two hosts. And a single thread per client meant the repository's
 * beaches were fetched strictly one after another — each weather fetch being
 * two sequential round trips, that is twice the beach count in a row before
 * the last one has any data.
 *
 * <p>{@link #WORKERS} is sized for the fan-out that remains. It no longer has
 * to scale with the beach list: {@code WeatherClient} asks Open-Meteo for a
 * batch of coordinates at a time, so a refresh of the whole coastline is a
 * handful of tasks rather than one per beach. That leaves this pool wide
 * enough to run those batches alongside the tide call a detail screen makes,
 * while staying polite to a free public API. The threads are daemons so a pool
 * sitting idle can never hold the process open.
 */
final class Http {

    private static final int WORKERS = 6;

    static final OkHttpClient CLIENT = new OkHttpClient();

    /**
     * Sent to the public services that ask apps to say who they are, so a
     * misbehaving one can be contacted rather than blocked: iNaturalist, eBird
     * and Overpass, which refuses OkHttp's default outright.
     */
    static final String USER_AGENT = "Flagline-Android/1.0 (supportflagline@gmail.com)";

    static final ExecutorService IO = Executors.newFixedThreadPool(WORKERS, new ThreadFactory() {
        private final AtomicInteger count = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "beach-io-" + count.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    });

    private Http() {
    }
}
