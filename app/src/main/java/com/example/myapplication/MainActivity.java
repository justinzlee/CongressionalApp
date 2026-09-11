package com.example.myapplication;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.res.ColorStateList;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.myapplication.ai.SafetyNote;
import com.example.myapplication.data.BeachRepository;
import com.example.myapplication.data.LastKnownLocation;
import com.example.myapplication.location.LocationService;
import com.example.myapplication.model.Beach;
import com.example.myapplication.model.BeachConditions;
import com.example.myapplication.model.SafetyLevel;
import com.example.myapplication.util.EdgeToEdge;
import com.example.myapplication.util.UnitConverter;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {
    /** Whether the welcome intro has run since the app process started. */
    private static boolean hasPlayedIntro = false;

    /**
     * How many beaches the list will draw at once.
     *
     * <p>The list sits inside the page's scroll view with its own scrolling
     * turned off, so it lays out every row it is given rather than recycling
     * them — fine for a screenful, expensive once the dataset covers every
     * coast. The list is distance-sorted, so this keeps the nearest beaches and
     * drops the far tail, which only "All" can reach anyway: no radius setting
     * matches this many beaches, and a search that does was never going to be
     * read to the end.
     */
    private static final int MAX_LISTED_BEACHES = 60;

    private BeachRepository repository;
    private LocationService locationService;
    private BeachAdapter adapter;
    private RecyclerView beachRecyclerView;
    
    private EditText searchEditText;
    private AutoCompleteTextView citySearchEditText;
    private MaterialButtonToggleGroup radiusToggleGroup;
    private Button citySearchButton;
    private View emptyState;
    private TextView beachCountBadge;

    // Header + verdict card, the design's answer to "can I swim right now?"
    private TextView headerKicker, headerTitle;
    private View headerKickerRow;
    private MaterialCardView verdictCard;
    private View verdictBand;
    private ImageView verdictIcon;
    private TextView verdictText, verdictMeta, verdictNote;
    private Button verdictDetailsButton;
    private View statGrid;
    private TextView statSurfValue, statUvValue, statWindValue, statTempValue;
    /** The beach the verdict card speaks for; what the SOS sheet reports. */
    private Beach nearestBeach;

    private double currentLat = 34.0522; // Default LA
    private double currentLon = -118.2437;
    private double realLat = 34.0522;
    private double realLon = -118.2437;
    private double currentRadiusMiles = Double.MAX_VALUE; // Default All
    private boolean hasLocation = false;
    private boolean hasRealLocation = false;
    private boolean isManualLocation = false;
    private boolean shouldScrollToTop = false;
    private final ExecutorService geocoderExecutor = Executors.newSingleThreadExecutor();

    private final String[] US_COASTAL_CITIES = {
        "San Diego, CA", "Carlsbad, CA", "Encinitas, CA", "Oceanside, CA", "La Jolla, CA",
        "Del Mar, CA", "Imperial Beach, CA", "Los Angeles, CA", "Santa Monica, CA", "Malibu, CA",
        "Venice, CA", "Manhattan Beach, CA", "Redondo Beach, CA", "Long Beach, CA", "Seal Beach, CA",
        "Huntington Beach, CA", "Newport Beach, CA", "Laguna Beach, CA", "Santa Barbara, CA",
        "Pismo Beach, CA", "Carmel-by-the-Sea, CA", "Monterey, CA", "Santa Cruz, CA",
        "Half Moon Bay, CA", "San Francisco, CA", "Stinson Beach, CA",
        "Miami, FL", "Miami Beach, FL", "Fort Lauderdale, FL", "Key West, FL", "Naples, FL",
        "Fort Myers Beach, FL", "Sanibel, FL", "Clearwater, FL", "Tampa, FL", "Sarasota, FL",
        "Siesta Key, FL", "Destin, FL", "Panama City Beach, FL", "Pensacola, FL",
        "Jacksonville, FL", "St. Augustine, FL", "Daytona Beach, FL", "Cocoa Beach, FL",
        "New York, NY", "Brooklyn, NY", "Queens, NY", "Coney Island, NY", "Long Beach, NY",
        "Montauk, NY", "Jones Beach, NY", "Rochester, NY",
        "Galveston, TX", "South Padre Island, TX", "Corpus Christi, TX", "Port Aransas, TX",
        "Grand Isle, LA", "Biloxi, MS", "Gulf Shores, AL", "Orange Beach, AL",
        "Atlantic City, NJ", "Cape May, NJ", "Asbury Park, NJ", "Ocean City, NJ",
        "Point Pleasant Beach, NJ", "Rehoboth Beach, DE", "Bethany Beach, DE",
        "Ocean City, MD", "Assateague Island, MD", "Virginia Beach, VA", "Sandbridge, VA",
        "Myrtle Beach, SC", "Hilton Head, SC", "Charleston, SC", "Folly Beach, SC",
        "Wilmington, NC", "Wrightsville Beach, NC", "Nags Head, NC", "Outer Banks, NC",
        "Cape Hatteras, NC", "Atlantic Beach, NC", "Savannah, GA", "Tybee Island, GA",
        "St. Simons Island, GA",
        "Madison, CT", "Narragansett, RI", "Westerly, RI", "Boston, MA", "Revere, MA",
        "Gloucester, MA", "Cape Cod, MA", "Orleans, MA", "Hampton Beach, NH",
        "Portland, ME", "Old Orchard Beach, ME", "Ogunquit, ME", "Phippsburg, ME",
        "Chicago, IL", "Milwaukee, WI", "Duluth, MN", "Grand Haven, MI", "Empire, MI",
        "Gary, IN", "Mentor, OH",
        "Cannon Beach, OR", "Seaside, OR", "Pacific City, OR", "Bandon, OR",
        "Ocean Shores, WA", "Seattle, WA", "Ruby Beach, WA", "Long Beach, WA",
        "Honolulu, HI", "Waikiki, HI", "Haleiwa, HI", "Lahaina, HI", "Koloa, HI", "Waimea, HI",
        "San Juan, PR", "Culebra, PR"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        EdgeToEdge.applyTopInset(findViewById(R.id.appBarLayout));
        EdgeToEdge.applyBottomInset(findViewById(R.id.mainScrollView));

        repository = BeachRepository.getInstance();
        locationService = new LocationService(this);

        searchEditText = findViewById(R.id.searchEditText);
        citySearchEditText = findViewById(R.id.citySearchEditText);
        citySearchButton = findViewById(R.id.citySearchButton);
        radiusToggleGroup = findViewById(R.id.radiusToggleGroup);
        emptyState = findViewById(R.id.emptyState);
        beachCountBadge = findViewById(R.id.beachCountBadge);

        headerKicker = findViewById(R.id.headerKicker);
        headerKickerRow = findViewById(R.id.headerKickerRow);
        headerTitle = findViewById(R.id.headerTitle);
        verdictCard = findViewById(R.id.verdictCard);
        verdictBand = findViewById(R.id.verdictBand);
        verdictIcon = findViewById(R.id.verdictIcon);
        verdictText = findViewById(R.id.verdictText);
        verdictMeta = findViewById(R.id.verdictMeta);
        verdictNote = findViewById(R.id.verdictNote);
        verdictDetailsButton = findViewById(R.id.verdictDetailsButton);
        statGrid = findViewById(R.id.statGrid);
        statSurfValue = findViewById(R.id.statSurfValue);
        statUvValue = findViewById(R.id.statUvValue);
        statWindValue = findViewById(R.id.statWindValue);
        statTempValue = findViewById(R.id.statTempValue);

        findViewById(R.id.myLocationButton).setOnClickListener(v -> useRealTimeLocation());
        findViewById(R.id.sosButton).setOnClickListener(v -> SosSheet.show(this, nearestBeach));

        EdgeToEdge.applyBottomInset(findViewById(R.id.tabBar));
        TabBar.setup(this, TabBar.Tab.NOW);

        findViewById(R.id.bestForCard).setOnClickListener(
            v -> startActivity(ActivityRankingActivity.intentFor(this)));

        beachRecyclerView = findViewById(R.id.beachRecyclerView);
        beachRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BeachAdapter(new ArrayList<>(), currentLat, currentLon, beach -> {
            Intent intent = new Intent(MainActivity.this, BeachDetailActivity.class);
            intent.putExtra(BeachDetailActivity.EXTRA_BEACH_ID, beach.getId());
            startActivity(intent);
        });
        beachRecyclerView.setAdapter(adapter);

        setupRadiusSpinner();
        setupSearch();
        setupCitySearch();
        updateRadiusButtonsEnabled();

        // Conditions are asked for in onResume, which runs on the way in too.
        updateList(); // Initial update

        setupWelcomeOverlay(savedInstanceState);
    }

    private void setupWelcomeOverlay(Bundle savedInstanceState) {
        View welcomeOverlay = findViewById(R.id.welcomeOverlay);
        View welcomeContent = findViewById(R.id.welcomeContent);
        WaveRevealOverlay revealView = findViewById(R.id.welcomeRevealView);

        // The intro belongs to launching the app, not to this activity being
        // constructed: coming back from another tab, a rotation, or a
        // low-memory recreation must not replay it. The flag is static so it
        // lasts the whole process and resets only on a genuine cold start.
        if (savedInstanceState != null || hasPlayedIntro) {
            welcomeOverlay.setVisibility(View.GONE);
            return;
        }
        hasPlayedIntro = true;

        revealView.setWaterColors(
            getColor(R.color.wave_deep),
            getColor(R.color.wave_shallow),
            getColor(R.color.wave_foam));

        // Hold on the logo/name for a beat, then reveal automatically - no
        // tap required.
        welcomeOverlay.postDelayed(() -> {
            // The water pulls back like an outgoing tide: the swell sweeps
            // down and off the bottom of the screen, trailing foam lines and
            // fading out to reveal the app underneath.
            ValueAnimator revealAnimator = ValueAnimator.ofFloat(0f, 1f);
            revealAnimator.setDuration(1400);
            revealAnimator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            revealAnimator.addUpdateListener(anim -> revealView.setProgress((float) anim.getAnimatedValue()));
            revealAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    welcomeOverlay.setVisibility(View.GONE);
                }
            });
            revealAnimator.start();

            // The logo fades as the water slides out from under it.
            welcomeContent.animate().alpha(0f).setDuration(500).start();
        }, 1200);
    }

    @Override
    protected void onStart() {
        super.onStart();
        requestLocation();
    }

    private void updateRadiusButtonsEnabled() {
        findViewById(R.id.radius5).setEnabled(hasLocation);
        findViewById(R.id.radius10).setEnabled(hasLocation);
        findViewById(R.id.radius25).setEnabled(hasLocation);
        findViewById(R.id.radius50).setEnabled(hasLocation);
        findViewById(R.id.radiusHint).setVisibility(hasLocation ? View.GONE : View.VISIBLE);
    }

    /** Clears any manual city search and snaps back to the device's location. */
    private void useRealTimeLocation() {
        isManualLocation = false;
        searchEditText.setText("");
        citySearchEditText.setText("");
        radiusToggleGroup.check(R.id.radiusAll);
        shouldScrollToTop = true;
        Toast.makeText(this, "Using real-time location", Toast.LENGTH_SHORT).show();
        requestLocation();
    }

    private void setupCitySearch() {
        ArrayAdapter<String> cityAdapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_dropdown_item_1line, US_COASTAL_CITIES);
        citySearchEditText.setAdapter(cityAdapter);

        citySearchButton.setOnClickListener(v -> {
            String cityName = citySearchEditText.getText().toString();
            if (!cityName.isEmpty()) {
                searchByCity(cityName);
            }
        });
    }

    private void searchByCity(String cityName) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        citySearchButton.setEnabled(false);
        geocoderExecutor.execute(() -> {
            List<Address> addresses = null;
            String errorMessage = null;
            try {
                addresses = geocoder.getFromLocationName(cityName, 1);
            } catch (IOException e) {
                errorMessage = "Network error";
            }

            List<Address> results = addresses;
            String error = errorMessage;
            runOnUiThread(() -> {
                citySearchButton.setEnabled(true);
                if (error != null) {
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
                } else if (results != null && !results.isEmpty()) {
                    Address address = results.get(0);
                    isManualLocation = true;
                    updateUserLocation(address.getLatitude(), address.getLongitude());
                    Toast.makeText(this, "Searching near: " + cityName, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Location not found", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void setupRadiusSpinner() {
        currentRadiusMiles = Double.MAX_VALUE; // Default All
        radiusToggleGroup.check(R.id.radiusAll);

        radiusToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;

            if (checkedId == R.id.radiusAll) {
                currentRadiusMiles = Double.MAX_VALUE;
                updateList();
                return;
            }

            if (checkedId == R.id.radius5) {
                currentRadiusMiles = 5.0;
            } else if (checkedId == R.id.radius10) {
                currentRadiusMiles = 10.0;
            } else if (checkedId == R.id.radius25) {
                currentRadiusMiles = 25.0;
            } else if (checkedId == R.id.radius50) {
                currentRadiusMiles = 50.0;
            }
            updateList();
        });
    }

    private void setupSearch() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateList(); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void requestLocation() {
        String[] permissions = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        };
        
        boolean hasFine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarse = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (!hasFine && !hasCoarse) {
            ActivityCompat.requestPermissions(this, permissions, 1);
        } else {
            fetchLocation();
        }
    }

    private void fetchLocation() {
        locationService.getLastLocation(new LocationService.LocationUpdateListener() {
            @Override
            public void onLocationUpdated(double latitude, double longitude) {
                onRealFix(latitude, longitude);
            }

            @Override
            public void onLocationError(String error) {}
        });

        locationService.startLocationUpdates(new LocationService.LocationUpdateListener() {
            @Override
            public void onLocationUpdated(double latitude, double longitude) {
                onRealFix(latitude, longitude);
            }

            @Override
            public void onLocationError(String error) {
                updateList();
                Toast.makeText(MainActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * A real position from the device, as opposed to the coastal city the user
     * may have picked to browse. Both the first fix and every later update land
     * here.
     *
     * <p>It is written down as well as held in fields: this is the only screen
     * that asks for location, and the Alerts tab and its badge need the same
     * answer to work out which beaches are within range of the user. The badge
     * is refreshed straight after, because a first fix can bring beaches into
     * alert range that were not in it a moment ago.
     */
    private void onRealFix(double latitude, double longitude) {
        realLat = latitude;
        realLon = longitude;
        hasRealLocation = true;
        new LastKnownLocation(this).remember(latitude, longitude);

        if (!isManualLocation) {
            updateUserLocation(latitude, longitude);
        } else {
            updateList();
        }
        TabBar.refreshAlertBadge(this);
    }

    private void updateUserLocation(double latitude, double longitude) {
        currentLat = latitude;
        currentLon = longitude;
        hasLocation = true;
        updateRadiusButtonsEnabled();
        updateList();
    }

    private void updateList() {
        String query = searchEditText.getText().toString();
        List<Beach> filteredBeaches = repository.searchBeaches(query).stream()
            .filter(b -> {
                if (currentRadiusMiles >= 1000.0) return true; // Disable filter for "All"
                double distanceMiles = UnitConverter.calculateDistance(currentLat, currentLon, b.getLatitude(), b.getLongitude());
                return distanceMiles <= currentRadiusMiles;
            })
            .sorted((b1, b2) -> {
                double d1 = UnitConverter.calculateDistance(currentLat, currentLon, b1.getLatitude(), b1.getLongitude());
                double d2 = UnitConverter.calculateDistance(currentLat, currentLon, b2.getLatitude(), b2.getLongitude());
                return Double.compare(d1, d2);
            })
            .limit(MAX_LISTED_BEACHES)
            .collect(Collectors.toList());

        boolean showDistance = isManualLocation ? hasRealLocation : hasLocation;
        double displayLat = isManualLocation ? realLat : currentLat;
        double displayLon = isManualLocation ? realLon : currentLon;

        adapter.setBeaches(filteredBeaches, showDistance, displayLat, displayLon);
        emptyState.setVisibility(filteredBeaches.isEmpty() ? View.VISIBLE : View.GONE);
        beachCountBadge.setText(getResources().getQuantityString(
                R.plurals.beach_count, filteredBeaches.size(), filteredBeaches.size()));

        // The list is distance-sorted, so the first entry is the beach the
        // verdict card speaks for.
        bindVerdict(filteredBeaches.isEmpty() ? null : filteredBeaches.get(0));

        if (shouldScrollToTop) {
            beachRecyclerView.scrollToPosition(0);
            shouldScrollToTop = false;
        }
    }

    /**
     * Fills the hero verdict card and the four stat tiles for the closest
     * beach, or hides them when there is nothing to report on.
     */
    private void bindVerdict(Beach beach) {
        nearestBeach = beach;
        if (beach == null) {
            verdictCard.setVisibility(View.GONE);
            statGrid.setVisibility(View.GONE);
            // Nothing to name yet, so the kicker would only repeat the title.
            headerKickerRow.setVisibility(View.GONE);
            headerTitle.setText(R.string.header_title_default);
            return;
        }

        verdictCard.setVisibility(View.VISIBLE);
        statGrid.setVisibility(View.VISIBLE);

        // "Near you" is a claim about where the phone is. Until a fix or a
        // searched city says where that is, the list is only sorted from a
        // default in Los Angeles, so the kicker stays unsaid.
        headerKickerRow.setVisibility(hasLocation ? View.VISIBLE : View.GONE);
        headerKicker.setText(isManualLocation
            ? R.string.header_kicker_searching : R.string.header_kicker_near_you);
        headerTitle.setText(beach.getName());

        SafetyLevel level = SafetyLevel.evaluate(beach);
        int color = ContextCompat.getColor(this,
            level != null ? level.getColorRes() : R.color.text_secondary);

        verdictBand.setBackgroundColor(color);
        verdictIcon.setImageResource(
            level != null ? level.getIconRes() : R.drawable.ic_warning_circle);
        ImageViewCompat.setImageTintList(verdictIcon, ColorStateList.valueOf(color));
        verdictText.setText(level != null ? level.getVerdictRes() : R.string.verdict_unknown);
        verdictText.setTextColor(color);
        verdictMeta.setText(updatedLine(beach));

        if (level == null) {
            verdictNote.setText(R.string.verdict_unknown_note);
        } else {
            verdictNote.setText(new SafetyNote().describe(this, beach, level));
        }

        verdictDetailsButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, BeachDetailActivity.class);
            intent.putExtra(BeachDetailActivity.EXTRA_BEACH_ID, beach.getId());
            startActivity(intent);
        });

        BeachConditions conditions = beach.getConditions();
        statSurfValue.setText(conditions == null ? "--"
            : UnitConverter.formatWaveHeight(this, conditions.getWaveHeight()));
        statUvValue.setText(conditions == null ? "--" : conditions.getFormattedUvIndex(this));
        statWindValue.setText(conditions == null ? "--"
            : UnitConverter.formatWindSpeed(this, conditions.getWindSpeed()));
        statTempValue.setText(conditions == null ? "--"
            : UnitConverter.formatTemperature(this, conditions.getAirTemperature()));
    }

    /**
     * "Santa Monica Pier · updated 12 min ago". Before any reading has landed
     * there is nothing to date, so it is only the name; the verdict beside it
     * already says conditions are still being checked.
     */
    private String updatedLine(Beach beach) {
        BeachConditions conditions = beach.getConditions();
        if (conditions == null) return beach.getName();
        long minutes = (System.currentTimeMillis() - conditions.getFetchedAtMillis()) / 60_000L;
        if (minutes < 1) return getString(R.string.verdict_updated_now, beach.getName());
        if (minutes < 60) return getString(R.string.verdict_updated_minutes, beach.getName(), minutes);
        return getString(R.string.verdict_updated_hours, beach.getName(), minutes / 60);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateRadiusButtonLabels();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        updateList();
        // Alerts may have been read since this screen was last in front.
        TabBar.refreshAlertBadge(this);

        // Tabs are brought back to the front rather than rebuilt, so asking
        // only in onCreate left this screen on the readings from whenever the
        // app first opened — hours old after an afternoon in the background,
        // under a line saying "updated just now". Within the repository's
        // freshness window this costs nothing.
        repository.refreshAllData(() -> runOnUiThread(() -> {
            updateList();
            // Some alerts are worked out from these readings, so the badge is
            // only right once they have landed.
            TabBar.refreshAlertBadge(this);
        }));
    }

    private void updateRadiusButtonLabels() {
        SharedPreferences prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE);
        boolean useMetric = prefs.getBoolean(SettingsActivity.KEY_USE_METRIC, false);

        setRadiusButtonLabel(R.id.radius5, 5.0, useMetric);
        setRadiusButtonLabel(R.id.radius10, 10.0, useMetric);
        setRadiusButtonLabel(R.id.radius25, 25.0, useMetric);
        setRadiusButtonLabel(R.id.radius50, 50.0, useMetric);
    }

    private void setRadiusButtonLabel(int buttonId, double miles, boolean useMetric) {
        long displayValue = useMetric ? Math.round(miles * 1.60934) : (long) miles;
        String unit = useMetric ? "km" : "mi";
        ((Button) findViewById(buttonId)).setText(displayValue + unit);
    }

    @Override
    protected void onStop() {
        super.onStop();
        locationService.stopLocationUpdates();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        geocoderExecutor.shutdown();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0) {
            boolean anyGranted = false;
            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) anyGranted = true;
            }
            if (anyGranted) fetchLocation();
        }
    }
}
