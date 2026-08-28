package com.example.myapplication;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
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
import android.widget.TextView;
import android.widget.Toast;
import android.view.Menu;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.myapplication.data.BeachRepository;
import com.example.myapplication.location.LocationService;
import com.example.myapplication.model.Beach;
import com.example.myapplication.util.EdgeToEdge;
import com.example.myapplication.util.UnitConverter;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {
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

    private double currentLat = 34.0522; // Default LA
    private double currentLon = -118.2437;
    private double currentRadiusMiles = Double.MAX_VALUE; // Default All
    private boolean hasLocation = false;
    private boolean isManualLocation = false;
    private boolean shouldScrollToTop = false;
    private final ExecutorService geocoderExecutor = Executors.newSingleThreadExecutor();

    private final String[] US_COASTAL_CITIES = {
        "San Diego, CA", "Carlsbad, CA", "Encinitas, CA", "Oceanside, CA", "La Jolla, CA",
        "Los Angeles, CA", "Santa Monica, CA", "Malibu, CA", "Venice, CA", "Huntington Beach, CA",
        "Newport Beach, CA", "Laguna Beach, CA", "San Francisco, CA", "Monterey, CA", "Santa Barbara, CA",
        "Miami, FL", "Fort Lauderdale, FL", "Miami Beach, FL", "Key West, FL", "Clearwater, FL",
        "Tampa, FL", "Sarasota, FL", "Destin, FL", "Panama City Beach, FL", "Jacksonville, FL",
        "New York, NY", "Brooklyn, NY", "Queens, NY", "Montauk, NY", "Jones Beach, NY",
        "Galveston, TX", "South Padre Island, TX", "Corpus Christi, TX", "Port Aransas, TX",
        "Atlantic City, NJ", "Cape May, NJ", "Asbury Park, NJ", "Ocean City, NJ",
        "Virginia Beach, VA", "Myrtle Beach, SC", "Hilton Head, SC", "Charleston, SC",
        "Wilmington, NC", "Wrightsville Beach, NC", "Nags Head, NC", "Outer Banks, NC",
        "Savannah, GA", "Tybee Island, GA", "Portland, ME", "Cannon Beach, OR", "Seaside, OR",
        "Ocean Shores, WA", "Seattle, WA", "Ruby Beach, WA"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        EdgeToEdge.applyTopInset(findViewById(R.id.appBarLayout));
        EdgeToEdge.applyBottomInset(findViewById(R.id.mainScrollView));

        repository = new BeachRepository();
        locationService = new LocationService(this);

        searchEditText = findViewById(R.id.searchEditText);
        citySearchEditText = findViewById(R.id.citySearchEditText);
        citySearchButton = findViewById(R.id.citySearchButton);
        radiusToggleGroup = findViewById(R.id.radiusToggleGroup);
        emptyState = findViewById(R.id.emptyState);
        beachCountBadge = findViewById(R.id.beachCountBadge);

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

        repository.refreshAllData(() -> runOnUiThread(this::updateList));

        updateList(); // Initial update

        setupWelcomeOverlay(savedInstanceState);
    }

    private void setupWelcomeOverlay(Bundle savedInstanceState) {
        View welcomeOverlay = findViewById(R.id.welcomeOverlay);
        View welcomeContent = findViewById(R.id.welcomeContent);
        MosaicRevealOverlay revealView = findViewById(R.id.welcomeRevealView);
        TextView tapToStartText = findViewById(R.id.welcomeTapToStart);

        if (savedInstanceState != null) {
            // Activity was recreated (e.g. rotation) - don't replay the intro.
            welcomeOverlay.setVisibility(View.GONE);
            return;
        }

        revealView.setFillColor(getColor(R.color.forest_green_translucent));

        ObjectAnimator blink = ObjectAnimator.ofFloat(tapToStartText, View.ALPHA, 0.25f, 0.9f);
        blink.setDuration(900);
        blink.setStartDelay(400);
        blink.setRepeatMode(ValueAnimator.REVERSE);
        blink.setRepeatCount(ValueAnimator.INFINITE);
        blink.start();

        welcomeOverlay.setOnClickListener(v -> {
            blink.cancel();
            welcomeOverlay.setClickable(false);

            // The green plate shatters into a grid of squares that shrink and
            // fade away starting at the center and rippling outward in a
            // circular wave, revealing the app underneath.
            ValueAnimator revealAnimator = ValueAnimator.ofFloat(0f, 1f);
            revealAnimator.setDuration(900);
            revealAnimator.setInterpolator(new android.view.animation.AccelerateInterpolator(1.2f));
            revealAnimator.addUpdateListener(anim -> revealView.setProgress((float) anim.getAnimatedValue()));
            revealAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    welcomeOverlay.setVisibility(View.GONE);
                }
            });
            revealAnimator.start();

            // The logo and prompt fade out quickly so they don't linger once
            // the hole has already swallowed them.
            welcomeContent.animate().alpha(0f).setDuration(250).start();
        });
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (item.getItemId() == R.id.action_my_location) {
            isManualLocation = false;
            searchEditText.setText("");
            citySearchEditText.setText("");
            radiusToggleGroup.check(R.id.radiusAll);
            shouldScrollToTop = true;
            Toast.makeText(this, "Using real-time location", Toast.LENGTH_SHORT).show();
            requestLocation();
            return true;
        }
        return super.onOptionsItemSelected(item);
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
                if (!isManualLocation) {
                    updateUserLocation(latitude, longitude);
                }
            }

            @Override
            public void onLocationError(String error) {}
        });

        locationService.startLocationUpdates(new LocationService.LocationUpdateListener() {
            @Override
            public void onLocationUpdated(double latitude, double longitude) {
                if (!isManualLocation) {
                    updateUserLocation(latitude, longitude);
                }
            }

            @Override
            public void onLocationError(String error) {
                updateList();
                Toast.makeText(MainActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateUserLocation(double latitude, double longitude) {
        currentLat = latitude;
        currentLon = longitude;
        hasLocation = true;
        updateRadiusButtonsEnabled();
        adapter.updateUserLocation(latitude, longitude);
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
            .collect(Collectors.toList());

        adapter.setBeaches(filteredBeaches, hasLocation, currentLat, currentLon);
        emptyState.setVisibility(filteredBeaches.isEmpty() ? View.VISIBLE : View.GONE);
        beachCountBadge.setText(getResources().getQuantityString(
                R.plurals.beach_count, filteredBeaches.size(), filteredBeaches.size()));

        if (shouldScrollToTop) {
            beachRecyclerView.scrollToPosition(0);
            shouldScrollToTop = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateRadiusButtonLabels();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        updateList();
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
