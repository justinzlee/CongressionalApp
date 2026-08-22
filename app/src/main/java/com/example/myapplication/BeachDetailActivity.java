package com.example.myapplication;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import com.bumptech.glide.Glide;
import com.example.myapplication.ai.SafetyAdvisor;
import com.example.myapplication.data.BeachRepository;
import com.example.myapplication.model.Beach;
import com.example.myapplication.network.WeatherClient;
import com.example.myapplication.util.UnitConverter;
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
            updateConditionsDisplay(beach.getConditions(), airTemp, waveHeight, uvIndex, visibility, windSpeed);
        }

        if (!beach.getHazards().isEmpty()) {
            hazards.setTextColor(ContextCompat.getColor(this, R.color.error));
            hazards.setText(beach.getHazards().stream()
                    .map(h -> h.getType().name().replace("_", " ") + ": " + h.getDescription())
                    .collect(Collectors.joining("\n\n")));
        } else {
            hazards.setTextColor(ContextCompat.getColor(this, R.color.safe_green));
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
                    updateConditionsDisplay(conditions, airTemp, waveHeight, uvIndex, visibility, windSpeed);

                    // Update Safety Summary with real-time data
                    SafetyAdvisor advisor = new SafetyAdvisor();
                    // We need a beach object with the updated conditions for the summary
                    Beach updatedBeach = new Beach(beach.getId(), beach.getName(), beach.getLatitude(), 
                        beach.getLongitude(), beach.getHazards(), conditions, beach.getNearestHelp(), beach.getImageUrl());
                    advisorSummary.setText(advisor.getBeachSummary(BeachDetailActivity.this, updatedBeach));
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

    private void updateConditionsDisplay(com.example.myapplication.model.BeachConditions conditions, 
                                        TextView airTemp, TextView waveHeight, TextView uvIndex, 
                                        TextView visibility, TextView windSpeed) {
        if (conditions.getAirTemperature() != null) {
            airTemp.setText(UnitConverter.formatTemperature(this, conditions.getAirTemperature()));
            
            // Dynamic Thermometer
            int iconRes;
            int color;
            double temp = conditions.getAirTemperature();
            if (temp < 60) {
                iconRes = R.drawable.ic_thermometer_cold;
                color = android.graphics.Color.parseColor("#03A9F4"); // Light Blue
            } else if (temp < 85) {
                iconRes = R.drawable.ic_thermometer_warm;
                color = android.graphics.Color.parseColor("#F57C00"); // Orange
            } else {
                iconRes = R.drawable.ic_thermometer_hot;
                color = android.graphics.Color.parseColor("#D32F2F"); // Red
            }
            
            airTemp.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0);
            TextViewCompat.setCompoundDrawableTintList(airTemp, android.content.res.ColorStateList.valueOf(color));
        } else {
            airTemp.setText("--");
        }

        waveHeight.setText(UnitConverter.formatWaveHeight(this, conditions.getWaveHeight()));
        
        uvIndex.setText(conditions.getFormattedUvIndex());
        
        visibility.setText(UnitConverter.formatVisibility(this, conditions.getVisibility()));
        
        windSpeed.setText(UnitConverter.formatWindSpeed(this, conditions.getWindSpeed()));
    }
}
