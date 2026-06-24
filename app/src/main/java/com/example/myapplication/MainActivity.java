package com.example.myapplication;

import android.content.Intent;
import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.myapplication.ai.SafetyAdvisor;
import com.example.myapplication.data.BeachRepository;
import com.example.myapplication.location.LocationService;
import com.example.myapplication.model.Beach;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {
    private BeachRepository repository;
    private SafetyAdvisor advisor;
    private LocationService locationService;
    private BeachAdapter adapter;
    
    private EditText searchEditText;
    private AutoCompleteTextView citySearchEditText;
    private AutoCompleteTextView radiusSpinner;
    private Button citySearchButton;
    private TextView advisorTitle;
    private TextView advisorText;
    
    private double currentLat = 34.0522; // Default LA
    private double currentLon = -118.2437;
    private double currentRadius = 10.0;
    private boolean hasSearched = false;

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

        repository = new BeachRepository();
        advisor = new SafetyAdvisor();
        locationService = new LocationService(this);

        searchEditText = findViewById(R.id.searchEditText);
        citySearchEditText = findViewById(R.id.citySearchEditText);
        citySearchButton = findViewById(R.id.citySearchButton);
        radiusSpinner = findViewById(R.id.radiusSpinner);
        advisorTitle = findViewById(R.id.advisorTitle);
        advisorText = findViewById(R.id.advisorText);
        
        RecyclerView recyclerView = findViewById(R.id.beachRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BeachAdapter(new ArrayList<>(), currentLat, currentLon, beach -> {
            Intent intent = new Intent(MainActivity.this, BeachDetailActivity.class);
            intent.putExtra(BeachDetailActivity.EXTRA_BEACH_ID, beach.getId());
            startActivity(intent);
        });
        recyclerView.setAdapter(adapter);

        setupRadiusSpinner();
        setupSearch();
        setupCitySearch();
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
        try {
            List<Address> addresses = geocoder.getFromLocationName(cityName, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                currentLat = address.getLatitude();
                currentLon = address.getLongitude();
                hasSearched = true;
                adapter.updateUserLocation(currentLat, currentLon);
                updateList();
                Toast.makeText(this, "Searching near: " + cityName, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Location not found", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Toast.makeText(this, "Network error", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupRadiusSpinner() {
        String[] options = {"5 miles", "10 miles", "25 miles", "50 miles"};
        ArrayAdapter<String> radiusAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, options);
        radiusSpinner.setAdapter(radiusAdapter);
        radiusSpinner.setText(options[1], false); // Default 10
        currentRadius = 10;

        radiusSpinner.setOnItemClickListener((parent, view, position, id) -> {
            switch (position) {
                case 0: currentRadius = 5; break;
                case 1: currentRadius = 10; break;
                case 2: currentRadius = 25; break;
                case 3: currentRadius = 50; break;
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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        } else {
            fetchLocation();
        }
    }

    private void fetchLocation() {
        locationService.startLocationUpdates(new LocationService.LocationUpdateListener() {
            @Override
            public void onLocationUpdated(double latitude, double longitude) {
                currentLat = latitude;
                currentLon = longitude;
                adapter.updateUserLocation(latitude, longitude);
                updateList();
            }

            @Override
            public void onLocationError(String error) {
                updateList();
            }
        });
    }

    private void updateList() {
        String query = searchEditText.getText().toString();
        List<Beach> filtered = repository.searchBeaches(query);
        
        filtered = filtered.stream()
            .filter(b -> calculateDistance(currentLat, currentLon, b.getLatitude(), b.getLongitude()) <= currentRadius)
            .sorted((b1, b2) -> Double.compare(
                calculateDistance(currentLat, currentLon, b1.getLatitude(), b1.getLongitude()),
                calculateDistance(currentLat, currentLon, b2.getLatitude(), b2.getLongitude())))
            .collect(Collectors.toList());

        adapter.setBeaches(filtered, hasSearched);
        
        if (!filtered.isEmpty()) {
            Beach top = filtered.get(0);
            advisorTitle.setText("Safety Advisor (" + top.getName() + ")");
            advisorText.setText(advisor.getAdvice(top.getHazards()));
        } else {
            advisorTitle.setText("AI Safety Advisor");
            advisorText.setText("No beaches found within " + (currentRadius == Double.MAX_VALUE ? "any distance" : currentRadius + " miles") + ".");
        }
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 3958.8;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }

    @Override
    protected void onStop() {
        super.onStop();
        locationService.stopLocationUpdates();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchLocation();
        }
    }
}
