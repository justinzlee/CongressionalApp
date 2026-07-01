package com.example.myapplication;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.myapplication.model.Beach;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class BeachAdapter extends RecyclerView.Adapter<BeachAdapter.BeachViewHolder> {
    public interface OnBeachClickListener {
        void onBeachClick(Beach beach);
    }

    private List<Beach> beaches;
    private double userLat;
    private double userLon;
    private boolean isSearching;
    private final OnBeachClickListener listener;

    public BeachAdapter(List<Beach> beaches, double userLat, double userLon, OnBeachClickListener listener) {
        this.beaches = beaches;
        this.userLat = userLat;
        this.userLon = userLon;
        this.listener = listener;
        this.isSearching = false;
    }

    public void setBeaches(List<Beach> beaches, boolean isSearching) {
        this.beaches = beaches;
        this.isSearching = isSearching;
        notifyDataSetChanged();
    }

    public void updateUserLocation(double lat, double lon) {
        this.userLat = lat;
        this.userLon = lon;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BeachViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_beach, parent, false);
        return new BeachViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BeachViewHolder holder, int position) {
        Beach beach = beaches.get(position);
        holder.name.setText(beach.getName());
        holder.itemView.setOnClickListener(v -> listener.onBeachClick(beach));
        
        if (beach.getConditions() != null) {
            String conditionsText = String.format(Locale.getDefault(), "Air: %s | Waves: %s", 
                beach.getConditions().getAirTemperature() != null ? beach.getConditions().getAirTemperature() + "°F" : "--",
                beach.getConditions().getWaveHeight() != null ? beach.getConditions().getWaveHeight() + "m" : "--");
            holder.conditions.setText(conditionsText);
        } else {
            holder.conditions.setText(holder.itemView.getContext().getString(R.string.conditions_unavailable));
        }

        if (!beach.getHazards().isEmpty()) {
            String hazardNames = beach.getHazards().stream()
                .map(h -> h.getType().name().replace("_", " "))
                .collect(Collectors.joining(", "));
            holder.hazards.setText(holder.itemView.getContext().getString(R.string.hazards_label, hazardNames));
        } else {
            holder.hazards.setText(holder.itemView.getContext().getString(R.string.no_major_hazards));
        }

        if (!beach.getNearestHelp().isEmpty()) {
            holder.help.setText(String.format(Locale.getDefault(), "Nearest Help: %s", beach.getNearestHelp().get(0).getName()));
        }

        if (isSearching) {
            double distance = calculateDistance(userLat, userLon, beach.getLatitude(), beach.getLongitude());
            holder.distance.setText(holder.itemView.getContext().getString(R.string.distance_away, distance));
            holder.distance.setVisibility(View.VISIBLE);
        } else {
            holder.distance.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return beaches.size();
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

    public static class BeachViewHolder extends RecyclerView.ViewHolder {
        public TextView name, conditions, hazards, help, distance;

        public BeachViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.beachName);
            conditions = itemView.findViewById(R.id.beachConditions);
            hazards = itemView.findViewById(R.id.beachHazards);
            help = itemView.findViewById(R.id.beachHelp);
            distance = itemView.findViewById(R.id.beachDistance);
        }
    }
}
