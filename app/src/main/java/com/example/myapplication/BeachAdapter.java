package com.example.myapplication;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.myapplication.model.Beach;
import com.example.myapplication.model.BeachConditions;
import com.example.myapplication.util.UnitConverter;
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
        
        Glide.with(holder.itemView.getContext())
            .load(beach.getImageUrl())
            .centerCrop()
            .placeholder(R.color.water_accent)
            .into(holder.beachImage);

        BeachConditions conditions = beach.getConditions();
        if (conditions != null) {
            String tempText = UnitConverter.formatTemperature(holder.itemView.getContext(), conditions.getAirTemperature());
            String waveText = UnitConverter.formatWaveHeight(holder.itemView.getContext(), conditions.getWaveHeight());
            String conditionsText = String.format(Locale.getDefault(), "Air: %s | Waves: %s", 
                tempText,
                waveText);
            holder.conditions.setText(conditionsText);

            // UV Badge setup
            if (conditions.getUvIndex() != null) {
                holder.uvBadgeContainer.setVisibility(View.VISIBLE);
                holder.uvValue.setText(String.valueOf(conditions.getUvIndex()));
                holder.uvLabel.setText(conditions.getUvLabel());
                
                int color = ContextCompat.getColor(holder.itemView.getContext(), conditions.getUvColorResId());
                GradientDrawable drawable = (GradientDrawable) holder.uvCircle.getBackground().mutate();
                drawable.setColor(color);
                int strokeWidth = (int) (2 * holder.itemView.getContext().getResources().getDisplayMetrics().density);
                drawable.setStroke(strokeWidth, Color.BLACK);
                
                holder.uvValue.setTextColor(color);
            } else {
                holder.uvBadgeContainer.setVisibility(View.GONE);
            }
        } else {
            holder.conditions.setText(holder.itemView.getContext().getString(R.string.conditions_unavailable));
            holder.uvBadgeContainer.setVisibility(View.GONE);
        }

        if (!beach.getHazards().isEmpty()) {
            holder.hazards.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.error));
            String hazardNames = beach.getHazards().stream()
                .map(h -> h.getType().name().replace("_", " "))
                .collect(Collectors.joining(", "));
            holder.hazards.setText(holder.itemView.getContext().getString(R.string.hazards_label, hazardNames));
        } else {
            holder.hazards.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.safe_green));
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
        public TextView name, conditions, hazards, help, distance, uvValue, uvLabel;
        public ImageView beachImage;
        public View uvCircle, uvBadgeContainer;

        public BeachViewHolder(@NonNull View itemView) {
            super(itemView);
            beachImage = itemView.findViewById(R.id.beachImage);
            name = itemView.findViewById(R.id.beachName);
            conditions = itemView.findViewById(R.id.beachConditions);
            hazards = itemView.findViewById(R.id.beachHazards);
            help = itemView.findViewById(R.id.beachHelp);
            distance = itemView.findViewById(R.id.beachDistance);
            uvBadgeContainer = itemView.findViewById(R.id.uvBadgeContainer);
            uvCircle = itemView.findViewById(R.id.uvCircle);
            uvValue = itemView.findViewById(R.id.uvValue);
            uvLabel = itemView.findViewById(R.id.uvLabel);
        }
    }
}
