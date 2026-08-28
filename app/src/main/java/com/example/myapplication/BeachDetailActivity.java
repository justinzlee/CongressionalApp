package com.example.myapplication;

import android.content.res.ColorStateList;
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
import com.example.myapplication.network.PhotoClient;
import com.example.myapplication.network.WeatherClient;
import com.example.myapplication.util.EdgeToEdge;
import com.example.myapplication.util.UnitConverter;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
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
            EdgeToEdge.applyTopInset(toolbar);
        }
        EdgeToEdge.applyBottomInset(findViewById(R.id.detailScrollView));

        ImageView image = findViewById(R.id.beachDetailImage);
        Glide.with(this).load(beach.getImageUrl()).into(image);

        // Swap in a real photo of this specific beach once it resolves; keep
        // the stock-photo fallback above if none is found.
        new PhotoClient().getBeachPhoto(beach.getName(), new PhotoClient.PhotoCallback() {
            @Override
            public void onSuccess(String imageUrl) {
                runOnUiThread(() -> Glide.with(BeachDetailActivity.this).load(imageUrl).into(image));
            }

            @Override
            public void onFailure(Exception e) {
            }
        });

        TextView airTemp = findViewById(R.id.detailAirTemp);
        TextView waveHeight = findViewById(R.id.detailWaveHeight);
        TextView uvIndex = findViewById(R.id.detailUvIndex);
        TextView visibility = findViewById(R.id.detailVisibility);
        TextView windSpeed = findViewById(R.id.detailWindSpeed);
        TextView hazards = findViewById(R.id.detailHazards);
        TextView advisorSummary = findViewById(R.id.advisorSummary);
        MaterialCardView hazardsCard = findViewById(R.id.hazardsCard);

        if (beach.getConditions() != null) {
            updateConditionsDisplay(beach.getConditions(), airTemp, waveHeight, uvIndex, visibility, windSpeed);
        }

        boolean hasHazards = !beach.getHazards().isEmpty();
        int hazardColor = ContextCompat.getColor(this, hasHazards ? R.color.error : R.color.safe_green);
        hazardsCard.setStrokeColor(ColorStateList.valueOf(hazardColor));
        if (hasHazards) {
            hazards.setTextColor(hazardColor);
            hazards.setText(beach.getHazards().stream()
                    .map(h -> h.getType().name().replace("_", " ") + ": " + h.getDescription())
                    .collect(Collectors.joining("\n\n")));
        } else {
            hazards.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
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
                color = ContextCompat.getColor(this, R.color.icon_wind);
            } else if (temp < 85) {
                iconRes = R.drawable.ic_thermometer_warm;
                color = ContextCompat.getColor(this, R.color.icon_temp);
            } else {
                iconRes = R.drawable.ic_thermometer_hot;
                color = ContextCompat.getColor(this, R.color.error);
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
