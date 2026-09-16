package com.example.myapplication;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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

    /**
     * How far the phone has to move before a new fix is worth acting on:
     * about fifty metres, under half the smallest step a distance is shown in
     * (a tenth of a kilometre), and well over the few metres a phone lying
     * still wanders by. See {@link #onRealFix}.
     */
    private static final double REFIX_MILES = 0.03;

    private BeachRepository repository;
    private LocationService locationService;
    private BeachAdapter adapter;
    private RecyclerView beachRecyclerView;
    
    private EditText searchEditText;
    private View searchClearButton;
    private AutoCompleteTextView citySearchEditText;
    private View cityField;
    private ImageView cityCaret;
    private View citySearchButton, citySearchPulse;
    private TextView citySearchLabel;
    /** The five radius chips, steps first and "All" last. */
    private final TextView[] radiusChips = new TextView[5];
    private View emptyState;
    private TextView beachCountBadge;

    // Header + verdict card, the design's answer to "can I swim right now?"
    private TextView headerKicker, headerTitle;
    private View headerKickerRow;
    private MaterialCardView verdictCard;
    /** The safety colour, as a dot in a ring of its own tint. */
    private View verdictDot;
    private TextView verdictText, verdictMeta, verdictNote;
    private View verdictDetailsButton;
    private TextView statSurfValue, statUvValue, statWindValue, statTempValue;
    /** The ring swelling out of the pin on the search button. */
    private ValueAnimator pulseAnimator;
    /** The beach the verdict card speaks for; what the SOS sheet reports. */
    private Beach nearestBeach;

    private double currentLat = 34.0522; // Default LA
    private double currentLon = -118.2437;
    private double realLat = 34.0522;
    private double realLon = -118.2437;
    /**
     * The four search radii, as round numbers in each system rather than one
     * converted into the other: five miles across reads as "8 km", which is
     * nobody's idea of a search radius. Whichever number is on the chip is
     * what the list cuts at, so the two can never disagree.
     */
    private static final int[] RADIUS_STEPS_MILES = {5, 10, 25, 50};
    private static final int[] RADIUS_STEPS_KM = {10, 25, 50, 100};
    /**
     * The chips in the tray, in order: the four steps above, then "All". The
     * last one is no step at all, so it is the only chip that works without a
     * position to measure from.
     */
    private static final int[] RADIUS_CHIP_IDS = {
        R.id.radiusStep1, R.id.radiusStep2, R.id.radiusStep3, R.id.radiusStep4, R.id.radiusAll};
    /** The "All" chip, which is no step at all. */
    private static final int RADIUS_ALL = -1;

    private int radiusStep = RADIUS_ALL;
    private double currentRadiusMiles = Double.MAX_VALUE;
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
        "Coronado, CA", "Solana Beach, CA", "Cardiff-by-the-Sea, CA", "San Clemente, CA",
        "Dana Point, CA", "Corona del Mar, CA", "Sunset Beach, CA", "Hermosa Beach, CA",
        "Torrance, CA", "El Segundo, CA", "San Pedro, CA", "Avalon, CA",
        "Pacific Palisades, CA", "Ventura, CA", "Oxnard, CA", "Carpinteria, CA", "Goleta, CA",
        "Avila Beach, CA", "Morro Bay, CA", "Cambria, CA", "Big Sur, CA", "Pacific Grove, CA",
        "Marina, CA", "Capitola, CA", "Pacifica, CA", "Montara, CA", "Point Reyes Station, CA",
        "Bodega Bay, CA", "Fort Bragg, CA", "Trinidad, CA", "Crescent City, CA",
        "Miami, FL", "Miami Beach, FL", "Fort Lauderdale, FL", "Key West, FL", "Naples, FL",
        "Fort Myers Beach, FL", "Sanibel, FL", "Clearwater, FL", "Tampa, FL", "Sarasota, FL",
        "Siesta Key, FL", "Destin, FL", "Panama City Beach, FL", "Pensacola, FL",
        "Jacksonville, FL", "St. Augustine, FL", "Daytona Beach, FL", "Cocoa Beach, FL",
        "Vero Beach, FL",
        "New York, NY", "Brooklyn, NY", "Queens, NY", "Coney Island, NY", "Long Beach, NY",
        "Montauk, NY", "Jones Beach, NY", "Rochester, NY",
        "Galveston, TX", "South Padre Island, TX", "Corpus Christi, TX", "Port Aransas, TX",
        "Grand Isle, LA", "Biloxi, MS", "Gulf Shores, AL", "Orange Beach, AL",
        "Atlantic City, NJ", "Cape May, NJ", "Asbury Park, NJ", "Ocean City, NJ",
        "Point Pleasant Beach, NJ", "Rehoboth Beach, DE", "Bethany Beach, DE",
        "Ocean City, MD", "Assateague Island, MD", "Virginia Beach, VA", "Sandbridge, VA",
        "Myrtle Beach, SC", "Hilton Head, SC", "Charleston, SC", "Folly Beach, SC",
        "Wilmington, NC", "Wrightsville Beach, NC", "Nags Head, NC", "Outer Banks, NC",
        "Cape Hatteras, NC", "Atlantic Beach, NC", "Carolina Beach, NC", "Savannah, GA",
        "Tybee Island, GA", "St. Simons Island, GA",
        "Madison, CT", "Narragansett, RI", "Westerly, RI", "Boston, MA", "Revere, MA",
        "Gloucester, MA", "Ipswich, MA", "Cape Cod, MA", "Orleans, MA", "Hampton Beach, NH",
        "Portland, ME", "Old Orchard Beach, ME", "Ogunquit, ME", "Phippsburg, ME",
        "Chicago, IL", "Milwaukee, WI", "Duluth, MN", "Grand Haven, MI", "Empire, MI",
        "Gary, IN", "Mentor, OH",
        "Cannon Beach, OR", "Seaside, OR", "Pacific City, OR", "Newport, OR", "Bandon, OR",
        "Ocean Shores, WA", "Seattle, WA", "Ruby Beach, WA", "La Push, WA", "Long Beach, WA",
        "Honolulu, HI", "Waikiki, HI", "Kailua, HI", "Haleiwa, HI", "Lahaina, HI", "Koloa, HI",
        "Waimea, HI",
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
        searchClearButton = findViewById(R.id.searchClearButton);
        citySearchEditText = findViewById(R.id.citySearchEditText);
        cityField = findViewById(R.id.cityField);
        cityCaret = findViewById(R.id.cityCaret);
        citySearchButton = findViewById(R.id.citySearchButton);
        citySearchLabel = findViewById(R.id.citySearchLabel);
        citySearchPulse = findViewById(R.id.citySearchPulse);
        for (int i = 0; i < RADIUS_CHIP_IDS.length; i++) {
            radiusChips[i] = findViewById(RADIUS_CHIP_IDS[i]);
        }
        emptyState = findViewById(R.id.emptyState);
        beachCountBadge = findViewById(R.id.beachCountBadge);

        headerKicker = findViewById(R.id.headerKicker);
        headerKickerRow = findViewById(R.id.headerKickerRow);
        headerTitle = findViewById(R.id.headerTitle);
        verdictCard = findViewById(R.id.verdictCard);
        verdictDot = findViewById(R.id.verdictDot);
        verdictText = findViewById(R.id.verdictText);
        verdictMeta = findViewById(R.id.verdictMeta);
        verdictNote = findViewById(R.id.verdictNote);
        verdictDetailsButton = findViewById(R.id.verdictDetailsButton);
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

        setupRadiusChips();
        setupSearch();
        setupCitySearch();
        updateRadiusChipsEnabled();

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
        startPulse();
    }

    /**
     * The ring swelling out of the pin on the search button: it grows and
     * fades away over the first two thirds of each cycle, then waits, so the
     * button breathes rather than throbs.
     *
     * <p>Run from onStart/onStop rather than started once, because an
     * animator left running behind another tab draws frames nobody is looking
     * at for as long as the app is open.
     */
    private void startPulse() {
        if (pulseAnimator != null) return;
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f);
        pulseAnimator.setDuration(2400);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setInterpolator(null); // the shape of the swell is in the maths below
        pulseAnimator.addUpdateListener(anim -> {
            float t = (float) anim.getAnimatedValue();
            float swell = Math.min(t / 0.7f, 1f);
            float scale = 0.85f + 0.65f * swell;
            citySearchPulse.setScaleX(scale);
            citySearchPulse.setScaleY(scale);
            citySearchPulse.setAlpha(0.5f * (1f - swell));
        });
        pulseAnimator.start();
    }

    private void stopPulse() {
        if (pulseAnimator == null) return;
        pulseAnimator.cancel();
        pulseAnimator = null;
        citySearchPulse.setAlpha(0f);
    }

    /**
     * Dims the four distance chips until there is a position to measure from.
     * "All" is never dimmed: it filters nothing, so it needs no position.
     */
    private void updateRadiusChipsEnabled() {
        for (int step = 0; step < radiusChips.length - 1; step++) {
            radiusChips[step].setEnabled(hasLocation);
        }
        findViewById(R.id.radiusHint).setVisibility(hasLocation ? View.GONE : View.VISIBLE);
    }

    /** Clears any manual city search and snaps back to the device's location. */
    private void useRealTimeLocation() {
        isManualLocation = false;
        searchEditText.setText("");
        citySearchEditText.setText("");
        selectRadius(RADIUS_ALL);
        shouldScrollToTop = true;
        Toast.makeText(this, R.string.search_using_location, Toast.LENGTH_SHORT).show();
        // Back to the fix already in hand straight away: the next one may be
        // too close to it to count as news (see onRealFix), which would leave
        // the list sorted around the searched city.
        if (hasRealLocation) {
            updateUserLocation(realLat, realLon);
        }
        requestLocation();
    }

    private void setupCitySearch() {
        citySearchEditText.setAdapter(new CityAdapter());
        // Typing opens the list too, without going through openCityList, so
        // the width is set as soon as the field has one.
        cityField.post(() -> citySearchEditText.setDropDownWidth(cityField.getWidth()));

        // The field reads as a picker, so tapping it anywhere has to open the
        // list - an empty box that only drops down once you have typed a
        // letter looks broken under a caret.
        citySearchEditText.setOnClickListener(v -> openCityList());
        citySearchEditText.setOnFocusChangeListener((v, focused) -> {
            if (focused) openCityList();
        });

        // The caret turns over while the list is down, the way the design's
        // does. There is no "dropdown closed" callback, so the dismiss
        // listener is where it turns back.
        citySearchEditText.setOnDismissListener(() -> turnCaret(false));
        citySearchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                turnCaret(citySearchEditText.isPopupShowing());
            }
        });

        citySearchButton.setOnClickListener(v -> {
            String cityName = citySearchEditText.getText().toString();
            if (!cityName.isEmpty()) {
                searchByCity(cityName);
            }
        });
    }

    private void openCityList() {
        // The list belongs to the field, so it is cut to the field's width.
        // MATCH_PARENT would spread it across the whole window instead, and
        // the width is only known once the card has been laid out.
        if (cityField.getWidth() > 0) {
            citySearchEditText.setDropDownWidth(cityField.getWidth());
        }
        citySearchEditText.showDropDown();
        turnCaret(true);
    }

    private void turnCaret(boolean open) {
        cityCaret.animate().rotation(open ? 180f : 0f).setDuration(200).start();
    }

    /**
     * The coastal-city list. The state is drawn on the right in its own colour
     * rather than trailing the name behind a comma, so a column of cities
     * reads as a column of names; the entries themselves stay "City, ST",
     * because that is the string the geocoder is handed and the string the
     * field shows once one is picked.
     */
    private final class CityAdapter extends ArrayAdapter<String> {
        CityAdapter() {
            super(MainActivity.this, R.layout.item_city, R.id.cityName, US_COASTAL_CITIES);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            View row = convertView != null ? convertView
                : LayoutInflater.from(getContext()).inflate(R.layout.item_city, parent, false);
            String entry = getItem(position);
            int comma = entry == null ? -1 : entry.lastIndexOf(", ");
            ((TextView) row.findViewById(R.id.cityName))
                .setText(comma < 0 ? entry : entry.substring(0, comma));
            ((TextView) row.findViewById(R.id.cityState))
                .setText(comma < 0 ? "" : entry.substring(comma + 2));
            return row;
        }
    }

    private void searchByCity(String cityName) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        // The button says what it is doing rather than only going dead.
        citySearchButton.setEnabled(false);
        citySearchButton.setAlpha(0.6f);
        citySearchLabel.setText(R.string.find_nearby_searching);
        geocoderExecutor.execute(() -> {
            List<Address> addresses = null;
            String errorMessage = null;
            try {
                addresses = geocoder.getFromLocationName(cityName, 1);
            } catch (IOException e) {
                errorMessage = getString(R.string.search_network_error);
            }

            List<Address> results = addresses;
            String error = errorMessage;
            runOnUiThread(() -> {
                citySearchButton.setEnabled(true);
                citySearchButton.setAlpha(1f);
                citySearchLabel.setText(R.string.find_nearby_beaches);
                if (error != null) {
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
                } else if (results != null && !results.isEmpty()) {
                    Address address = results.get(0);
                    isManualLocation = true;
                    updateUserLocation(address.getLatitude(), address.getLongitude());
                    Toast.makeText(this, getString(R.string.search_near_city, cityName),
                        Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, R.string.search_city_not_found, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void setupRadiusChips() {
        updateRadiusChipLabels();
        for (int i = 0; i < radiusChips.length; i++) {
            // The last chip is "All", which is no step at all.
            final int step = i == radiusChips.length - 1 ? RADIUS_ALL : i;
            radiusChips[i].setOnClickListener(v -> {
                if (radiusStep == step) return;
                selectRadius(step);
                updateList();
            });
        }
        selectRadius(RADIUS_ALL);
    }

    /**
     * Moves the tray to a step and cuts the list at it. Selection is drawn by
     * the chip's own background and colour states; only the weight has to be
     * set by hand, because a typeface is not a state a selector can hold.
     */
    private void selectRadius(int step) {
        radiusStep = step;
        currentRadiusMiles = radiusMiles(step);
        for (int i = 0; i < radiusChips.length; i++) {
            boolean on = (i == radiusChips.length - 1) ? step == RADIUS_ALL : step == i;
            radiusChips[i].setSelected(on);
            radiusChips[i].setTypeface(
                Typeface.create(on ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        }
    }

    /**
     * A step as miles, which is the unit the list filters in whatever the chip
     * says. Read fresh rather than stored, so flipping the metric switch moves
     * the cut to the number now printed on the chip.
     */
    private double radiusMiles(int step) {
        if (step == RADIUS_ALL) return Double.MAX_VALUE;
        return UnitConverter.useMetric(this)
            ? RADIUS_STEPS_KM[step] / UnitConverter.KM_PER_MILE
            : RADIUS_STEPS_MILES[step];
    }

    private void setupSearch() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                // The clear mark is only worth its place once there is
                // something to clear.
                searchClearButton.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);
                updateList();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        searchClearButton.setOnClickListener(v -> searchEditText.setText(""));
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
        // The provider reports every second or two whether or not the phone
        // has moved, and each report used to rebuild the whole list, which
        // reloaded every row's photo and flashed it to the drawn placeholder
        // and back. A fix too close to the last one to change any distance on
        // screen has nothing new for the list, the badge or the stored position.
        if (hasRealLocation && UnitConverter.calculateDistance(
                realLat, realLon, latitude, longitude) < REFIX_MILES) {
            return;
        }
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
        updateRadiusChipsEnabled();
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
     * Fills the hero card - the verdict, the sentence behind it and the four
     * readings it is drawn from - for the closest beach, or hides it when
     * there is nothing to report on.
     */
    private void bindVerdict(Beach beach) {
        nearestBeach = beach;
        if (beach == null) {
            verdictCard.setVisibility(View.GONE);
            // Nothing to name yet, so the kicker would only repeat the title.
            headerKickerRow.setVisibility(View.GONE);
            headerTitle.setText(R.string.header_title_default);
            return;
        }

        verdictCard.setVisibility(View.VISIBLE);

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
        int ring = ContextCompat.getColor(this,
            level != null ? level.getTintRes() : R.color.surface_alt);

        paintVerdictDot(color, ring);
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
     * The safety colour as a dot in a ring of its own tint - the one mark that
     * carries the verdict, where a coloured band across the card and a badge
     * beside the text used to carry it between them.
     */
    private void paintVerdictDot(int color, int ringColor) {
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(ringColor);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(color);

        LayerDrawable layers = new LayerDrawable(new Drawable[] {ring, dot});
        int inset = Math.round(4 * getResources().getDisplayMetrics().density);
        layers.setLayerInset(1, inset, inset, inset, inset);
        verdictDot.setBackground(layers);
    }

    /**
     * "Updated 12m ago", beside the verdict. The beach is not named here - the
     * header above the card already names it. Before any reading has landed
     * there is nothing to date, so the line stays empty; the verdict beside it
     * already says conditions are still being checked.
     */
    private String updatedLine(Beach beach) {
        BeachConditions conditions = beach.getConditions();
        if (conditions == null) return "";
        long minutes = (System.currentTimeMillis() - conditions.getFetchedAtMillis()) / 60_000L;
        if (minutes < 1) return getString(R.string.verdict_updated_now);
        if (minutes < 60) return getString(R.string.verdict_updated_minutes, minutes);
        return getString(R.string.verdict_updated_hours, minutes / 60);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateRadiusChipLabels();
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

    /**
     * Labels the radius chips in the unit Settings is on, and re-reads the
     * checked one in that unit. Called from onResume because the metric switch
     * lives on another screen: coming back from it should leave the list
     * showing what the chip now says, not what it said before.
     */
    private void updateRadiusChipLabels() {
        int[] steps = UnitConverter.useMetric(this) ? RADIUS_STEPS_KM : RADIUS_STEPS_MILES;
        String unit = UnitConverter.distanceUnit(this);
        for (int step = 0; step < steps.length; step++) {
            radiusChips[step].setText(getString(R.string.radius_step, steps[step], unit));
        }
        currentRadiusMiles = radiusMiles(radiusStep);
    }

    @Override
    protected void onStop() {
        super.onStop();
        locationService.stopLocationUpdates();
        stopPulse();
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
