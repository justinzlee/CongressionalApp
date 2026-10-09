package com.example.myapplication;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.myapplication.data.BeachRepository;
import com.example.myapplication.data.LastKnownLocation;
import com.example.myapplication.data.LiveReadings;
import com.example.myapplication.data.RankingScope;
import com.example.myapplication.data.RockRatingStore;
import com.example.myapplication.location.LocationService;
import com.example.myapplication.model.Activity;
import com.example.myapplication.model.ActivityFit;
import com.example.myapplication.model.ActivityScorer;
import com.example.myapplication.model.AirQuality;
import com.example.myapplication.model.Beach;
import com.example.myapplication.model.BeachConditions;
import com.example.myapplication.model.ConditionHazards;
import com.example.myapplication.model.Hazard;
import com.example.myapplication.model.HelpType;
import com.example.myapplication.model.ParkingSummary;
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
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Every beach in range, ranked by how well it suits one thing you might want
 * to do.
 *
 * <p>The readings are fetched once for every beach in scope and every activity
 * is then scored from them in memory, so switching between "where should I
 * surf" and "where should I picnic" is instant and costs no network. That is
 * also why the picker sits at the top rather than on a previous screen: the
 * interesting thing about these four rankings is how differently they order the
 * same coast, and that is only visible if flipping between them is free.
 *
 * <p>Which beaches are in scope is {@link RankingScope}'s decision: the ones
 * saved, plus the ones within fifty miles of where the phone is as this screen
 * opens, or only the saved ones if the user has asked for that in More. All
 * of them are listed, best first, rather than only the top few.
 *
 * <p>Every beach is scored from what its own screen scores it from, parking
 * included, so the number beside a beach here is the number its page shows
 * under "Good for today". Parking used to come only from a cache the beach
 * screens fill, so a beach nobody had opened was ranked without it, and the
 * order changed once somebody did.
 */
public class ActivityRankingActivity extends AppCompatActivity {

    private static final String STATE_ACTIVITY = "activity";

    /**
     * How long the list waits on parking before drawing without it. Parking
     * comes from a free service that can take half a minute when it is busy,
     * while the weather, air and water that carry most of every score are in
     * within a second or two.
     */
    private static final long PARKING_WAIT_MS = 8_000;

    private final List<TextView> activityChips = new ArrayList<>();
    private Activity selected = Activity.SWIM;

