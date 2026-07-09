package com.example.myapplication.network;

import com.example.myapplication.model.BeachConditions;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class WeatherClient {
    private final OkHttpClient client = new OkHttpClient();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface WeatherCallback {
        void onSuccess(BeachConditions conditions);
        void onFailure(Exception e);
    }

    public void getBeachConditions(double lat, double lon, WeatherCallback callback) {
        executor.execute(() -> {
            try {
                // Weather API: Temp (F), Wind (mph), Visibility (meters -> miles), UV Index
                String weatherUrl = String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,wind_speed_10m,visibility,uv_index&temperature_unit=fahrenheit&wind_speed_unit=mph",
                    lat, lon);
                
                // Marine API: Wave Height (meters -> feet)
                String marineUrl = String.format(Locale.US,
                    "https://marine-api.open-meteo.com/v1/marine?latitude=%.4f&longitude=%.4f&current=wave_height",
                    lat, lon);

                Request weatherRequest = new Request.Builder().url(weatherUrl).build();
                Request marineRequest = new Request.Builder().url(marineUrl).build();

                double temp = 0, wind = 0, visibility = 0, uv = 0, waveHeight = 0;

                try (Response response = client.newCall(weatherRequest).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        JsonObject json = JsonParser.parseString(response.body().string()).getAsJsonObject();
                        JsonObject current = json.getAsJsonObject("current");
                        temp = current.get("temperature_2m").getAsDouble();
                        wind = current.get("wind_speed_10m").getAsDouble();
                        // Visibility in meters -> convert to miles
                        visibility = current.get("visibility").getAsDouble() * 0.000621371;
                        uv = current.get("uv_index").getAsDouble();
                    }
                }

                try (Response response = client.newCall(marineRequest).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        JsonObject json = JsonParser.parseString(response.body().string()).getAsJsonObject();
                        JsonObject current = json.getAsJsonObject("current");
                        if (current.has("wave_height") && !current.get("wave_height").isJsonNull()) {
                            // meters to feet
                            waveHeight = current.get("wave_height").getAsDouble() * 3.28084;
                        }
                    }
                }

                BeachConditions conditions = new BeachConditions(temp, (int)uv, wind, waveHeight, visibility);
                callback.onSuccess(conditions);

            } catch (IOException e) {
                callback.onFailure(e);
            }
        });
    }
}
