package com.example.myapplication;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.example.myapplication.ai.SafetyAdvisor;
import com.example.myapplication.data.BeachRepository;
import com.example.myapplication.model.Beach;
import com.example.myapplication.network.WeatherClient;
import com.google.android.material.appbar.MaterialToolbar;
import java.util.Objects;
import java.util.stream.Collectors;

public class BeachDetailActivity extends AppCompatActivity {
    public static final String EXTRA_BEACH_ID = "extra_beach_id";
    private WeatherClient weatherClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_beach_detail);

        String beachId = getIntent().getStringExtra(EXTRA_BEACH_ID);
        BeachRepository repository = new BeachRepository();
        Beach beach = repository.getBeaches().stream()
                .filter(b -> Objects.equals(b.getId(), beachId))
                .findFirst()
                .orElse(null);

        if (beach == null) {
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.detailToolbar);
        if (toolbar != null) {
            toolbar.setTitle(beach.getName());
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
            toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        }

        ImageView image = findViewById(R.id.beachDetailImage);
        Glide.with(this).load(beach.getImageUrl()).into(image);

        TextView airTemp = findViewById(R.id.detailAirTemp);
        TextView waveHeight = findViewById(R.id.detailWaveHeight);
        TextView uvIndex = findViewById(R.id.detailUvIndex);
        TextView visibility = findViewById(R.id.detailVisibility);
        TextView windSpeed = findViewById(R.id.detailWindSpeed);
        TextView hazards = findViewById(R.id.detailHazards);
        TextView advisorSummary = findViewById(R.id.advisorSummary);

        if (beach.getConditions() != null) {
            airTemp.setText(beach.getConditions().getAirTemperature() != null ? beach.getConditions().getAirTemperature() + "°F" : "--");
            waveHeight.setText(beach.getConditions().getWaveHeight() != null ? beach.getConditions().getWaveHeight() + " ft" : "--");
            uvIndex.setText(beach.getConditions().getUvIndex() != null ? beach.getConditions().getUvIndex().toString() : "--");
            visibility.setText(beach.getConditions().getVisibility() != null ? beach.getConditions().getVisibility() + " mi" : "--");
            windSpeed.setText(beach.getConditions().getWindSpeed() != null ? beach.getConditions().getWindSpeed() + " mph" : "--");
        }

        if (!beach.getHazards().isEmpty()) {
            hazards.setText(beach.getHazards().stream()
                    .map(h -> h.getType().name().replace("_", " ") + ": " + h.getDescription())
                    .collect(Collectors.joining("\n\n")));
        } else {
            hazards.setText(getString(R.string.no_major_hazards));
        }

        weatherClient = new WeatherClient();
        fetchRealTimeData(beach, airTemp, waveHeight, uvIndex, visibility, windSpeed, advisorSummary);
    }

    private void fetchRealTimeData(Beach beach, TextView airTemp, TextView waveHeight, TextView uvIndex, 
                                   TextView visibility, TextView windSpeed, TextView advisorSummary) {
        weatherClient.getBeachConditions(beach.getLatitude(), beach.getLongitude(), new WeatherClient.WeatherCallback() {
            @Override
            public void onSuccess(com.example.myapplication.model.BeachConditions conditions) {
                runOnUiThread(() -> {
                    airTemp.setText(String.format(java.util.Locale.US, "%.1f°F", conditions.getAirTemperature()));
                    waveHeight.setText(String.format(java.util.Locale.US, "%.1f ft", conditions.getWaveHeight()));
                    uvIndex.setText(String.valueOf(conditions.getUvIndex()));
                    visibility.setText(String.format(java.util.Locale.US, "%.1f mi", conditions.getVisibility()));
                    windSpeed.setText(String.format(java.util.Locale.US, "%.1f mph", conditions.getWindSpeed()));

                    // Update Safety Summary with real-time data
                    SafetyAdvisor advisor = new SafetyAdvisor();
                    // We need a beach object with the updated conditions for the summary
                    Beach updatedBeach = new Beach(beach.getId(), beach.getName(), beach.getLatitude(), 
                        beach.getLongitude(), beach.getHazards(), conditions, beach.getNearestHelp(), beach.getImageUrl());
                    advisorSummary.setText(advisor.getBeachSummary(updatedBeach));
                });
            }

            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> {
                    // Fallback to static data if available
                    if (beach.getConditions() != null) {
                        // ... already set in onCreate
                    }
                });
            }
        });
    }
}
