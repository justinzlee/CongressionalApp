package com.example.myapplication;

import android.content.Context;
import android.graphics.drawable.Drawable;
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
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.example.myapplication.data.BeachPhotoStore;
import com.example.myapplication.model.Beach;
import com.example.myapplication.model.BeachConditions;
import com.example.myapplication.model.HelpType;
import com.example.myapplication.model.SafetyLevel;
import com.example.myapplication.util.BeachPlaceholder;
import com.example.myapplication.util.SunClock;
import com.example.myapplication.util.UnitConverter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class BeachAdapter extends RecyclerView.Adapter<BeachAdapter.BeachViewHolder> {
    public interface OnBeachClickListener {
        void onBeachClick(Beach beach);
    }

    /** What a row with no photo is showing, by day or after dark; see {@link #bindImage}. */
    private static final Object DRAWN_BY_DAY = new Object();
    private static final Object DRAWN_AFTER_DARK = new Object();

    private List<Beach> beaches;
    private double userLat;
    private double userLon;
    private boolean hasLocation;
    private final OnBeachClickListener listener;

    /** One id per beach for as long as the adapter lives; see {@link #getItemId}. */
    private final Map<String, Long> stableIds = new HashMap<>();

    /**
     * Built from the first row's context rather than passed in, so the adapter
     * keeps its existing constructor. It only reads preferences, so one for the
     * whole list is enough and it costs nothing to hold.
     */
    private BeachPhotoStore photoStore;

    public BeachAdapter(List<Beach> beaches, double userLat, double userLon, OnBeachClickListener listener) {
        this.beaches = beaches;
        this.userLat = userLat;
        this.userLon = userLon;
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setBeaches(List<Beach> beaches, boolean hasLocation, double userLat, double userLon) {
        this.beaches = beaches;
        this.hasLocation = hasLocation;
        this.userLat = userLat;
        this.userLon = userLon;
        notifyDataSetChanged();
    }

    /**
     * Ties each row to its beach rather than to its position. Without it every
     * notifyDataSetChanged sent all the rows back through the recycler and
     * dealt them out again in a different order, so nearly every row came back
     * holding some other beach and had to load that beach's photo afresh.
     */
    @Override
    public long getItemId(int position) {
        String id = beaches.get(position).getId();
        Long stable = stableIds.get(id);
        if (stable == null) {
            stable = (long) stableIds.size();
            stableIds.put(id, stable);
        }
        return stable;
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

        bindImage(holder, beach);

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

    /**
     * The row's picture. A photo somebody added themselves wins, then the
     * curated one. A beach with neither, or whose photo will not load, shows
     * the drawn shoreline rather than a stock picture of somewhere else, in
     * daylight or after dark according to the sun at that beach.
     *
     * <p>Left alone when the row is already showing exactly this. The home
     * screen rebinds the whole list whenever anything on it changes, and
     * handing Glide the same photo again is not free: it clears the row to the
     * placeholder and loads again, and with every row laid out at once most of
     * those loads miss the memory cache and come back from disk a few frames
     * later. That is what made the photos blink to the drawing and back.
     */
    private void bindImage(BeachViewHolder holder, Beach beach) {
        Context context = holder.itemView.getContext();
        if (photoStore == null) {
            photoStore = new BeachPhotoStore(context);
        }

        Object source = photoStore.imageSourceFor(beach);
        Object shown = source != null ? source
            : (SunClock.isDaylightAt(beach.getLatitude(), beach.getLongitude(),
                System.currentTimeMillis()) ? DRAWN_BY_DAY : DRAWN_AFTER_DARK);

        boolean isSameBeach = Objects.equals(holder.currentBeachId, beach.getId());
        holder.currentBeachId = beach.getId();
        if (isSameBeach && shown.equals(holder.shownImage)) return;
        holder.shownImage = shown;

        Drawable drawn = BeachPlaceholder.forBeach(context, beach);
        Glide.with(context)
            .load(source)
            .centerCrop()
            .placeholder(drawn)
            .fallback(drawn)
            .error(drawn)
            .listener(new RequestListener<Drawable>() {
                @Override
                public boolean onLoadFailed(GlideException e, Object model,
                        Target<Drawable> target, boolean isFirstResource) {
                    // The drawing standing in for a photo that failed is not
                    // the photo, so the next bind gets to try again.
                    if (source != null) holder.shownImage = null;
                    return false;
                }

                @Override
                public boolean onResourceReady(Drawable resource, Object model,
                        Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                    return false;
                }
            })
            .into(holder.beachImage);
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
        public String currentBeachId;
        /** The photo source, or the day/night drawing, this row last asked Glide for. */
        Object shownImage;

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