    private List<Beach> beaches = new ArrayList<>();
    private BeachConditions[] conditions;
    private AirQuality[] air;
    private WaterQuality[] water;
    private ParkingSummary[] parking;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean readingsIn;
    private boolean parkingIn;
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
        locateThenFetch();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_ACTIVITY, selected.name());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
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
                // Segments of one tray sit edge to edge.
                ((ViewGroup.MarginLayoutParams) params).setMarginEnd(0);
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

    /**
     * One segment of the tray: the selected one takes the sea tint, the rest
     * sit flat on the tray, the same as the radius setting on the home screen.
     */
    private void paintChip(TextView chip, boolean isSelected) {
        chip.setTextColor(ContextCompat.getColor(this,
            isSelected ? R.color.primary : R.color.text_secondary));
        Drawable background = chip.getBackground();
        if (background instanceof GradientDrawable) {
            GradientDrawable shape = (GradientDrawable) background.mutate();
            shape.setColor(isSelected
                ? ContextCompat.getColor(this, R.color.accent_tint)
                : android.graphics.Color.TRANSPARENT);
            shape.setStroke(0, android.graphics.Color.TRANSPARENT);
        }
    }

    // ── Fetching ─────────────────────────────────────────────────────────
    //
    // Where the phone is first, since that decides which beaches are asked
    // about. Then four sources over the same list of points, each reporting per
    // point and then completing. The screen draws once all of them have, since
    // a ranking that reorders itself while you are reading it is worse than
    // one that takes a moment to appear — except that parking only gets
    // PARKING_WAIT_MS past the rest before the list is drawn without it.

    /**
     * A fresh fix rather than the one the home screen last stored, since a
     * trip since then changes which beaches are within range. The stored one
     * is only the fallback when the phone cannot give a fresh one, and with
     * neither, only saved beaches are ranked.
     */
    private void locateThenFetch() {
        new LocationService(this).getCurrentLocation(new LocationService.LocationUpdateListener() {
            @Override
            public void onLocationUpdated(double latitude, double longitude) {
                if (isFinishing() || isDestroyed()) return;
                new LastKnownLocation(ActivityRankingActivity.this).remember(latitude, longitude);
                fetchEverything(latitude, longitude);
            }

            @Override
            public void onLocationError(String error) {
                if (isFinishing() || isDestroyed()) return;
                LastKnownLocation last = new LastKnownLocation(ActivityRankingActivity.this);
                fetchEverything(last.isKnown() ? last.getLatitude() : null,
                    last.isKnown() ? last.getLongitude() : null);
            }
        });
    }

    private void fetchEverything(@Nullable Double latitude, @Nullable Double longitude) {
        beaches = RankingScope.inScope(this, BeachRepository.getInstance().getBeaches(),
            latitude, longitude);
        int count = beaches.size();
        conditions = new BeachConditions[count];
        air = new AirQuality[count];
        water = new WaterQuality[count];
        parking = new ParkingSummary[count];

        if (count == 0) {
            show();
            return;
        }

        double[] latitudes = new double[count];
        double[] longitudes = new double[count];
        List<String> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            latitudes[i] = beaches.get(i).getLatitude();
            longitudes[i] = beaches.get(i).getLongitude();
            ids.add(beaches.get(i).getId());
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
                    // Handed back to the shared list, as the beach screen does,
                    // so a beach opened from here starts on the same reading.
                    if (c != null) {
                        BeachRepository.getInstance()
                            .updateConditions(beaches.get(offset + index).getId(), c);
                    }
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

        new ParkingClient(this).getParking(ids, latitudes, longitudes,
            new ParkingClient.BatchCallback() {
                @Override
                public void onPoint(int index, @Nullable ParkingSummary p) {
                    parking[index] = p;
                }

                @Override
                public void onComplete() {
                    runOnUiThread(() -> {
                        parkingIn = true;
                        if (readingsIn) show();
                    });
                }
            });
    }

    /** Counts down the weather, air and water; parking reports on its own. */
    private void settle(AtomicInteger outstanding) {
        if (outstanding.decrementAndGet() > 0) return;
        runOnUiThread(() -> {
            readingsIn = true;
            if (parkingIn) {
                show();
            } else {
                handler.postDelayed(() -> {
                    if (!loaded) show();
                }, PARKING_WAIT_MS);
            }
        });
    }

    /**
     * Draws the ranking. Runs once the readings are in, and again if parking
     * turns up after the list was drawn without it: a late reorder is a smaller
     * fault than a score that disagrees with the beach's own page.
     */
    private void show() {
        if (isFinishing() || isDestroyed()) return;
        loaded = true;
        bindRanking();
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
            status.setText(RankingScope.savedOnly(this)
                ? R.string.activity_ranking_empty_saved : R.string.activity_ranking_empty);
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

        status.setText(getString(R.string.activity_ranking_heading, String.valueOf(ranked.size()),
            getString(selected.getLabelRes()).toLowerCase(Locale.getDefault())));

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

        List<ActivityFit> fits = new ArrayList<>();
        for (int i = 0; i < beaches.size(); i++) {
            if (conditions[i] == null) continue;
            Beach beach = beaches.get(i).withConditions(conditions[i]);

            ActivityScorer.Inputs in = new ActivityScorer.Inputs();
            in.conditions = conditions[i];
            in.air = air[i];
            in.water = water[i];
            in.parking = parking[i];
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
