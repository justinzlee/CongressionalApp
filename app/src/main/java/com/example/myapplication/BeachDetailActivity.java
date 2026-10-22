package com.example.myapplication;

import android.content.ActivityNotFoundException;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.widget.ImageViewCompat;
import androidx.core.widget.TextViewCompat;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.example.myapplication.ai.SafetyNote;
import com.example.myapplication.data.BeachPhotoStore;
import com.example.myapplication.data.BeachRepository;
import com.example.myapplication.data.LastKnownLocation;
import com.example.myapplication.data.LiveReadings;
import com.example.myapplication.data.CombinedReports;
import com.example.myapplication.data.RockRatingStore;
import com.example.myapplication.data.SeagullRatingStore;
import com.example.myapplication.data.SavedBeachStore;
import com.example.myapplication.data.SightingStore;
import com.google.android.material.button.MaterialButton;
import com.example.myapplication.model.Beach;
import com.example.myapplication.model.BeachConditions;
import com.example.myapplication.model.ConditionHazards;
import com.example.myapplication.model.Hazard;
import com.example.myapplication.model.HelpLocation;
import com.example.myapplication.model.HelpType;
import com.example.myapplication.model.Activity;
import com.example.myapplication.model.ActivityFit;
import com.example.myapplication.model.ActivityScorer;
import com.example.myapplication.model.AirQuality;
import com.example.myapplication.model.ParkingSummary;
import com.example.myapplication.model.RockRating;
import com.example.myapplication.model.SeagullRating;
import com.example.myapplication.model.SafetyLevel;
import com.example.myapplication.model.Severity;
import com.example.myapplication.model.Sighting;
import com.example.myapplication.model.SightingSpecies;
import com.example.myapplication.model.WildlifeTally;
import com.example.myapplication.model.Tide;
import com.example.myapplication.model.WaterQuality;
import com.example.myapplication.model.TideForecast;
import com.example.myapplication.location.LocationService;
import com.example.myapplication.network.AirQualityClient;
import com.example.myapplication.network.LifeguardClient;
import com.example.myapplication.network.ParkingClient;
import com.example.myapplication.network.RainfallClient;
import com.example.myapplication.network.TideClient;
import com.example.myapplication.network.WeatherClient;
import com.example.myapplication.util.BeachPlaceholder;
import com.example.myapplication.util.EdgeToEdge;
import com.example.myapplication.util.UnitConverter;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * One beach in full: the swim verdict over its photograph, the readings it is
 * drawn from, the map, the air, water and parking, what it is good for today,
 * what to watch for, and what people have reported there.
 *
 * <p>Most of the screen arrives after it opens — the forecast, the tides, the
 * air, the rainfall and the parking are five separate calls that land in their
 * own time. Until each one does, its place on screen shows a {@link
 * SwellLoaderView} rather than a dash or an empty box, so "still asking" and
 * "nothing there" never look the same. A reading that could not be had says
 * so; the forecast, which carries most of the page, also offers to try again,
 * and anything that failed is asked for again the next time the screen comes
 * back to the front.
 */
public class BeachDetailActivity extends AppCompatActivity {
    public static final String EXTRA_BEACH_ID = "extra_beach_id";

    /**
     * How often the tide countdowns are rewritten while the screen is in
     * front. "In 3h 20m" is worked out from the clock, not fetched, so it can
     * be kept true for free; left alone it went on counting from whenever the
     * forecast landed.
     */
    private static final long TIDE_TICK_MS = 30_000L;

    /**
     * Where a camera capture is being written. Held across a save/restore
     * because the camera is another app in front of ours, and a phone short on
     * memory is free to kill this process while it is there — losing the path
     * would strand the photo the user just took in our cache.
     */
    private static final String STATE_PENDING_CAPTURE = "pending_capture";

    private WeatherClient weatherClient;
    private TideClient tideClient;
    /** When the readings on screen were last asked for; zero means never. */
    private long fetchedAt;

    private Beach beach;
    private ViewGroup hazardList;
    private ImageView verdictIcon;
    private TextView verdictText, advisorSummary;
    private View verdictChip;
    private WaveHeightCard waveCard;
    private TextView lowTideCountdown, highTideCountdown;

    // Each tile's value and the loader that stands in for it until it lands.
    private ReadingSlot uvSlot, windSlot, airTempSlot, waterTempSlot, visibilitySlot;
    private ReadingSlot lowTideSlot, highTideSlot;
    private TextView conditionsError;

    /** Where one of the screen's calls stands. */
    private enum Fetch {
        /** Not asked for yet: its place shows a loader, since it is about to be. */
        NOT_ASKED,
        /** Out now. A second copy is never sent while one is. */
        ASKING,
        ANSWERED,
        /** Came back with nothing: worth asking again on the way back in. */
        FAILED
    }

    private Fetch weatherFetch = Fetch.NOT_ASKED;
    private Fetch tideFetch = Fetch.NOT_ASKED;
    private Fetch airFetch = Fetch.NOT_ASKED;
    private Fetch waterFetch = Fetch.NOT_ASKED;
    private Fetch parkingFetch = Fetch.NOT_ASKED;

    /** The last tide forecast, kept so the countdowns can be redrawn without refetching. */
    @Nullable private TideForecast tideForecast;
    private final Handler clock = new Handler(Looper.getMainLooper());
    private final Runnable tideTick = new Runnable() {
        @Override
        public void run() {
            if (tideForecast != null) updateTideDisplay(tideForecast);
            clock.postDelayed(this, TIDE_TICK_MS);
        }
    };

    private ImageView heroImage;
    private View heroScrim;
    private BeachPhotoStore photoStore;
    private ActivityResultLauncher<PickVisualMediaRequest> photoPicker;
    private ActivityResultLauncher<Uri> photoCamera;
    private File pendingCapture;

    private RockRatingStore rockStore;
    private SeagullRatingStore gullStore;
    private CombinedReports combined;
    private SightingStore sightingStore;

    private AirQualityClient airClient;
    private RainfallClient rainClient;
    private ParkingClient parkingClient;

    /**
     * The three readings that arrive after the forecast does. Held rather than
     * bound straight to views because the activity scores are a blend of all of
     * them plus the conditions, so each one landing has to rebuild the same
     * section — see {@link #bindGoodFor()}.
     */
    @Nullable private AirQuality air;
    @Nullable private WaterQuality water;
    @Nullable private ParkingSummary parking;

    private BeachMapCard mapCard;

