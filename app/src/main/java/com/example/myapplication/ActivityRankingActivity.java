package com.example.myapplication;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.example.myapplication.data.AlertScope;
import com.example.myapplication.data.BeachRepository;
import com.example.myapplication.data.LiveReadings;
import com.example.myapplication.data.RockRatingStore;
import com.example.myapplication.model.Activity;
import com.example.myapplication.model.ActivityFit;
import com.example.myapplication.model.ActivityScorer;
import com.example.myapplication.model.AirQuality;
import com.example.myapplication.model.Beach;
import com.example.myapplication.model.BeachConditions;
import com.example.myapplication.model.ConditionHazards;
import com.example.myapplication.model.Hazard;
import com.example.myapplication.model.HelpType;
import com.example.myapplication.model.Severity;
import com.example.myapplication.model.WaterQuality;
import com.example.myapplication.network.AirQualityClient;
import com.example.myapplication.network.ParkingClient;
import com.example.myapplication.network.RainfallClient;
import com.example.myapplication.network.WeatherClient;
import com.example.myapplication.util.EdgeToEdge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Beaches ranked by how well they suit one thing you might want to do.
 *
 * <p>The readings are fetched once for every beach in scope and every activity
 * is then scored from them in memory, so switching between "where should I
 * surf" and "where should I picnic" is instant and costs no network. That is
 * also why the picker sits at the top rather than on a previous screen: the
 * interesting thing about these four rankings is how differently they order the
 * same coast, and that is only visible if flipping between them is free.
 *
 * <p>Which beaches are in scope is {@link AlertScope}'s decision, the same one
 * the Alerts tab uses — the beaches near you plus the ones you saved. Ranking
 * every beach in the country would be a slower answer to a question nobody
 * asked.
 */
public class ActivityRankingActivity extends AppCompatActivity {

    private static final String STATE_ACTIVITY = "activity";

    /**
     * How many beaches are ranked. Well past what anyone scrolls, and low
     * enough that the batched fetches stay to a couple of requests each.
     */
    private static final int MAX_RANKED = 40;

    private final List<TextView> activityChips = new ArrayList<>();
    private Activity selected = Activity.SWIM;

    private List<Beach> beaches = new ArrayList<>();
    private BeachConditions[] conditions;
    private AirQuality[] air;
    private WaterQuality[] water;

    private boolean loaded;

