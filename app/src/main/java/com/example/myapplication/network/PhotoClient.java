package com.example.myapplication.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class PhotoClient {
    // Wikimedia requires a descriptive User-Agent identifying the app; requests
    // without one (the default OkHttp UA) get aggressively rate-limited or
    // rejected outright with a plain-text "too many requests" response instead
    // of JSON, which silently looked like "no photo found" for every beach.
    // See: https://meta.wikimedia.org/wiki/User-Agent_policy
    private static final String USER_AGENT = "BeachSafetyUS-Android/1.0 (Congressional App Challenge project)";

    private final OkHttpClient client = new OkHttpClient();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface PhotoCallback {
        void onSuccess(String imageUrl);
        void onFailure(Exception e);
    }

    public void getBeachPhoto(String beachName, PhotoCallback callback) {
        executor.execute(() -> {
            try {
                // Wikipedia API to get page image by title
                String url = String.format(Locale.US,
                    "https://en.wikipedia.org/w/api.php?action=query&titles=%s&prop=pageimages&format=json&pithumbsize=1000&redirects=1",
                    encode(beachName));

                Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        JsonObject json = JsonParser.parseString(response.body().string()).getAsJsonObject();
                        JsonObject query = json.getAsJsonObject("query");
                        if (query != null && query.has("pages")) {
                            JsonObject pages = query.getAsJsonObject("pages");
                            // Wikipedia returns dynamic keys for page IDs
                            String firstKey = pages.keySet().iterator().next();
                            if (!"-1".equals(firstKey)) {
                                JsonObject page = pages.getAsJsonObject(firstKey);
                                if (page.has("thumbnail")) {
                                    String imageUrl = page.getAsJsonObject("thumbnail").get("source").getAsString();
                                    callback.onSuccess(imageUrl);
                                    return;
                                }
                            }
                        }
                    }
                }
                callback.onFailure(new Exception("No image found for " + beachName));

            } catch (IOException e) {
                callback.onFailure(e);
            }
        });
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // UTF-8 is always supported; this is unreachable in practice.
            return value.replace(" ", "%20");
        }
    }
}