    /**
     * Lifeguard towers OpenStreetMap has around this beach, once they land.
     * Empty until then, and when nothing is mapped. The map, the patrol line,
     * the Lifeguards card and the activity scores all read these alongside
     * the app's own records, so none of them can disagree with another.
     */
    private List<HelpLocation> mappedTowers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_beach_detail);

        String beachId = getIntent().getStringExtra(EXTRA_BEACH_ID);
        beach = BeachRepository.getInstance().findById(beachId);

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
        waveCard = findViewById(R.id.detailWaveCard);
        waveCard.setBeachName(beach.getName());
        lowTideCountdown = findViewById(R.id.detailLowTideCountdown);
        highTideCountdown = findViewById(R.id.detailHighTideCountdown);

        uvSlot = slot(R.id.detailUvIndex, R.id.detailUvLoader);
        windSlot = slot(R.id.detailWindSpeed, R.id.detailWindLoader);
        airTempSlot = slot(R.id.detailAirTemp, R.id.detailAirTempLoader);
        waterTempSlot = slot(R.id.detailWaterTemp, R.id.detailWaterTempLoader);
        visibilitySlot = slot(R.id.detailVisibility, R.id.detailVisibilityLoader);
        lowTideSlot = slot(R.id.detailLowTide, R.id.detailLowTideLoader);
        highTideSlot = slot(R.id.detailHighTide, R.id.detailHighTideLoader);

        conditionsError = findViewById(R.id.detailConditionsError);
        conditionsError.setOnClickListener(v -> retryConditions());

        heroImage = findViewById(R.id.beachDetailImage);
        heroScrim = findViewById(R.id.detailHeroScrim);
        photoStore = new BeachPhotoStore(this);
        if (savedInstanceState != null) {
            String pending = savedInstanceState.getString(STATE_PENDING_CAPTURE);
            if (pending != null) pendingCapture = new File(pending);
        }
        registerPhotoLaunchers();
        bindHeroPhoto();
        bindPhotoButton();

        ((TextView) findViewById(R.id.detailBeachName)).setText(beach.getName());
        bindSaveButton();
        bindMeta();
        bindLifeguards();
        bindHazards();
        bindVerdict();
        bindMap();

        rockStore = new RockRatingStore(this);
        gullStore = new SeagullRatingStore(this);
        combined = new CombinedReports(this);
        // The real-world starting points for rocks, gulls and wildlife. Each
        // redraws its card when it lands; until then the cards show this
        // phone's reports alone, or whatever an earlier visit cached.
        combined.refresh(beach, this::onStartingPointsChanged);
        sightingStore = new SightingStore(this);
        findViewById(R.id.detailRocksCard).setOnClickListener(
            v -> startActivity(RockRatingActivity.intentFor(this, beach.getId())));
        findViewById(R.id.detailGullsCard).setOnClickListener(v -> rateGulls());
        findViewById(R.id.detailSightingCta).setOnClickListener(
            v -> startActivity(SightingActivity.intentFor(this, beach.getId())));
        // The rows themselves are drawn in onResume, which also runs on the way
        // back from those two screens.

        if (beach.getConditions() != null) {
            updateConditionsDisplay(beach.getConditions());
        } else {
            // Loaders in every tile, and the sea settled at rest, while the
            // forecast is still in flight - rather than a page of dashes that
            // reads the same as "there is no reading".
            showConditionsLoading();
        }
        // The tide tiles take their loaders in fetchTides, from onStart.
        // Air, water and parking show their cards from the start, each with a
        // loader where its reading will go, so the page does not grow under
        // the reader's thumb as they land one by one.
        bindEnvironment();
        bindGoodFor();

        weatherClient = new WeatherClient();
        tideClient = new TideClient();
        airClient = new AirQualityClient();
        rainClient = new RainfallClient();
        parkingClient = new ParkingClient(this);
        // The readings themselves are asked for in onStart, which runs on the
        // way in and again whenever this screen comes back to the front.
    }

    private ReadingSlot slot(int valueId, int loaderId) {
        return new ReadingSlot(findViewById(valueId), findViewById(loaderId));
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (beach == null) return;
        // Left open in the background, this screen used to come back showing
        // an hours-old reading as if it were current, beside a tide countdown
        // that had long since run out. The shared list's freshness window
        // keeps a quick trip away from refetching; anything that failed last
        // time is asked for again regardless, since that is the reading the
        // page is missing.
        long now = System.currentTimeMillis();
        boolean due = now - fetchedAt >= BeachRepository.FRESH_FOR_MS;
        if (due) fetchedAt = now;

        // The forecast is skipped while the one in hand is still fresh. A
        // beach opened from the list usually arrives with a reading the list
        // fetched a minute ago, and asking Open-Meteo for the same numbers
        // again only spends a call against its rate limit.
        if (weatherFetch == Fetch.FAILED || (due && !hasFreshConditions(now))) {
            fetchRealTimeData();
        }
        if (due || tideFetch == Fetch.FAILED) fetchTides();
        if (due || airFetch == Fetch.FAILED) fetchAirQuality();
        if (due || waterFetch == Fetch.FAILED) fetchWaterQuality();
        if (due || parkingFetch == Fetch.FAILED) fetchParking();
    }

    private boolean hasFreshConditions(long now) {
        BeachConditions conditions = beach.getConditions();
        return conditions != null
            && now - conditions.getFetchedAtMillis() < BeachRepository.FRESH_FOR_MS;
    }

    /**
     * Redraws everything a user report can change.
     *
     * <p>Runs on the way in and on the way back from the rating and sighting
     * screens, which is the only way this screen learns that a report was
     * filed. The hazard list is rebuilt alongside the two report sections, not
     * just the sections themselves: a stranding is filed as a hazard, so
     * leaving "What to watch for" as it was drawn would put a washed-up seal in
     * the log at the bottom of the screen and nowhere near the warnings.
     */
    @Override
    protected void onResume() {
        super.onResume();
        bindRocks();
        bindGulls();
        bindSightings();
        bindHazards();
        // A rocks rating filed on the way back moves two of the four scores:
        // shingle is a problem walking in and an advantage to fish over.
        bindGoodFor();
        mapCard.onResume();
        // Brings the countdowns up to date straight away, then keeps them so.
        clock.removeCallbacks(tideTick);
        tideTick.run();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapCard.onPause();
        clock.removeCallbacks(tideTick);
    }

    @Override
    protected void onDestroy() {
        clock.removeCallbacksAndMessages(null);
        // Null when onCreate turned away a beach it could not find.
        if (mapCard != null) mapCard.onDestroy();
        super.onDestroy();
    }

    /**
     * The rocks-underfoot card: what the shore comes out as once the map's
     * starting point and this phone's rating are put together, and an
     * invitation to rate it either way.
     */
    private void bindRocks() {
        CombinedReports.Rating rocks = combined.rocks(beach.getId());
        boolean rated = rocks.isRated();

        TextView value = findViewById(R.id.detailRocksValue);
        TextView body = findViewById(R.id.detailRocksBody);
        TextView adviceLine = findViewById(R.id.detailRocksAdvice);
        TextView when = findViewById(R.id.detailRocksWhen);
        ImageView photo = findViewById(R.id.detailRocksPhoto);

        value.setVisibility(rated ? View.VISIBLE : View.GONE);
        adviceLine.setVisibility(rated ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.detailRocksCta)).setText(
            rocks.fromUser ? R.string.rocks_rerate_cta : R.string.rocks_rate_cta);
        bindSource(findViewById(R.id.detailRocksSource), rocks);
        bindRatedWhen(when, rocks.fromUser ? rockStore.ratedAt(beach.getId()) : 0);

        if (!rated) {
            body.setText(R.string.rocks_unrated_body);
            photo.setVisibility(View.GONE);
            return;
        }

        value.setText(getString(R.string.rocks_value, rocks.value));
        body.setText(RockRating.labelRes(rocks.value));
        adviceLine.setText(RockRating.adviceRes(rocks.value));

        // The photograph is the evidence for this phone's own rating, so it
        // goes with that rating and not with a number the map supplied.
        File evidence = rocks.fromUser ? rockStore.photoFor(beach.getId()) : null;
        if (evidence == null) {
            photo.setVisibility(View.GONE);
        } else {
            photo.setVisibility(View.VISIBLE);
            Glide.with(this).load(evidence).centerCrop().into(photo);
        }
    }

    /** The seagulls card: eBird's count and this phone's rating, put together. */
    private void bindGulls() {
        CombinedReports.Rating gulls = combined.gulls(beach.getId());
        boolean rated = gulls.isRated();

        TextView value = findViewById(R.id.detailGullsValue);
        TextView body = findViewById(R.id.detailGullsBody);

        value.setVisibility(rated ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.detailGullsCta)).setText(
            gulls.fromUser ? R.string.rocks_rerate_cta : R.string.gulls_rate_cta);
        // The nobody-has-said line reads at the rocks card's size; the advice
        // under a rating is secondary to the step named beside the title.
        TextViewCompat.setTextAppearance(body, rated
            ? R.style.TextAppearance_App_Subtitle : R.style.TextAppearance_App_Body);
        bindSource(findViewById(R.id.detailGullsSource), gulls);
        bindRatedWhen(findViewById(R.id.detailGullsWhen),
            gulls.fromUser ? gullStore.ratedAt(beach.getId()) : 0);

        if (!rated) {
            body.setText(R.string.gulls_unrated_body);
            return;
        }
        value.setText(SeagullRating.labelRes(gulls.value));
        body.setText(SeagullRating.adviceRes(gulls.value));
    }

    /** "From OpenStreetMap", "From eBird and your rating"; hidden for a rating that is only this phone's. */
    private static void bindSource(TextView line, CombinedReports.Rating rating) {
        line.setVisibility(rating.sourceRes == 0 ? View.GONE : View.VISIBLE);
        if (rating.sourceRes != 0) line.setText(rating.sourceRes);
    }

    /** "Rated 3 days ago" for this phone's own rating; hidden when there is none. */
    private void bindRatedWhen(TextView when, long ratedAt) {
        when.setVisibility(ratedAt > 0 ? View.VISIBLE : View.GONE);
        if (ratedAt > 0) {
            when.setText(getString(R.string.rocks_rated_when,
                SightingSheet.relativeTime(this, ratedAt)));
        }
    }

    /**
     * Redraws everything a starting point can move, as each one arrives from
     * the network. The hazards and scores are included because a rocky shore
     * on the map raises one and moves the other, exactly as a rating does.
     */
    private void onStartingPointsChanged() {
        if (isFinishing() || isDestroyed()) return;
        bindRocks();
        bindGulls();
        bindSightings();
        bindHazards();
        bindGoodFor();
    }

    private void rateGulls() {
        SeagullSheet.show(this, gullStore.ratingFor(beach.getId()), new SeagullSheet.Choice() {
            @Override
            public void onRated(int rating) {
                gullStore.save(beach.getId(), rating);
                bindGulls();
                // Gulls go after picnics, so the picnic score moves with them.
                bindGoodFor();
            }

            @Override
            public void onCleared() {
                gullStore.clear(beach.getId());
                bindGulls();
                bindGoodFor();
            }
        });
    }

    /**
     * The last fortnight's sightings counted by group — iNaturalist's and this
     * phone's together — and under them this phone's own reports, newest
     * first, which are the ones that can be shared or taken back.
     */
    private void bindSightings() {
        List<Sighting> sightings = sightingStore.forBeach(beach.getId());
        WildlifeTally tally = combined.wildlife(beach.getId());
        bindTally(tally);

        ViewGroup list = findViewById(R.id.detailSightingsList);
        list.removeAllViews();
        boolean counted = combined.wildlifeHasBase(beach.getId());
        findViewById(R.id.detailSightingsEmpty).setVisibility(
            sightings.isEmpty() && !counted ? View.VISIBLE : View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (Sighting sighting : sightings) {
            list.addView(buildSightingRow(inflater, list, sighting));
        }
    }

    /**
     * The fortnight's count, shown only when iNaturalist has contributed to it.
     * A count made of nothing but this phone's reports would repeat the rows
     * listed straight underneath.
     */
    private void bindTally(WildlifeTally tally) {
        ViewGroup rows = findViewById(R.id.detailWildlifeTally);
        TextView source = findViewById(R.id.detailWildlifeSource);
        rows.removeAllViews();
        boolean show = combined.wildlifeHasBase(beach.getId());
        rows.setVisibility(show ? View.VISIBLE : View.GONE);
        source.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;
        source.setText(sightingStore.forBeach(beach.getId()).isEmpty()
            ? R.string.source_inat : R.string.source_inat_and_you);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (SightingSpecies group : tally.groupsBySize()) {
            View row = inflater.inflate(R.layout.item_wildlife_tally, rows, false);
            ((ImageView) row.findViewById(R.id.tallyIcon)).setImageResource(group.getIconRes());
            ((TextView) row.findViewById(R.id.tallyLabel)).setText(group.getLabelRes());
            String count = String.format(Locale.getDefault(), "%d", tally.count(group));
            int dead = group == SightingSpecies.SEAL ? tally.getDeadSeals() : 0;
            ((TextView) row.findViewById(R.id.tallyCount)).setText(dead == 0 ? count
                : getString(R.string.wildlife_tally_with_dead, count,
                    getResources().getQuantityString(R.plurals.wildlife_tally_dead, dead, dead)));
            rows.addView(row);
        }
    }

    private View buildSightingRow(LayoutInflater inflater, ViewGroup parent, Sighting sighting) {
        View row = inflater.inflate(R.layout.item_sighting, parent, false);

        boolean stranded = sighting.isStranded();
        // Red on the row follows whether the report warns anyone, not whether
        // the animal was on the sand: a shark still in the water is the one
        // in-water report that does, and it would otherwise be drawn in the
        // same grey as a dolphin off the break.
        boolean warning = sighting.raisesWarning();
        int color = ContextCompat.getColor(this,
            warning ? R.color.danger : R.color.text_secondary);

        ImageView icon = row.findViewById(R.id.sightingRowIcon);
        icon.setImageResource(sighting.getSpecies().getIconRes());
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(color));

        ((TextView) row.findViewById(R.id.sightingRowSpecies))
            .setText(sighting.getLabelRes());

        TextView tag = row.findViewById(R.id.sightingRowTag);
        tag.setText(stranded ? R.string.sighting_tag_stranded : R.string.sighting_tag_water);
        tag.setTextColor(color);
        Drawable tagBackground = tag.getBackground();
        if (tagBackground instanceof GradientDrawable) {
            ((GradientDrawable) tagBackground.mutate()).setColor(ContextCompat.getColor(this,
                warning ? R.color.danger_tint : R.color.surface_alt));
        }

        ((TextView) row.findViewById(R.id.sightingRowWhen))
            .setText(SightingSheet.relativeTime(this, sighting.getTimeMillis()));

        TextView note = row.findViewById(R.id.sightingRowNote);
        if (sighting.getNote() == null) {
            note.setVisibility(View.GONE);
        } else {
            note.setVisibility(View.VISIBLE);
            note.setText(sighting.getNote());
        }

        ImageView photo = row.findViewById(R.id.sightingRowPhoto);
        File taken = sighting.getPhoto();
        if (taken == null) {
            photo.setVisibility(View.GONE);
        } else {
            photo.setVisibility(View.VISIBLE);
            Glide.with(this).load(taken).centerCrop().into(photo);
        }

        row.setOnClickListener(v -> SightingSheet.show(this, beach, sighting,
            new SightingSheet.Choice() {
                @Override
                public void onShare() {
                    SightingShare.share(BeachDetailActivity.this, beach, sighting);
                }

                @Override
                public void onDelete() {
                    sightingStore.remove(beach.getId(), sighting.getId());
                    Toast.makeText(BeachDetailActivity.this,
                        R.string.sighting_deleted, Toast.LENGTH_SHORT).show();
                    bindSightings();
                    // Deleting the stranding that raised the warning has to take
                    // the warning down with it.
                    bindHazards();
                }
            }));

        return row;
    }

    /**
     * The hero: this beach's submitted photo if someone has added one, else the
     * curated photograph, else the drawn shoreline.
     *
     * <p>Looking the name up on Wikipedia used to run here and overwrite the
     * curated one, but that search returns the wrong subject too often to trust
     * — a car park for Huntington Beach, a map for Galveston Island — and a
     * wrong photo is worse than an honest placeholder on a screen about whether
     * the water is safe. A photo somebody took standing on the sand is the
     * opposite case, which is why it outranks everything else here.
     */
    private void bindHeroPhoto() {
        Drawable current = heroImage.getDrawable();
        Drawable defaultPlaceholder = BeachPlaceholder.forBeach(this, beach);
        Drawable placeholder = current != null ? current : defaultPlaceholder;

        Glide.with(this)
            .load(photoStore.imageSourceFor(beach))
            .centerCrop()
            .placeholder(placeholder)
            .fallback(defaultPlaceholder)
            .error(defaultPlaceholder)
            // The scrim exists to keep the verdict chip readable on a
            // photograph, so it only comes on once a photograph is actually
            // there. Over the drawn shoreline it just dims the drawing.
            .listener(new RequestListener<Drawable>() {
                @Override
                public boolean onLoadFailed(GlideException e, Object model,
                        Target<Drawable> target, boolean isFirstResource) {
                    heroScrim.setVisibility(View.GONE);
                    return false;
                }

                @Override
                public boolean onResourceReady(Drawable resource, Object model,
                        Target<Drawable> target, DataSource source, boolean isFirstResource) {
                    heroScrim.setVisibility(View.VISIBLE);
                    return false;
                }
            })
            .into(heroImage);
    }

    /** The camera button on the hero, and the sheet of choices behind it. */
    private void bindPhotoButton() {
        ImageView button = findViewById(R.id.detailPhotoButton);
        boolean hasPhoto = photoStore.hasPhoto(beach.getId());
        button.setContentDescription(
            getString(hasPhoto ? R.string.photo_change : R.string.photo_add));

        button.setOnClickListener(v -> BeachPhotoSheet.show(this, beach.getName(), hasPhoto,
            new BeachPhotoSheet.Choice() {
                @Override
                public void onTakePhoto() {
                    takePhoto();
                }

                @Override
                public void onChoosePhoto() {
                    photoPicker.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build());
                }

                @Override
                public void onRemovePhoto() {
                    photoStore.remove(beach.getId());
                    bindHeroPhoto();
                    bindPhotoButton();
                }
            }));
    }

    /**
     * Both ways in from another app. Registered in {@code onCreate} because
     * that is the contract: a launcher created any later than the activity
     * being started cannot be delivered a result.
     */
    private void registerPhotoLaunchers() {
        // The system photo picker: it hands back one image and nothing else, so
        // it needs no storage permission and cannot see the rest of the library.
        photoPicker = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(),
            uri -> {
                if (uri != null) submitPhoto(uri, null);
            });

        photoCamera = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            taken -> {
                File capture = pendingCapture;
                pendingCapture = null;
                if (capture == null) return;
                if (Boolean.TRUE.equals(taken)) {
                    submitPhoto(Uri.fromFile(capture), capture);
                } else {
                    // Backed out of the camera: the file it was given is empty
                    // or half-written, and nothing will come back for it.
                    capture.delete();
                }
            });
    }

    /**
     * Hands the camera app a file of ours to write into. No CAMERA permission
     * is declared or asked for — this starts whichever camera app the phone
     * already has and takes the result, rather than driving the sensor itself.
     */
    private void takePhoto() {
        File capture = null;
        try {
            File dir = new File(getCacheDir(), "camera");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                throw new IOException("Could not create " + dir);
            }
            capture = File.createTempFile("capture-", ".jpg", dir);
            Uri target = FileProvider.getUriForFile(
                this, getPackageName() + ".fileprovider", capture);
            pendingCapture = capture;
            photoCamera.launch(target);
        } catch (ActivityNotFoundException e) {
            pendingCapture = null;
            if (capture != null) capture.delete();
            Toast.makeText(this, R.string.photo_no_camera, Toast.LENGTH_SHORT).show();
        } catch (IOException | IllegalArgumentException e) {
            pendingCapture = null;
            if (capture != null) capture.delete();
            Toast.makeText(this, R.string.photo_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * @param cleanup a scratch file to delete once the photo is safely stored;
     *                null when the image came from the picker and is not ours
     *                to delete.
     */
    private void submitPhoto(Uri source, @Nullable File cleanup) {
        photoStore.submit(beach.getId(), source, new BeachPhotoStore.SaveCallback() {
            @Override
            public void onSaved(File photo) {
                if (cleanup != null) cleanup.delete();
                // The import runs off the main thread, so this screen may be
                // gone by the time it lands; Glide refuses a destroyed
                // activity. The photo is stored either way and will be there
                // the next time the beach is opened.
                if (isFinishing() || isDestroyed()) return;
                bindHeroPhoto();
                bindPhotoButton();
            }

            @Override
            public void onFailed(Exception e) {
                if (cleanup != null) cleanup.delete();
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(BeachDetailActivity.this,
                    R.string.photo_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (pendingCapture != null) {
            outState.putString(STATE_PENDING_CAPTURE, pendingCapture.getAbsolutePath());
        }
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
        // Colour comes from Widget.App.Button.Secondary; saved or not, it is
        // the same action, and the filled bookmark is what says which.
    }

    /** "Lifeguard tower nearby" under the beach name. */
    private void bindMeta() {
        boolean guarded = recordedTower() != null || !mappedTowers.isEmpty();
        String guardText = getString(guarded ? R.string.guarded_yes : R.string.guarded_no);
        ((TextView) findViewById(R.id.detailBeachMeta)).setText(guardText);
    }

    private void bindLifeguards() {
        TextView lifeguards = findViewById(R.id.detailLifeguards);
        List<HelpLocation> help = beach.getNearestHelp();
        HelpLocation recorded = recordedTower();

        if (recorded != null) {
            lifeguards.setText(getString(R.string.lifeguards_nearest, recorded.getName()));
        } else if (!mappedTowers.isEmpty()) {
            // OpenStreetMap towers often carry no name, or only a number, so
            // they are counted rather than named; the map shows where they are.
            lifeguards.setText(getResources().getQuantityString(
                R.plurals.lifeguards_mapped, mappedTowers.size(), mappedTowers.size()));
        } else if (!help.isEmpty()) {
            lifeguards.setText(getString(R.string.lifeguards_nearest, help.get(0).getName()));
        } else {
            lifeguards.setText(R.string.lifeguards_none);
        }
    }

    /** The first lifeguard tower in the app's own records, if it has one. */
    @Nullable
    private HelpLocation recordedTower() {
        return beach.getNearestHelp().stream()
            .filter(h -> h.getType() == HelpType.LIFEGUARD_TOWER)
            .findFirst()
            .orElse(null);
    }

    /**
     * The beach map: the beach, its lifeguard towers, and you.
     *
     * <p>You are wherever the phone last knew, then a fresh fix if location is
     * already allowed. This screen never asks for it — the home screen does —
     * and without one the map simply shows the beach without you on it. The
     * towers in the app's own records go on straight away; the ones
     * OpenStreetMap has join them when they land.
     */
    private void bindMap() {
        mapCard = findViewById(R.id.detailMapCard);
        mapCard.setBeach(beach);

        LastKnownLocation last = new LastKnownLocation(this);
        if (last.isKnown()) mapCard.setUserLocation(last.getLatitude(), last.getLongitude());
        new LocationService(this).getLastLocation(new LocationService.LocationUpdateListener() {
            @Override
            public void onLocationUpdated(double latitude, double longitude) {
                if (isFinishing() || isDestroyed()) return;
                mapCard.setUserLocation(latitude, longitude);
            }

            @Override
            public void onLocationError(String error) {
                // Not allowed, switched off, or no fix yet.
            }
        });

        new LifeguardClient(this).getTowers(beach.getId(), beach.getLatitude(), beach.getLongitude(),
            towers -> runOnUiThread(() -> {
                if (towers == null || isFinishing() || isDestroyed()) return;
                mapCard.addTowers(towers);
                mappedTowers = new ArrayList<>(towers);
                bindMeta();
                bindLifeguards();
                // Cached towers land straight away, inside onCreate and before
                // the stores the scores read exist; onResume scores them then.
                if (rockStore != null) bindGoodFor();
            }));
    }

    /**
     * Renders one card per hazard: the ones on record for this beach, plus the
     * ones today's readings imply. Reading only the recorded list left this
     * section claiming "no major hazards" under a wave card calling the surf
     * hazardous, which is the one contradiction the screen must never show.
     * With genuinely nothing to report the section says so rather than leaving
     * an empty gap.
     */
    private void bindHazards() {
        hazardList.removeAllViews();
        List<Hazard> hazards = ConditionHazards.merged(this, beach);
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

    /**
     * The chip sits on the photo's dark scrim in both themes, so it takes the
     * bright version of each safety colour rather than the light-mode text one.
     */
    private static int onHero(int colorRes) {
        if (colorRes == R.color.safe) return R.color.safe_on_hero;
        if (colorRes == R.color.caution) return R.color.caution_on_hero;
        if (colorRes == R.color.danger) return R.color.danger_on_hero;
        return colorRes;
    }

    /** Colours the chip over the photo with the beach's swim verdict. */
    private void bindVerdict() {
        SafetyLevel level = SafetyLevel.evaluate(this, beach);
        int color = ContextCompat.getColor(this,
            level != null ? onHero(level.getColorRes()) : R.color.on_hero);

        verdictIcon.setImageResource(
            level != null ? level.getIconRes() : R.drawable.ic_warning_circle);
        ImageViewCompat.setImageTintList(verdictIcon, ColorStateList.valueOf(color));
        // With nothing to judge from, the chip says which kind of nothing: the
        // forecast still on its way, or a forecast that could not be had.
        // "Checking conditions" over a request that has already failed would
        // promise an answer that is not coming.
        if (level != null) {
            verdictText.setText(level.getVerdictRes());
        } else {
            verdictText.setText(weatherFetch == Fetch.FAILED
                ? R.string.verdict_unavailable : R.string.verdict_unknown);
        }
        verdictText.setTextColor(color);

        Drawable background = verdictChip.getBackground();
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

    /**
     * The next low and high from NOAA's predictions for this beach's station.
     * A lake names no station: there is no tide there to count down to.
     */
    private void fetchTides() {
        String station = beach.getTideStation();
        if (station == null) {
            bindNoTide(lowTideSlot, lowTideCountdown);
            bindNoTide(highTideSlot, highTideCountdown);
            return;
        }
        if (tideFetch == Fetch.ASKING) return;
        tideFetch = Fetch.ASKING;
        if (tideForecast == null) {
            lowTideSlot.loading();
            highTideSlot.loading();
            lowTideCountdown.setText(R.string.tide_checking);
            highTideCountdown.setText(R.string.tide_checking);
        }
        tideClient.getTideForecast(station, new TideClient.TideCallback() {
            @Override
            public void onSuccess(TideForecast forecast) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    tideFetch = Fetch.ANSWERED;
                    tideForecast = forecast;
                    updateTideDisplay(forecast);
                });
            }

            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    tideFetch = Fetch.FAILED;
                    // A forecast already in hand covers two days, so a failed
                    // refresh goes on counting down from it; only with nothing
                    // to fall back on does the tile say it is not available.
                    if (tideForecast != null) {
                        updateTideDisplay(tideForecast);
                    } else {
                        bindTide(null, null, lowTideSlot, lowTideCountdown);
                        bindTide(null, null, highTideSlot, highTideCountdown);
                    }
                });
            }
        });
    }

    private void bindNoTide(ReadingSlot time, TextView countdown) {
        time.show(R.string.placeholder_dashes);
        countdown.setText(R.string.tide_none);
    }

    private void updateTideDisplay(TideForecast forecast) {
        long now = System.currentTimeMillis();
        TimeZone zone = forecast.getZone();
        bindTide(forecast.getNextLow(now), zone, lowTideSlot, lowTideCountdown);
        bindTide(forecast.getNextHigh(now), zone, highTideSlot, highTideCountdown);
    }

    /** One tide cube: the clock time on top, how long until it underneath. */
    private void bindTide(@Nullable Tide tide, @Nullable TimeZone zone,
                          ReadingSlot time, TextView countdown) {
        if (tide == null) {
            time.show(R.string.placeholder_dashes);
            countdown.setText(R.string.tide_unavailable);
            return;
        }

        // The beach's clock, not the phone's - they differ on a trip - and the
        // reader's 12/24-hour preference.
        DateFormat format = android.text.format.DateFormat.getTimeFormat(this);
        if (zone != null) format.setTimeZone(zone);
        time.show(format.format(new Date(tide.getTimeMillis())));
        countdown.setText(formatCountdown(tide.getTimeMillis() - System.currentTimeMillis()));
    }

    /**
     * "in 3h 20m" under the time, so a tide at 5:04 tomorrow morning can't read
     * as one this morning.
     */
    private String formatCountdown(long millisAway) {
        long minutes = millisAway / 60000L;
        if (minutes <= 0) return getString(R.string.tide_now);
        if (minutes < 60) return getString(R.string.tide_in_minutes, minutes);
        return getString(R.string.tide_in_hours_minutes, minutes / 60, minutes % 60);
    }

    // ── Air, water and parking ────────────────────────────────────────────
    //
    // Three independent calls that each land when they land. None of them
    // blocks the screen, none of them fails it, and each one rebuilds the
    // activity scores on arrival because every score is a blend that may
    // include it. Everything here is free and keyless: Open-Meteo for the air
    // and the rainfall, OpenStreetMap for the parking.
    //
    // A refresh that comes back empty keeps whatever reading was already on
    // screen rather than blanking it; the card only says "not available" when
    // there was never anything to show.

    private void fetchAirQuality() {
        if (airFetch == Fetch.ASKING) return;
        airFetch = Fetch.ASKING;
        bindEnvironment();
        airClient.getAirQuality(beach.getLatitude(), beach.getLongitude(), result ->
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                airFetch = result == null ? Fetch.FAILED : Fetch.ANSWERED;
                if (result != null) {
                    air = result;
                    // Share it, so a smoky day reaches the Alerts tab and its
                    // badge rather than only the screen that fetched it.
                    LiveReadings.putAir(beach.getId(), result);
                }
                bindEnvironment();
                bindGoodFor();
                bindHazards();
            }));
    }

    /**
     * Rainfall in, advisory out. The advisory is worked out here rather than in
     * the client because it needs the beach as well as the weather — how close
     * this one sits to the Tijuana River mouth decides whether recent rain is
     * an inconvenience or a closure.
     */
    private void fetchWaterQuality() {
        if (waterFetch == Fetch.ASKING) return;
        waterFetch = Fetch.ASKING;
        bindEnvironment();
        rainClient.getRecentRain(beach.getLatitude(), beach.getLongitude(), dailyRainMm ->
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                WaterQuality evaluated = WaterQuality.evaluate(beach, dailyRainMm);
                // An empty series is the client's way of saying the call
                // failed; see WaterQuality#isInformative.
                waterFetch = evaluated.hasRainfall() ? Fetch.ANSWERED : Fetch.FAILED;
                if (evaluated.hasRainfall() || water == null || !water.hasRainfall()) {
                    water = evaluated;
                    LiveReadings.putWater(beach.getId(), evaluated);
                }
                bindEnvironment();
                bindGoodFor();
                bindHazards();
            }));
    }

    private void fetchParking() {
        if (parkingFetch == Fetch.ASKING) return;
        parkingFetch = Fetch.ASKING;
        bindEnvironment();
        parkingClient.getParking(beach.getId(), beach.getLatitude(), beach.getLongitude(),
            result -> runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                parkingFetch = result == null ? Fetch.FAILED : Fetch.ANSWERED;
                if (result != null) parking = result;
                bindEnvironment();
                bindGoodFor();
            }));
    }

    /**
     * The air, water and parking cards. They are on screen from the start, so
     * the page keeps its length as they fill: each shows its reading once it
     * has landed, a loader where the reading will go until then, and says
     * plainly when it could not be had.
     */
    private void bindEnvironment() {
        bindAirCard();
        bindWaterCard();
        bindParkingCard();
    }

    private void bindAirCard() {
        findViewById(R.id.detailAqiCard).setVisibility(View.VISIBLE);
        TextView value = findViewById(R.id.detailAqiValue);
        View loader = findViewById(R.id.detailAqiLoader);
        TextView band = findViewById(R.id.detailAqiBand);
        TextView advice = findViewById(R.id.detailAqiAdvice);

        if (air != null) {
            value.setText(getString(R.string.aqi_value, air.getUsAqi()));
            value.setTextColor(ContextCompat.getColor(this, air.getColorRes()));
            reveal(value, loader);
            band.setText(air.getLabelRes());
            band.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            advice.setText(air.getAdviceRes());
            advice.setVisibility(View.VISIBLE);
            return;
        }
        boolean failed = airFetch == Fetch.FAILED;
        value.setVisibility(View.GONE);
        loader.setVisibility(failed ? View.GONE : View.VISIBLE);
        band.setText(failed ? R.string.aqi_unavailable : R.string.aqi_loading);
        band.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        advice.setVisibility(View.GONE);
    }

    private void bindWaterCard() {
        findViewById(R.id.detailWaterCard).setVisibility(View.VISIBLE);
        TextView status = findViewById(R.id.detailWaterStatus);
        View loader = findViewById(R.id.detailWaterLoader);
        TextView body = findViewById(R.id.detailWaterBody);

        int color;
        if (water != null && water.isInformative()) {
            color = ContextCompat.getColor(this, water.getStatus().colorRes);
            status.setText(water.getStatus().labelRes);
            reveal(status, loader);
            body.setText(water.getStatus().bodyRes);
        } else if (waterFetch == Fetch.FAILED) {
            // Not "Likely clean": nobody checked whether it rained.
            color = ContextCompat.getColor(this, R.color.text_secondary);
            status.setText(R.string.water_unknown);
            loader.setVisibility(View.GONE);
            status.setVisibility(View.VISIBLE);
            body.setText(R.string.water_unknown_body);
        } else {
            color = ContextCompat.getColor(this, R.color.text_secondary);
            status.setVisibility(View.GONE);
            loader.setVisibility(View.VISIBLE);
            body.setText(R.string.water_loading);
        }
        status.setTextColor(color);
        ImageViewCompat.setImageTintList(
            findViewById(R.id.detailWaterIcon), ColorStateList.valueOf(color));
    }

    private void bindParkingCard() {
        findViewById(R.id.detailParkingCard).setVisibility(View.VISIBLE);
        View loader = findViewById(R.id.detailParkingLoader);
        TextView summary = findViewById(R.id.detailParkingSummary);
        TextView detail = findViewById(R.id.detailParkingDetail);
        View caveat = findViewById(R.id.detailParkingCaveat);

        boolean known = parking != null;
        boolean failed = !known && parkingFetch == Fetch.FAILED;
        loader.setVisibility(known || failed ? View.GONE : View.VISIBLE);
        detail.setVisibility(known ? View.VISIBLE : View.GONE);
        caveat.setVisibility(known ? View.VISIBLE : View.GONE);
        summary.setTextColor(ContextCompat.getColor(this,
            known ? R.color.text_primary : R.color.text_secondary));

        if (!known) {
            summary.setText(failed ? R.string.parking_unavailable : R.string.parking_loading);
            return;
        }
        summary.setText(parking.getSummaryRes());
        detail.setText(getString(R.string.parking_detail,
            getResources().getQuantityString(R.plurals.parking_lots,
                parking.getLots(), parking.getLots()),
            getResources().getQuantityString(R.plurals.parking_streets,
                parking.getStreetSide(), parking.getStreetSide())));
    }

    /** A card's reading takes the loader's place, fading in if the loader was up. */
    private static void reveal(View value, View loader) {
        if (loader.getVisibility() != View.VISIBLE) {
            value.setVisibility(View.VISIBLE);
            return;
        }
        loader.setVisibility(View.GONE);
        value.setAlpha(0f);
        value.setVisibility(View.VISIBLE);
        value.animate().alpha(1f).setDuration(220).start();
    }

    /** Marks the "still scoring" row, so a rebind leaves the one already rolling. */
    private static final String LOADING_ROW = "loading";

    /**
     * The four activity scores.
     *
     * <p>Held back until there is a forecast to blend: scoring a beach on
     * parking alone would produce a confident-looking number about nothing.
     * Until it lands the section holds its place with a loader, and it only
     * steps aside when the forecast could not be had at all.
     */
    private void bindGoodFor() {
        View section = findViewById(R.id.detailGoodForSection);
        ViewGroup list = findViewById(R.id.detailActivityList);
        LayoutInflater inflater = LayoutInflater.from(this);

        if (beach.getConditions() == null) {
            if (weatherFetch == Fetch.FAILED) {
                section.setVisibility(View.GONE);
                return;
            }
            section.setVisibility(View.VISIBLE);
            if (list.getChildCount() == 1 && LOADING_ROW.equals(list.getChildAt(0).getTag())) {
                return;
            }
            list.removeAllViews();
            View row = inflater.inflate(R.layout.item_loading_row, list, false);
            row.setTag(LOADING_ROW);
            ((TextView) row.findViewById(R.id.loadingRowText)).setText(R.string.good_for_loading);
            list.addView(row);
            return;
        }
        section.setVisibility(View.VISIBLE);

        list.removeAllViews();
        for (ActivityFit fit : ActivityScorer.scoreAll(beach, scorerInputs())) {
            list.addView(buildActivityRow(inflater, list, fit));
        }
    }

    /** Everything the scorer is allowed to look at, as this screen knows it. */
    private ActivityScorer.Inputs scorerInputs() {
        ActivityScorer.Inputs in = new ActivityScorer.Inputs();
        in.conditions = beach.getConditions();
        in.air = air;
        in.water = water;
        in.parking = parking;
        in.rockRating = combined.rocks(beach.getId()).value;
        in.gullRating = combined.gulls(beach.getId()).value;
        in.guarded = ActivityScorer.guardedState(beach, mappedTowers);
        in.worstHazard = ActivityScorer.worstOf(ConditionHazards.merged(this, beach));
        return in;
    }

    private View buildActivityRow(LayoutInflater inflater, ViewGroup parent, ActivityFit fit) {
        View row = inflater.inflate(R.layout.item_activity_fit, parent, false);
        Activity activity = fit.getActivity();
        int color = ContextCompat.getColor(this, fit.getColorRes());

        ImageView icon = row.findViewById(R.id.fitIcon);
        icon.setImageResource(activity.getIconRes());
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(color));

        ((TextView) row.findViewById(R.id.fitActivity)).setText(activity.getLabelRes());

        TextView verdict = row.findViewById(R.id.fitVerdict);
        verdict.setText(fit.getVerdictRes());
        verdict.setTextColor(color);
        ((TextView) row.findViewById(R.id.fitScore))
            .setText(getString(R.string.fit_out_of, fit.getScore()));

        ((TextView) row.findViewById(R.id.fitReason)).setText(reasonFor(fit));
        return row;
    }

    /**
     * The line under an activity's name: what is holding it back, or what is
     * carrying it when nothing is. A score built on too few readings says so
     * instead, because "held back by wind" implies the rest was checked.
     */
    private String reasonFor(ActivityFit fit) {
        if (!fit.isConfident()) {
            return getString(R.string.fit_thin);
        }
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

    private void fetchRealTimeData() {
        if (weatherFetch == Fetch.ASKING) return;
        boolean wasFailed = weatherFetch == Fetch.FAILED;
        weatherFetch = Fetch.ASKING;
        if (wasFailed) {
            // Asking again after "couldn't load", by a tap or on the way back
            // in: the note goes, and the loaders come back while it asks.
            conditionsError.setVisibility(View.GONE);
            if (beach.getConditions() == null) showConditionsLoading();
            bindVerdict();
            bindGoodFor();
        }
        weatherClient.getBeachConditions(beach.getLatitude(), beach.getLongitude(), new WeatherClient.WeatherCallback() {
            @Override
            public void onSuccess(BeachConditions conditions) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    weatherFetch = Fetch.ANSWERED;
                    conditionsError.setVisibility(View.GONE);
                    // Re-judge with live numbers in hand. The hazard list is
                    // rebuilt too: it is partly derived from these readings,
                    // and leaving it on the empty state it was built with is
                    // what left "no major hazards" sitting under six-foot surf.
                    beach = beach.withConditions(conditions);
                    // Hand the reading back to the shared list so the Alerts
                    // tab and its badge see it too, rather than this screen
                    // being the only place that knows.
                    BeachRepository.getInstance()
                        .updateConditions(beach.getId(), conditions);
                    updateConditionsDisplay(conditions);
                    bindVerdict();
                    bindHazards();
                    // The scores are a blend that leans on these readings, so
                    // they cannot be drawn until the forecast is in.
                    bindGoodFor();
                });
            }

            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    weatherFetch = Fetch.FAILED;
                    showConditionsFailed();
                });
            }
        });
    }

    /** Every tile waiting on the forecast shows its loader, and the sea settles at rest. */
    private void showConditionsLoading() {
        uvSlot.loading();
        windSlot.loading();
        airTempSlot.loading();
        waterTempSlot.loading();
        visibilitySlot.loading();
        waveCard.setLoading();
    }

    /**
     * The forecast could not be had. With nothing on screen yet, the tiles go
     * to dashes and the wave card to its no-reading state; an older reading
     * that is already showing stays. Either way the note under the heading
     * says what happened, and tapping it asks again.
     */
    private void showConditionsFailed() {
        boolean nothingShown = beach.getConditions() == null;
        if (nothingShown) {
            String none = getString(R.string.placeholder_dashes);
            uvSlot.show(none);
            windSlot.show(none);
            airTempSlot.show(none);
            waterTempSlot.show(none);
            visibilitySlot.show(none);
            waveCard.setReading(null);
        }
        conditionsError.setText(nothingShown
            ? R.string.conditions_error_none : R.string.conditions_error_stale);
        conditionsError.setVisibility(View.VISIBLE);
        bindVerdict();
        bindGoodFor();
    }

    /** The note's tap: asks again for the forecast, and anything else that failed with it. */
    private void retryConditions() {
        fetchRealTimeData();
        if (tideFetch == Fetch.FAILED) fetchTides();
        if (airFetch == Fetch.FAILED) fetchAirQuality();
        if (waterFetch == Fetch.FAILED) fetchWaterQuality();
        if (parkingFetch == Fetch.FAILED) fetchParking();
    }

    private void updateConditionsDisplay(BeachConditions conditions) {
        ImageView airTempIcon = findViewById(R.id.detailAirTempIcon);

        if (conditions.getAirTemperature() != null) {
            airTempSlot.show(UnitConverter.formatTemperature(this, conditions.getAirTemperature()));

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
            airTempSlot.show(R.string.placeholder_dashes);
        }

        ImageView waterTempIcon = findViewById(R.id.detailWaterTempIcon);

        if (conditions.getWaterTemperature() != null) {
            double wTemp = conditions.getWaterTemperature();
            waterTempSlot.show(UnitConverter.formatTemperature(this, wTemp));

            // Same thermometer as the air tile, on the sea's own scale:
            // 82°F is very warm water but only warm air.
            int iconRes;
            int color;
            if (wTemp < 60) {
                iconRes = R.drawable.ic_thermometer_cold;
                color = ContextCompat.getColor(this, R.color.icon_wind);
            } else if (wTemp < 72) {
                iconRes = R.drawable.ic_thermometer_warm;
                color = ContextCompat.getColor(this, R.color.icon_temp);
            } else if (wTemp < 82) {
                iconRes = R.drawable.ic_thermometer_warm;
                color = ContextCompat.getColor(this, R.color.safe);
            } else {
                iconRes = R.drawable.ic_thermometer_hot;
                color = ContextCompat.getColor(this, R.color.icon_temp);
            }

            waterTempIcon.setImageResource(iconRes);
            ImageViewCompat.setImageTintList(waterTempIcon, ColorStateList.valueOf(color));
        } else {
            waterTempSlot.show(R.string.placeholder_dashes);
        }

        waveCard.setReading(conditions.getWaveHeight());
        uvSlot.show(conditions.getFormattedUvIndex(this));
        visibilitySlot.show(UnitConverter.formatVisibility(this, conditions.getVisibility()));
        windSlot.show(UnitConverter.formatWindSpeed(this, conditions.getWindSpeed()));
    }
}
