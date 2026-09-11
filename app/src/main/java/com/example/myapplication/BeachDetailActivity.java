package com.example.myapplication;

import android.content.ActivityNotFoundException;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
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
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.example.myapplication.ai.SafetyNote;
import com.example.myapplication.data.BeachPhotoStore;
import com.example.myapplication.data.BeachRepository;
import com.example.myapplication.data.LiveReadings;
import com.example.myapplication.data.RockRatingStore;
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
import com.example.myapplication.model.SafetyLevel;
import com.example.myapplication.model.Severity;
import com.example.myapplication.model.Sighting;
import com.example.myapplication.model.Tide;
import com.example.myapplication.model.WaterQuality;
import com.example.myapplication.model.TideForecast;
import com.example.myapplication.network.AirQualityClient;
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
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;

public class BeachDetailActivity extends AppCompatActivity {
    public static final String EXTRA_BEACH_ID = "extra_beach_id";

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
    private TextView lowTide, lowTideCountdown, highTide, highTideCountdown;

    private ImageView heroImage;
    private View heroScrim;
    private BeachPhotoStore photoStore;
    private ActivityResultLauncher<PickVisualMediaRequest> photoPicker;
    private ActivityResultLauncher<Uri> photoCamera;
    private File pendingCapture;

    private RockRatingStore rockStore;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_beach_detail);

        String beachId = getIntent().getStringExtra(EXTRA_BEACH_ID);
        BeachRepository repository = BeachRepository.getInstance();
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
        waveCard = findViewById(R.id.detailWaveCard);
        waveCard.setBeachName(beach.getName());
        lowTide = findViewById(R.id.detailLowTide);
        lowTideCountdown = findViewById(R.id.detailLowTideCountdown);
        highTide = findViewById(R.id.detailHighTide);
        highTideCountdown = findViewById(R.id.detailHighTideCountdown);

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

        rockStore = new RockRatingStore(this);
        sightingStore = new SightingStore(this);
        findViewById(R.id.detailRocksCard).setOnClickListener(
            v -> startActivity(RockRatingActivity.intentFor(this, beach.getId())));
        findViewById(R.id.detailSightingCta).setOnClickListener(
            v -> startActivity(SightingActivity.intentFor(this, beach.getId())));
        // The rows themselves are drawn in onResume, which also runs on the way
        // back from those two screens.

        if (beach.getConditions() != null) {
            updateConditionsDisplay(beach.getConditions());
        } else {
            // Settle the sea at its resting level while the forecast is still
            // in flight, rather than leaving the card blank until it lands.
            waveCard.setReading(null);
        }

        weatherClient = new WeatherClient();
        tideClient = new TideClient();
        airClient = new AirQualityClient();
        rainClient = new RainfallClient();
        parkingClient = new ParkingClient(this);
        // The readings themselves are asked for in onStart, which runs on the
        // way in and again whenever this screen comes back to the front.
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (beach == null) return;
        // Left open in the background, this screen used to come back showing
        // an hours-old reading as if it were current, beside a tide countdown
        // that had long since run out. The shared list's freshness window
        // keeps a quick trip away from refetching.
        long now = System.currentTimeMillis();
        if (now - fetchedAt >= BeachRepository.FRESH_FOR_MS) {
            fetchedAt = now;
            fetchRealTimeData();
            fetchTides();
            fetchAirQuality();
            fetchWaterQuality();
            fetchParking();
        }
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
        bindSightings();
        bindHazards();
        // A rocks rating filed on the way back moves two of the four scores:
        // shingle is a problem walking in and an advantage to fish over.
        bindGoodFor();
    }

    /**
     * The rocks-underfoot card: the rating if this beach has one, and an
     * invitation to give it one either way.
     */
    private void bindRocks() {
        int rating = rockStore.ratingFor(beach.getId());
        boolean rated = RockRating.isRated(rating);

        TextView value = findViewById(R.id.detailRocksValue);
        TextView body = findViewById(R.id.detailRocksBody);
        TextView adviceLine = findViewById(R.id.detailRocksAdvice);
        TextView when = findViewById(R.id.detailRocksWhen);
        ImageView photo = findViewById(R.id.detailRocksPhoto);

        value.setVisibility(rated ? View.VISIBLE : View.GONE);
        adviceLine.setVisibility(rated ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.detailRocksCta)).setText(
            rated ? R.string.rocks_rerate_cta : R.string.rocks_rate_cta);

        if (!rated) {
            body.setText(R.string.rocks_unrated_body);
            when.setVisibility(View.GONE);
            photo.setVisibility(View.GONE);
            return;
        }

        value.setText(getString(R.string.rocks_value, rating));
        body.setText(RockRating.labelRes(rating));
        adviceLine.setText(RockRating.adviceRes(rating));

        long ratedAt = rockStore.ratedAt(beach.getId());
        if (ratedAt > 0) {
            when.setVisibility(View.VISIBLE);
            when.setText(getString(R.string.rocks_rated_when,
                SightingSheet.relativeTime(this, ratedAt)));
        } else {
            when.setVisibility(View.GONE);
        }

        File evidence = rockStore.photoFor(beach.getId());
        if (evidence == null) {
            photo.setVisibility(View.GONE);
        } else {
            photo.setVisibility(View.VISIBLE);
            Glide.with(this).load(evidence).centerCrop().into(photo);
        }
    }

    /** The wildlife log for this beach, newest first. */
    private void bindSightings() {
        List<Sighting> sightings = sightingStore.forBeach(beach.getId());

        ViewGroup list = findViewById(R.id.detailSightingsList);
        list.removeAllViews();
        findViewById(R.id.detailSightingsEmpty)
            .setVisibility(sightings.isEmpty() ? View.VISIBLE : View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (Sighting sighting : sightings) {
            list.addView(buildSightingRow(inflater, list, sighting));
        }
    }

    private View buildSightingRow(LayoutInflater inflater, ViewGroup parent, Sighting sighting) {
        View row = inflater.inflate(R.layout.item_sighting, parent, false);

        boolean stranded = sighting.isStranded();
        int color = ContextCompat.getColor(this,
            stranded ? R.color.danger : R.color.text_secondary);

        ImageView icon = row.findViewById(R.id.sightingRowIcon);
        icon.setImageResource(sighting.getSpecies().getIconRes());
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(color));

        ((TextView) row.findViewById(R.id.sightingRowSpecies))
            .setText(sighting.getSpecies().getLabelRes());

        TextView tag = row.findViewById(R.id.sightingRowTag);
        tag.setText(stranded ? R.string.sighting_tag_stranded : R.string.sighting_tag_water);
        tag.setTextColor(color);
        Drawable tagBackground = tag.getBackground();
        if (tagBackground instanceof GradientDrawable) {
            ((GradientDrawable) tagBackground.mutate()).setColor(ContextCompat.getColor(this,
                stranded ? R.color.danger_tint : R.color.surface_alt));
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
        Drawable placeholder = BeachPlaceholder.forBeach(this, beach);
        Glide.with(this)
            .load(photoStore.imageSourceFor(beach))
            .centerCrop()
            .placeholder(placeholder)
            .fallback(placeholder)
            .error(placeholder)
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

    /**
     * The next low and high from NOAA's predictions for this beach's station.
     * A lake names no station: there is no tide there to count down to.
     */
    private void fetchTides() {
        String station = beach.getTideStation();
        if (station == null) {
            bindNoTide(lowTide, lowTideCountdown);
            bindNoTide(highTide, highTideCountdown);
            return;
        }
        tideClient.getTideForecast(station, new TideClient.TideCallback() {
            @Override
            public void onSuccess(TideForecast forecast) {
                runOnUiThread(() -> updateTideDisplay(forecast));
            }

            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> {
                    bindTide(null, null, lowTide, lowTideCountdown);
                    bindTide(null, null, highTide, highTideCountdown);
                });
            }
        });
    }

    private void bindNoTide(TextView time, TextView countdown) {
        time.setText(R.string.placeholder_dashes);
        countdown.setText(R.string.tide_none);
    }

    private void updateTideDisplay(TideForecast forecast) {
        long now = System.currentTimeMillis();
        TimeZone zone = forecast.getZone();
        bindTide(forecast.getNextLow(now), zone, lowTide, lowTideCountdown);
        bindTide(forecast.getNextHigh(now), zone, highTide, highTideCountdown);
    }

    /** One tide cube: the clock time on top, how long until it underneath. */
    private void bindTide(Tide tide, TimeZone zone, TextView time, TextView countdown) {
        if (tide == null) {
            time.setText(R.string.placeholder_dashes);
            countdown.setText(R.string.tide_unavailable);
            return;
        }

        // The beach's clock, not the phone's - they differ on a trip - and the
        // reader's 12/24-hour preference.
        DateFormat format = android.text.format.DateFormat.getTimeFormat(this);
        format.setTimeZone(zone);
        time.setText(format.format(new Date(tide.getTimeMillis())));
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

    private void fetchAirQuality() {
        airClient.getAirQuality(beach.getLatitude(), beach.getLongitude(), result ->
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                air = result;
                // Share it, so a smoky day reaches the Alerts tab and its
                // badge rather than only the screen that fetched it.
                LiveReadings.putAir(beach.getId(), result);
                bindAirWater();
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
        rainClient.getRecentRain(beach.getLatitude(), beach.getLongitude(), dailyRainMm ->
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                water = WaterQuality.evaluate(beach, dailyRainMm);
                LiveReadings.putWater(beach.getId(), water);
                bindAirWater();
                bindGoodFor();
                bindHazards();
            }));
    }

    private void fetchParking() {
        parkingClient.getParking(beach.getId(), beach.getLatitude(), beach.getLongitude(),
            result -> runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                parking = result;
                bindAirWater();
                bindGoodFor();
            }));
    }

    /** The three cards, each appearing only once its own reading has landed. */
    private void bindAirWater() {
        View section = findViewById(R.id.detailAirWaterSection);
        section.setVisibility(
            air == null && water == null && parking == null ? View.GONE : View.VISIBLE);

        View aqiCard = findViewById(R.id.detailAqiCard);
        if (air == null) {
            aqiCard.setVisibility(View.GONE);
        } else {
            aqiCard.setVisibility(View.VISIBLE);
            TextView value = findViewById(R.id.detailAqiValue);
            value.setText(getString(R.string.aqi_value, air.getUsAqi()));
            value.setTextColor(ContextCompat.getColor(this, air.getColorRes()));
            ((TextView) findViewById(R.id.detailAqiBand)).setText(air.getLabelRes());
            ((TextView) findViewById(R.id.detailAqiAdvice)).setText(air.getAdviceRes());
        }

        View waterCard = findViewById(R.id.detailWaterCard);
        if (water == null) {
            waterCard.setVisibility(View.GONE);
        } else {
            waterCard.setVisibility(View.VISIBLE);
            int color = ContextCompat.getColor(this, water.getStatus().colorRes);
            TextView status = findViewById(R.id.detailWaterStatus);
            status.setText(water.getStatus().labelRes);
            status.setTextColor(color);
            ImageViewCompat.setImageTintList(
                findViewById(R.id.detailWaterIcon), ColorStateList.valueOf(color));
            ((TextView) findViewById(R.id.detailWaterBody)).setText(water.getStatus().bodyRes);
        }

        View parkingCard = findViewById(R.id.detailParkingCard);
        if (parking == null) {
            parkingCard.setVisibility(View.GONE);
        } else {
            parkingCard.setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.detailParkingSummary))
                .setText(parking.getSummaryRes());
            ((TextView) findViewById(R.id.detailParkingDetail)).setText(
                getString(R.string.parking_detail,
                    parking.getLots(), parking.getStreetSide()));
        }
    }

    /**
     * The four activity scores.
     *
     * <p>Held back until there is a forecast to blend: scoring a beach on
     * parking alone would produce a confident-looking number about nothing.
     */
    private void bindGoodFor() {
        View section = findViewById(R.id.detailGoodForSection);
        if (beach.getConditions() == null) {
            section.setVisibility(View.GONE);
            return;
        }
        section.setVisibility(View.VISIBLE);

        ViewGroup list = findViewById(R.id.detailActivityList);
        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
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
        in.rockRating = rockStore.ratingFor(beach.getId());
        in.guarded = guardedState(beach);
        in.worstHazard = worstHazardSeverity();
        return in;
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

    /** The most severe hazard currently on this beach, or null if there are none. */
    @Nullable
    private Severity worstHazardSeverity() {
        Severity worst = null;
        for (Hazard hazard : ConditionHazards.merged(this, beach)) {
            if (worst == null || hazard.getSeverity().ordinal() > worst.ordinal()) {
                worst = hazard.getSeverity();
            }
        }
        return worst;
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
        weatherClient.getBeachConditions(beach.getLatitude(), beach.getLongitude(), new WeatherClient.WeatherCallback() {
            @Override
            public void onSuccess(BeachConditions conditions) {
                runOnUiThread(() -> {
                    updateConditionsDisplay(conditions);
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
                    bindVerdict();
                    bindHazards();
                    // The scores are a blend that leans on these readings, so
                    // they cannot be drawn until the forecast is in.
                    bindGoodFor();
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

        waveCard.setReading(conditions.getWaveHeight());
        ((TextView) findViewById(R.id.detailUvIndex))
            .setText(conditions.getFormattedUvIndex(this));
        ((TextView) findViewById(R.id.detailVisibility))
            .setText(UnitConverter.formatVisibility(this, conditions.getVisibility()));
        ((TextView) findViewById(R.id.detailWindSpeed))
            .setText(UnitConverter.formatWindSpeed(this, conditions.getWindSpeed()));
    }
}
