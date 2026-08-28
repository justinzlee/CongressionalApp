package com.example.myapplication;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import android.content.res.ColorStateList;
import com.bumptech.glide.Glide;
import com.example.myapplication.model.Beach;
import com.example.myapplication.model.BeachConditions;
import com.example.myapplication.model.HelpType;
import com.example.myapplication.model.SafetyLevel;
import com.example.myapplication.util.UnitConverter;
import java.util.List;

public class BeachAdapter extends RecyclerView.Adapter<BeachAdapter.BeachViewHolder> {
    public interface OnBeachClickListener {
        void onBeachClick(Beach beach);
    }

    private List<Beach> beaches;
    private double userLat;
    private double userLon;
    private boolean hasLocation;
    private final OnBeachClickListener listener;

    public BeachAdapter(List<Beach> beaches, double userLat, double userLon, OnBeachClickListener listener) {
        this.beaches = beaches;
        this.userLat = userLat;
        this.userLon = userLon;
        this.listener = listener;
    }

    public void setBeaches(List<Beach> beaches, boolean hasLocation, double userLat, double userLon) {
        this.beaches = beaches;
        this.hasLocation = hasLocation;
        this.userLat = userLat;
        this.userLon = userLon;
        notifyDataSetChanged();
    }

    public void updateUserLocation(double lat, double lon) {
        this.userLat = lat;
        this.userLon = lon;
        this.hasLocation = true;
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
        View row = holder.itemView;

        holder.name.setText(beach.getName());
        row.setOnClickListener(v -> listener.onBeachClick(beach));

        Glide.with(row.getContext())
            .load(beach.getImageUrl())
            .centerCrop()
            .placeholder(R.drawable.bg_image_placeholder)
            .into(holder.beachImage);

        // Verdict drives the row's chip colour and its one-line summary.
        SafetyLevel level = SafetyLevel.evaluate(beach);
        if (level != null) {
            int color = ContextCompat.getColor(row.getContext(), level.getColorRes());
            int tint = ContextCompat.getColor(row.getContext(), level.getTintRes());
            holder.levelIcon.setImageResource(level.getIconRes());
            ImageViewCompat.setImageTintList(holder.levelIcon, ColorStateList.valueOf(color));
            tintChip(holder.levelChip, tint);
            holder.verdict.setText(level.getShortVerdictRes());
            holder.verdict.setTextColor(color);
        } else {
            int muted = ContextCompat.getColor(row.getContext(), R.color.text_secondary);
            holder.levelIcon.setImageResource(R.drawable.ic_warning_circle);
            ImageViewCompat.setImageTintList(holder.levelIcon, ColorStateList.valueOf(muted));
            tintChip(holder.levelChip, ContextCompat.getColor(row.getContext(), R.color.surface_alt));
            holder.verdict.setText(R.string.verdict_unknown);
            holder.verdict.setTextColor(muted);
        }

        // A lifeguard station on record earns the lifebuoy marker.
        boolean guarded = beach.getNearestHelp().stream()
            .anyMatch(h -> h.getType() == HelpType.LIFEGUARD_TOWER);
        holder.guardedIcon.setVisibility(guarded ? View.VISIBLE : View.GONE);

        BeachConditions conditions = beach.getConditions();
        if (conditions != null && conditions.getWaveHeight() != null) {
            holder.surf.setText(row.getContext().getString(R.string.surf_suffix,
                UnitConverter.formatWaveHeight(row.getContext(), conditions.getWaveHeight())));
            holder.surf.setVisibility(View.VISIBLE);
        } else {
            holder.surf.setVisibility(View.GONE);
        }

        if (hasLocation) {
            double distance = UnitConverter.calculateDistance(userLat, userLon, beach.getLatitude(), beach.getLongitude());
            holder.distance.setText(UnitConverter.formatDistance(row.getContext(), distance));
            holder.distance.setVisibility(View.VISIBLE);
        } else {
            holder.distance.setVisibility(View.GONE);
        }
    }

    private static void tintChip(View chip, int color) {
        android.graphics.drawable.Drawable background = chip.getBackground();
        if (background instanceof GradientDrawable) {
            ((GradientDrawable) background.mutate()).setColor(color);
        }
    }

    @Override
    public int getItemCount() {
        return beaches.size();
    }

    public static class BeachViewHolder extends RecyclerView.ViewHolder {
        public final TextView name, verdict, distance, surf;
        public final ImageView beachImage, levelIcon, guardedIcon;
        public final View levelChip;

        public BeachViewHolder(@NonNull View itemView) {
            super(itemView);
            beachImage = itemView.findViewById(R.id.beachImage);
            name = itemView.findViewById(R.id.beachName);
            verdict = itemView.findViewById(R.id.beachVerdict);
            distance = itemView.findViewById(R.id.beachDistance);
            surf = itemView.findViewById(R.id.beachSurf);
            levelChip = itemView.findViewById(R.id.beachLevelChip);
            levelIcon = itemView.findViewById(R.id.beachLevelIcon);
            guardedIcon = itemView.findViewById(R.id.beachGuardedIcon);
        }
    }
}
