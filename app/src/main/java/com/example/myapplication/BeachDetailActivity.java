package com.example.myapplication;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.example.myapplication.data.BeachRepository;
import com.example.myapplication.model.Beach;
import com.google.android.material.appbar.MaterialToolbar;
import java.util.stream.Collectors;

public class BeachDetailActivity extends AppCompatActivity {
    public static final String EXTRA_BEACH_ID = "extra_beach_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_beach_detail);

        String beachId = getIntent().getStringExtra(EXTRA_BEACH_ID);
        BeachRepository repository = new BeachRepository();
        Beach beach = repository.getBeaches().stream()
                .filter(b -> b.getId().equals(beachId))
                .findFirst()
                .orElse(null);

        if (beach == null) {
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.detailToolbar);
        toolbar.setTitle(beach.getName());
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        ImageView image = findViewById(R.id.beachDetailImage);
        Glide.with(this).load(beach.getImageUrl()).into(image);

        TextView waterTemp = findViewById(R.id.detailWaterTemp);
        TextView airTemp = findViewById(R.id.detailAirTemp);
        TextView waveHeight = findViewById(R.id.detailWaveHeight);
        TextView uvIndex = findViewById(R.id.detailUvIndex);
        TextView hazards = findViewById(R.id.detailHazards);

        if (beach.getConditions() != null) {
            waterTemp.setText(beach.getConditions().getWaterTemperature() != null ? beach.getConditions().getWaterTemperature() + "°C" : "--");
            airTemp.setText(beach.getConditions().getAirTemperature() != null ? beach.getConditions().getAirTemperature() + "°C" : "--");
            waveHeight.setText(beach.getConditions().getWaveHeight() != null ? beach.getConditions().getWaveHeight() + "m" : "--");
            uvIndex.setText(beach.getConditions().getUvIndex() != null ? beach.getConditions().getUvIndex().toString() : "--");
        }

        if (!beach.getHazards().isEmpty()) {
            hazards.setText(beach.getHazards().stream()
                    .map(h -> h.getType().name() + ": " + h.getDescription())
                    .collect(Collectors.joining("\n\n")));
        } else {
            hazards.setText("No major hazards reported.");
        }
    }
}
