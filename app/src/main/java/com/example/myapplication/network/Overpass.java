package com.example.myapplication.network;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * OpenStreetMap's query service, as {@link ParkingClient} and
 * {@link LifeguardClient} both use it.
 *
 * <p>overpass-api.de is the main public instance and is asked first, but it is
 * a free service that turns work away when busy and stops answering an address
 * it decides is asking too much. From the machine this was written on it went,
 * inside one afternoon, from answering to "406 Not Acceptable" to refusing to
 * connect at all — while the public instance below it returned the same towers
 * for the same query. So the second is asked whenever the first does not
 * answer. (overpass.kumi.systems, often listed as a third, is at present the
 * same machine as the second under another name, so trying it after the second
 * has failed would only double the wait.)
 */
final class Overpass {

    private static final String[] ENDPOINTS = {
        "https://overpass-api.de/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
    };

    /**
     * Quick to give up on connecting, slow to give up on an answer. A server
     * that is up connects in well under a second, and the main one, when it
     * refuses, does so by never connecting — on each of its two addresses in
     * turn, so the shared client's ten seconds made that a twenty-second wait
     * before the next server was tried. But once connected, Overpass sends
     * nothing until its whole answer is ready, and a busy instance took up to
     * half a minute. Built from the shared client so it keeps the same
     * connection pool and threads.
     */
    private static final OkHttpClient CLIENT = Http.CLIENT.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build();

    private Overpass() {
    }

    /**
     * Runs one query and returns the body of the first instance to answer it,
     * or null if none did. Blocks, so it belongs on {@link Http#IO}.
     */
    @Nullable
    static String fetch(String query) {
        for (String endpoint : ENDPOINTS) {
            try {
                String body = post(endpoint, query);
                if (body != null) return body;
            } catch (IOException e) {
                // Down, slow or refusing us: the next one may not be.
            }
        }
        return null;
    }

    @Nullable
    private static String post(String endpoint, String query) throws IOException {
        Request request = new Request.Builder()
            .url(endpoint)
            .post(RequestBody.create(query.getBytes(StandardCharsets.UTF_8)))
            .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return null;
            return response.body().string();
        }
    }
}
