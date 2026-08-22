package com.example.myapplication.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class PhotoClient {
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
                    beachName.replace(" ", "%20"));

                Request request = new Request.Builder().url(url).build();

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
}