    public static Intent intentFor(Context context) {
        return new Intent(context, ActivityRankingActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activity_ranking);

        EdgeToEdge.applyTopInset(findViewById(R.id.rankHeader));
        EdgeToEdge.applyBottomInset(findViewById(R.id.rankScrollView));
        findViewById(R.id.rankBackButton).setOnClickListener(
            v -> getOnBackPressedDispatcher().onBackPressed());

        if (savedInstanceState != null) {
            selected = Activity.parse(savedInstanceState.getString(STATE_ACTIVITY));
        }

        buildActivityChips();
        showActivity(selected);
        fetchEverything();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_ACTIVITY, selected.name());
    }

    private void buildActivityChips() {
        ViewGroup row = findViewById(R.id.rankActivityRow);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Activity activity : Activity.values()) {
            TextView chip = (TextView) inflater.inflate(R.layout.item_coast_chip, row, false);
            chip.setText(activity.getLabelRes());
            chip.setOnClickListener(v -> showActivity(activity));

            ViewGroup.LayoutParams params = chip.getLayoutParams();
            if (params instanceof ViewGroup.MarginLayoutParams) {
                // Four across, sharing the width evenly.
                ((ViewGroup.MarginLayoutParams) params).width = 0;
            }
            if (params instanceof android.widget.LinearLayout.LayoutParams) {
                ((android.widget.LinearLayout.LayoutParams) params).weight = 1;
            }
            chip.setLayoutParams(params);
            chip.setGravity(android.view.Gravity.CENTER);

            activityChips.add(chip);
            row.addView(chip);
        }
    }

    private void showActivity(Activity activity) {
        selected = activity;
        for (int i = 0; i < activityChips.size(); i++) {
            paintChip(activityChips.get(i), Activity.values()[i] == activity);
        }
        ((TextView) findViewById(R.id.rankBlurb)).setText(activity.getBlurbRes());
        bindRanking();
    }

    /** The selected chip fills with the brand colour; the rest stay hairlines. */
    private void paintChip(TextView chip, boolean isSelected) {
        chip.setTextColor(ContextCompat.getColor(this,
            isSelected ? R.color.on_primary : R.color.text_secondary));
        Drawable background = chip.getBackground();
        if (background instanceof GradientDrawable) {
            GradientDrawable shape = (GradientDrawable) background.mutate();
            shape.setColor(ContextCompat.getColor(this,
                isSelected ? R.color.primary : R.color.surface));
            shape.setStroke(Math.round(getResources().getDisplayMetrics().density),
                ContextCompat.getColor(this, isSelected ? R.color.primary : R.color.divider));
        }
    }

    // ── Fetching ─────────────────────────────────────────────────────────
    //
    // Three batched sources over the same list of points. Each one reports per
    // point and then completes; the screen draws once all of them have, since a
    // ranking that reorders itself twice while you are reading it is worse than
    // one that takes a moment to appear.

    private void fetchEverything() {
        beaches = AlertScope.inScope(this, BeachRepository.getInstance().getBeaches());
        if (beaches.size() > MAX_RANKED) {
            beaches = new ArrayList<>(beaches.subList(0, MAX_RANKED));
        }
        int count = beaches.size();
        conditions = new BeachConditions[count];
        air = new AirQuality[count];
        water = new WaterQuality[count];

        if (count == 0) {
            loaded = true;
            bindRanking();
            return;
        }

        double[] latitudes = new double[count];
        double[] longitudes = new double[count];
        for (int i = 0; i < count; i++) {
            latitudes[i] = beaches.get(i).getLatitude();
            longitudes[i] = beaches.get(i).getLongitude();
        }

        int chunks = (count + WeatherClient.BATCH_SIZE - 1) / WeatherClient.BATCH_SIZE;
        // Three sources, one completion each per chunk.
        AtomicInteger outstanding = new AtomicInteger(chunks * 3);

        WeatherClient weather = new WeatherClient();
        AirQualityClient airClient = new AirQualityClient();
        RainfallClient rainClient = new RainfallClient();

        for (int start = 0; start < count; start += WeatherClient.BATCH_SIZE) {
            int end = Math.min(count, start + WeatherClient.BATCH_SIZE);
            double[] lats = slice(latitudes, start, end);
            double[] lons = slice(longitudes, start, end);
            final int offset = start;

            weather.getBeachConditions(lats, lons, new WeatherClient.BatchCallback() {
                @Override
                public void onPoint(int index, BeachConditions c) {
                    conditions[offset + index] = c;
                }

                @Override
                public void onComplete() {
                    settle(outstanding);
                }
            });

            airClient.getAirQuality(lats, lons, new AirQualityClient.BatchCallback() {
                @Override
                public void onPoint(int index, AirQuality a) {
                    air[offset + index] = a;
                    LiveReadings.putAir(beaches.get(offset + index).getId(), a);
                }

                @Override
                public void onComplete() {
                    settle(outstanding);
                }
            });

            rainClient.getRecentRain(lats, lons, new RainfallClient.BatchCallback() {
                @Override
                public void onPoint(int index, double[] dailyRainMm) {
                    Beach beach = beaches.get(offset + index);
                    WaterQuality w = WaterQuality.evaluate(beach, dailyRainMm);
                    water[offset + index] = w;
                    LiveReadings.putWater(beach.getId(), w);
                }

                @Override
                public void onComplete() {
                    settle(outstanding);
                }
            });
        }
    }

    private void settle(AtomicInteger outstanding) {
        if (outstanding.decrementAndGet() > 0) return;
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            loaded = true;
            bindRanking();
        });
    }

    private static double[] slice(double[] values, int start, int end) {
        double[] out = new double[end - start];
        System.arraycopy(values, start, out, 0, end - start);
        return out;
    }

    // ── Drawing ──────────────────────────────────────────────────────────

    private void bindRanking() {
        TextView status = findViewById(R.id.rankStatus);
        ViewGroup list = findViewById(R.id.rankList);
        list.removeAllViews();

        if (!loaded) {
            status.setVisibility(View.VISIBLE);
            status.setText(R.string.activity_ranking_loading);
            return;
        }
        if (beaches.isEmpty()) {
            status.setVisibility(View.VISIBLE);
            status.setText(R.string.activity_ranking_empty);
            return;
        }

        List<ActivityFit> ranked = rank();
        status.setVisibility(View.VISIBLE);

        // Beaches in scope but not one forecast between them: the fetches all
        // failed. Saying "11 beaches ranked" above an empty list would read as
        // though every one of them had been judged and found wanting.
        if (ranked.isEmpty()) {
            status.setText(R.string.activity_ranking_no_readings);
            return;
        }

        status.setText(getString(R.string.activity_ranking_heading,
            String.valueOf(ranked.size()), getString(selected.getLabelRes()).toLowerCase(
                java.util.Locale.getDefault())));

        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < ranked.size(); i++) {
            list.addView(buildRow(inflater, list, ranked.get(i), i + 1));
        }
    }

    /**
     * Every beach scored for the chosen activity, best first.
     *
     * <p>Beaches with no forecast at all are dropped rather than ranked last.
     * A beach the wave model has nothing for is not a bad beach to surf; it is
     * a beach this app cannot speak for, and putting it at the bottom of the
     * list would be a claim rather than an omission.
     */
    private List<ActivityFit> rank() {
        RockRatingStore rocks = new RockRatingStore(this);
        ParkingClient parkingClient = new ParkingClient(this);

        List<ActivityFit> fits = new ArrayList<>();
        for (int i = 0; i < beaches.size(); i++) {
            if (conditions[i] == null) continue;
            Beach beach = beaches.get(i).withConditions(conditions[i]);

            ActivityScorer.Inputs in = new ActivityScorer.Inputs();
            in.conditions = conditions[i];
            in.air = air[i];
            in.water = water[i];
            in.parking = parkingClient.cached(beach.getId());
            in.rockRating = rocks.ratingFor(beach.getId());
            in.guarded = guardedState(beach);
            in.worstHazard = worstHazard(beach);

            fits.add(ActivityScorer.score(selected, beach, in));
        }
        fits.sort(Comparator.comparingInt(ActivityFit::getScore).reversed());
        return fits;
    }


    /**
     * Whether this beach is patrolled, or null when the app has no help data
     * for it. An empty list means nobody recorded any, not that there is none —
     * see {@link ActivityScorer.Inputs#guarded}.
     */
    @Nullable
    private static Boolean guardedState(Beach beach) {
        if (beach.getNearestHelp().isEmpty()) return null;
        return beach.getNearestHelp().stream()
            .anyMatch(h -> h.getType() == HelpType.LIFEGUARD_TOWER);
    }

    @Nullable
    private Severity worstHazard(Beach beach) {
        Severity worst = null;
        for (Hazard hazard : ConditionHazards.merged(this, beach)) {
            if (worst == null || hazard.getSeverity().ordinal() > worst.ordinal()) {
                worst = hazard.getSeverity();
            }
        }
        return worst;
    }

    private View buildRow(LayoutInflater inflater, ViewGroup parent, ActivityFit fit, int position) {
        View row = inflater.inflate(R.layout.item_ranked_beach, parent, false);
        int color = ContextCompat.getColor(this, fit.getColorRes());

        ((TextView) row.findViewById(R.id.rankPosition))
            .setText(getString(R.string.fit_rank, position));
        ((TextView) row.findViewById(R.id.rankBeachName)).setText(fit.getBeach().getName());

        TextView verdict = row.findViewById(R.id.rankVerdict);
        verdict.setText(fit.getVerdictRes());
        verdict.setTextColor(color);
        ((TextView) row.findViewById(R.id.rankScore))
            .setText(getString(R.string.fit_out_of, fit.getScore()));

        ((TextView) row.findViewById(R.id.rankReason)).setText(reasonFor(fit));

        row.setOnClickListener(v -> {
            Intent intent = new Intent(this, BeachDetailActivity.class);
            intent.putExtra(BeachDetailActivity.EXTRA_BEACH_ID, fit.getBeach().getId());
            startActivity(intent);
        });
        return row;
    }

    /** Same rule as the beach screen: what held it back, else what carried it. */
    private String reasonFor(ActivityFit fit) {
        if (!fit.isConfident()) return getString(R.string.fit_thin);
        ActivityFit.Factor limiting = fit.getLimitingFactor();
        if (limiting != null) {
            return getString(R.string.fit_held_back, getString(limiting.getLabelRes()));
        }
        ActivityFit.Factor strongest = fit.getStrongestFactor();
        if (strongest != null) {
            return getString(R.string.fit_helped_by, getString(strongest.getLabelRes()));
        }
        return getString(R.string.fit_thin);
    }
}
