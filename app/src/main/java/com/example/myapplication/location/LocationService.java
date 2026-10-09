package com.example.myapplication.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import com.example.myapplication.R;
import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

public class LocationService {
    private final FusedLocationProviderClient fusedLocationClient;
    private final Context context;
    private LocationCallback locationCallback;

    public interface LocationUpdateListener {
        void onLocationUpdated(double latitude, double longitude);

        /** @param error why there is no position, in the app's language, fit to show */
        void onLocationError(String error);
    }

    public LocationService(Context context) {
        this.context = context;
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
    }

    private boolean isLocationEnabled() {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        return locationManager != null
                && (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
    }

    public void getLastLocation(LocationUpdateListener listener) {
        boolean hasFine = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarse = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!hasFine && !hasCoarse) {
            listener.onLocationError(context.getString(R.string.location_permission_needed));
            return;
        }
        if (!isLocationEnabled()) {
            listener.onLocationError(context.getString(R.string.location_off));
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                listener.onLocationUpdated(location.getLatitude(), location.getLongitude());
            } else {
                listener.onLocationError(context.getString(R.string.location_unavailable));
            }
        }).addOnFailureListener(e -> listener.onLocationError(
            context.getString(R.string.location_unavailable)));
    }

    /**
     * Where the phone is now, for a screen that needs one answer rather than a
     * stream. A position the provider already holds is taken if it is under a
     * minute old; otherwise it gets a fresh fix, giving up after ten seconds.
     * A failure is reported as an error rather than as an old position.
     */
    public void getCurrentLocation(LocationUpdateListener listener) {
        boolean hasFine = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarse = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!hasFine && !hasCoarse) {
            listener.onLocationError(context.getString(R.string.location_permission_needed));
            return;
        }
        if (!isLocationEnabled()) {
            listener.onLocationError(context.getString(R.string.location_off));
            return;
        }

        CurrentLocationRequest request = new CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setMaxUpdateAgeMillis(60_000)
                .setDurationMillis(10_000)
                .build();
        fusedLocationClient.getCurrentLocation(request, null).addOnSuccessListener(location -> {
            if (location != null) {
                listener.onLocationUpdated(location.getLatitude(), location.getLongitude());
            } else {
                listener.onLocationError(context.getString(R.string.location_unavailable));
            }
        }).addOnFailureListener(e -> listener.onLocationError(
            context.getString(R.string.location_unavailable)));
    }

    public void startLocationUpdates(LocationUpdateListener listener) {
        boolean hasFine = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarse = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!hasFine && !hasCoarse) {
            listener.onLocationError(context.getString(R.string.location_permission_needed));
            return;
        }
        if (!isLocationEnabled()) {
            listener.onLocationError(context.getString(R.string.location_off));
            return;
        }

        stopLocationUpdates();

        // PRIORITY_HIGH_ACCURACY requires ACCESS_FINE_LOCATION and throws SecurityException
        // with only ACCESS_COARSE_LOCATION granted (e.g. user picked "approximate" access).
        // High accuracy is kept for the SOS sheet's map pin; what is relaxed is
        // how often it reports. The home screen ignores any fix within about
        // fifty metres of the last, so a report every second or two of a phone
        // lying still on a towel only woke the app to throw it away.
        int priority = hasFine ? Priority.PRIORITY_HIGH_ACCURACY : Priority.PRIORITY_BALANCED_POWER_ACCURACY;
        LocationRequest locationRequest = new LocationRequest.Builder(priority, 5000)
                .setMinUpdateIntervalMillis(2000)
                .setMinUpdateDistanceMeters(10f)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        listener.onLocationUpdated(location.getLatitude(), location.getLongitude());
                    }
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, context.getMainLooper());
    }

    public void stopLocationUpdates() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            locationCallback = null;
        }
    }
}
