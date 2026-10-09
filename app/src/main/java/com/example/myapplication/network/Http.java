package com.example.myapplication.network;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

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

    static final ExecutorService IO = Executors.newFixedThreadPool(WORKERS, new ThreadFactory() {
        private final AtomicInteger count = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "beach-io-" + count.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    });

    /**
     * How many Open-Meteo calls may be in flight at once, across the weather,
     * air quality and rainfall clients together.
     *
     * <p>A refresh of the whole list hands one batch to each worker above, so
     * six forecast calls used to leave at the same moment, and Open-Meteo
     * turns some of those away with 429 "Too many concurrent requests". The
     * batch turned away came back empty and its twenty-five beaches sat on
     * "Checking conditions" until the repository's retry, twenty seconds or
     * more later. Two at a time still answers a batch in about a second.
     */
    private static final Semaphore OPEN_METEO_SLOTS = new Semaphore(2, true);

    /** Open-Meteo's answer when it is busy, and how hard to keep asking. */
    private static final int TOO_MANY_REQUESTS = 429;
    private static final int BUSY_RETRIES = 2;
    private static final long BUSY_PAUSE_MS = 1000L;

    /**
     * Runs a call to one of Open-Meteo's hosts: at most
     * {@link #OPEN_METEO_SLOTS} at a time, and asked again after a short pause
     * when the service answers that it is busy. The caller closes the response,
     * as with {@code CLIENT.newCall(request).execute()}.
     */
    static Response openMeteo(Request request) throws IOException {
        for (int attempt = 0; ; attempt++) {
            Response response;
            OPEN_METEO_SLOTS.acquireUninterruptibly();
            try {
                response = CLIENT.newCall(request).execute();
            } finally {
                OPEN_METEO_SLOTS.release();
            }
            if (response.code() != TOO_MANY_REQUESTS || attempt >= BUSY_RETRIES) {
                return response;
            }
            response.close();
            try {
                Thread.sleep(BUSY_PAUSE_MS * (attempt + 1));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted waiting to retry Open-Meteo", e);
            }
        }
    }

    private Http() {
    }
}
