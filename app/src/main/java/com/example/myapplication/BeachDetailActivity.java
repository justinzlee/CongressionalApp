package com.example.myapplication;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import com.bumptech.glide.Glide;
import com.example.myapplication.ai.SafetyNote;
import com.example.myapplication.data.BeachRepository;
import com.example.myapplication.data.SavedBeachStore;
import com.google.android.material.button.MaterialButton;
import com.example.myapplication.model.Beach;
import com.example.myapplication.model.BeachConditions;
import com.example.myapplication.model.Hazard;
import com.example.myapplication.model.HelpLocation;
import com.example.myapplication.model.HelpType;
import com.example.myapplication.model.SafetyLevel;
import com.example.myapplication.model.Severity;
import com.example.myapplication.network.PhotoClient;
import com.example.myapplication.network.WeatherClient;
import com.example.myapplication.util.EdgeToEdge;
import com.example.myapplication.util.UnitConverter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class BeachDetailActivity extends AppCompatActivity {
    public static final String EXTRA_BEACH_ID = "extra_beach_id";
    private WeatherClient weatherClient;

    private Beach beach;
    private ViewGroup hazardList;
    private ImageView verdictIcon;
    private TextView verdictText, advisorSummary;
    private View verdictChip;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_beach_detail);

        String beachId = getIntent().getStringExtra(EXTRA_BEACH_ID);
        BeachRepository repository = new BeachRepository();
        beach = repository.getBeaches().stream()
                .filter(b -> Objects.equals(b.getId(), beachId))
                .findFirst()
                .orElse(null);

        if (beach == null) {
            finish();
            return;
        }

        EdgeToEdge.applyTopInset(findViewById(R.id.detailHeader));
        EdgeToEdge.applyBottomInset(findViewById(R.id.detailScrollView));

        findViewById(R.id.detailBackButton).setOnClickListener(
            v -> getOnBackPressedDispatcher().onBackPressed());

        hazardList = findViewById(R.id.hazardList);
        verdictChip = findViewById(R.id.detailVerdictChip);
        verdictIcon = findViewById(R.id.detailVerdictIcon);
        verdictText = findViewById(R.id.detailVerdictText);
        advisorSummary = findViewById(R.id.advisorSummary);

        ImageView image = findViewById(R.id.beachDetailImage);
        Glide.with(this).load(beach.getImageUrl()).centerCrop().into(image);

        // Swap in a real photo of this specific beach once it resolves; keep
        // the stock-photo fallback above if none is found.
        new PhotoClient().getBeachPhoto(beach.getName(), new PhotoClient.PhotoCallback() {
            @Override
            public void onSuccess(String imageUrl) {
                runOnUiThread(() -> Glide.with(BeachDetailActivity.this)
                    .load(imageUrl).centerCrop().into(image));
            }

            @Override
            public void onFailure(Exception e) {
            }
        });

        ((TextView) findViewById(R.id.detailBeachName)).setText(beach.getName());
        bindSaveButton();
        bindMeta();
        bindLifeguards();
        bindHazards();
        bindVerdict();

        if (beach.getConditions() != null) {
            updateConditionsDisplay(beach.getConditions());
        }

        weatherClient = new WeatherClient();
        fetchRealTimeData();
    }

    /** Save/unsave, persisted so the Saved tab picks it up immediately. */
    private void bindSaveButton() {
        SavedBeachStore store = new SavedBeachStore(this);
        MaterialButton save = findViewById(R.id.detailSaveButton);
        applySaveState(save, store.isSaved(beach.getId()));
        save.setOnClickListener(v -> applySaveState(save, store.toggle(beach.getId())));
    }

    private void applySaveState(MaterialButton save, boolean isSaved) {
        save.setText(isSaved ? R.string.saved_beach : R.string.save_beach);
        save.setIconResource(isSaved ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark);
        int color = ContextCompat.getColor(this,
            isSaved ? R.color.primary : R.color.text_primary);
        save.setTextColor(color);
        save.setIconTint(ColorStateList.valueOf(color));
        save.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this,
            isSaved ? R.color.primary : R.color.divider)));
    }

    /** "Lifeguard tower nearby · 2.4 mi away" under the beach name. */
    private void bindMeta() {
        boolean guarded = beach.getNearestHelp().stream()
            .anyMatch(h -> h.getType() == HelpType.LIFEGUARD_TOWER);
        String guardText = getString(guarded ? R.string.guarded_yes : R.string.guarded_no);
        ((TextView) findViewById(R.id.detailBeachMeta)).setText(guardText);
    }

    private void bindLifeguards() {
        TextView lifeguards = findViewById(R.id.detailLifeguards);
        List<HelpLocation> help = beach.getNearestHelp();
        HelpLocation tower = help.stream()
            .filter(h -> h.getType() == HelpType.LIFEGUARD_TOWER)
            .findFirst()
            .orElse(help.isEmpty() ? null : help.get(0));

        if (tower == null) {
            lifeguards.setText(R.string.lifeguards_none);
        } else {
            lifeguards.setText(getString(R.string.lifeguards_nearest, tower.getName()));
        }
    }

    /**
     * Renders one card per hazard on record. With none, the section says so
     * rather than leaving an empty gap.
     */
    private void bindHazards() {
        hazardList.removeAllViews();
        List<Hazard> hazards = beach.getHazards();
        LayoutInflater inflater = LayoutInflater.from(this);

        if (hazards.isEmpty()) {
            View row = inflater.inflate(R.layout.item_hazard, hazardList, false);
            ImageView icon = row.findViewById(R.id.hazardIcon);
            icon.setImageResource(R.drawable.ic_check_circle);
            ImageViewCompat.setImageTintList(icon,
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.safe)));
            ((TextView) row.findViewById(R.id.hazardTitle)).setText(R.string.no_major_hazards);
            row.findViewById(R.id.hazardBody).setVisibility(View.GONE);
            hazardList.addView(row);
            return;
        }

        for (Hazard hazard : hazards) {
            View row = inflater.inflate(R.layout.item_hazard, hazardList, false);
            ImageView icon = row.findViewById(R.id.hazardIcon);
            int color = ContextCompat.getColor(this, severityColorRes(hazard.getSeverity()));
            ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(color));

            ((TextView) row.findViewById(R.id.hazardTitle))
                .setText(hazard.getType().getLabelRes());
            ((TextView) row.findViewById(R.id.hazardBody))
                .setText(hazard.getDescription());
            hazardList.addView(row);
        }
    }

    private static int severityColorRes(Severity severity) {
        if (severity == Severity.HIGH || severity == Severity.EXTREME) return R.color.danger;
        if (severity == Severity.MODERATE) return R.color.caution;
        return R.color.safe;
    }

    /** Colours the chip over the photo with the beach's swim verdict. */
    private void bindVerdict() {
        SafetyLevel level = SafetyLevel.evaluate(beach);
        int color = ContextCompat.getColor(this,
            level != null ? level.getColorRes() : R.color.text_secondary);

        verdictIcon.setImageResource(
            level != null ? level.getIconRes() : R.drawable.ic_warning_circle);
        ImageViewCompat.setImageTintList(verdictIcon, ColorStateList.valueOf(color));
        verdictText.setText(level != null ? level.getVerdictRes() : R.string.verdict_unknown);
        verdictText.setTextColor(color);

        android.graphics.drawable.Drawable background = verdictChip.getBackground();
        if (background instanceof GradientDrawable) {
            int stroke = Math.round(getResources().getDisplayMetrics().density);
            ((GradientDrawable) background.mutate()).setStroke(stroke, color);
        }

        if (level == null) {
            advisorSummary.setText(R.string.verdict_unknown_note);
        } else {
            advisorSummary.setText(new SafetyNote().describe(this, beach, level));
        }
    }

    private void fetchRealTimeData() {
        weatherClient.getBeachConditions(beach.getLatitude(), beach.getLongitude(), new WeatherClient.WeatherCallback() {
            @Override
            public void onSuccess(BeachConditions conditions) {
                runOnUiThread(() -> {
                    updateConditionsDisplay(conditions);
                    // Re-judge with live numbers in hand.
                    beach = beach.withConditions(conditions);
                    bindVerdict();
                });
            }

            @Override
            public void onFailure(Exception e) {
                // Leave whatever static conditions were already shown.
            }
        });
    }

    private void updateConditionsDisplay(BeachConditions conditions) {
        TextView airTemp = findViewById(R.id.detailAirTemp);
        ImageView airTempIcon = findViewById(R.id.detailAirTempIcon);

        if (conditions.getAirTemperature() != null) {
            airTemp.setText(UnitConverter.formatTemperature(this, conditions.getAirTemperature()));

            // The tile's own label icon carries the cold/warm/hot reading, so
            // there is only ever one thermometer on the tile.
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

            airTempIcon.setImageResource(iconRes);
            ImageViewCompat.setImageTintList(airTempIcon, ColorStateList.valueOf(color));
        } else {
            airTemp.setText("--");
        }

        ((TextView) findViewById(R.id.detailWaveHeight))
            .setText(UnitConverter.formatWaveHeight(this, conditions.getWaveHeight()));
        ((TextView) findViewById(R.id.detailUvIndex))
            .setText(conditions.getFormattedUvIndex());
        ((TextView) findViewById(R.id.detailVisibility))
            .setText(UnitConverter.formatVisibility(this, conditions.getVisibility()));
        ((TextView) findViewById(R.id.detailWindSpeed))
            .setText(UnitConverter.formatWindSpeed(this, conditions.getWindSpeed()));
    }
}
