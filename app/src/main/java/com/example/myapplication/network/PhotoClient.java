package com.example.myapplication.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;
import okhttp3.Request;
import okhttp3.Response;

public class PhotoClient {
    // Wikimedia requires a descriptive User-Agent identifying the app; requests
    // without one (the default OkHttp UA) get aggressively rate-limited or
    // rejected outright with a plain-text "too many requests" response instead
    // of JSON, which silently looked like "no photo found" for every beach.
    // See: https://meta.wikimedia.org/wiki/User-Agent_policy
    private static final String USER_AGENT = "BeachSafetyUS-Android/1.0 (Congressional App Challenge project)";

    public interface PhotoCallback {
        void onSuccess(String imageUrl);
        void onFailure(Exception e);
    }

    public void getBeachPhoto(String beachName, PhotoCallback callback) {
        Http.IO.execute(() -> {
            try {
                // Wikipedia API to get page image by title
                String url = String.format(Locale.US,
                    "https://en.wikipedia.org/w/api.php?action=query&titles=%s&prop=pageimages&format=json&pithumbsize=1000&redirects=1",
                    encode(beachName));

                Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .build();

                try (Response response = Http.CLIENT.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        JsonElement parsed = JsonParser.parseString(response.body().string());
                        JsonObject query = parsed.isJsonObject()
                            ? parsed.getAsJsonObject().getAsJsonObject("query") : null;
                        if (query != null && query.has("pages")) {
                            JsonObject pages = query.getAsJsonObject("pages");
                            // Wikipedia returns dynamic keys for page IDs, and
                            // an empty object when it matched nothing at all.
                            if (!pages.keySet().isEmpty()) {
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
                }
                callback.onFailure(new IOException("No image found for " + beachName));

            } catch (Exception e) {
                // Broad by design: Gson throws unchecked on a malformed or
                // unexpected payload, and Wikimedia serves plain text rather
                // than JSON when it rate-limits. An escaped exception would
                // fire neither callback and leave the hero on its placeholder.
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
