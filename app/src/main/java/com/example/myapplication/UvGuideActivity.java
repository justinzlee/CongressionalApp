package com.example.myapplication;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.example.myapplication.model.UvLevel;
import com.example.myapplication.util.EdgeToEdge;

/**
 * What the UV index is telling you, band by band, and how much cover each one
 * asks for — from none at all up to staying out of the sun altogether. Static
 * safety guidance, like the flag and rip-current guides.
 *
 * <p>The bands, their names and their colours all come from {@link UvLevel},
 * the same table the live reading on the home and beach screens is named
 * from, so nobody can learn "very high starts at 8" here and then read a
 * different word for 8 on the way to the beach.
 */
public class UvGuideActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_uv_guide);

        EdgeToEdge.applyTopInset(findViewById(R.id.uvGuideHeader));
        EdgeToEdge.applyBottomInset(findViewById(R.id.uvGuideScrollView));

        findViewById(R.id.uvGuideBackButton).setOnClickListener(
            v -> getOnBackPressedDispatcher().onBackPressed());

        buildScale(LayoutInflater.from(this));
    }

    private void buildScale(LayoutInflater inflater) {
        ViewGroup list = findViewById(R.id.uvBandList);
        for (UvLevel band : UvLevel.values()) {
            View row = inflater.inflate(R.layout.item_uv_band, list, false);

            ((TextView) row.findViewById(R.id.uvBandTitle)).setText(
                getString(R.string.uv_band_title, getString(band.getLabelRes()), range(band)));
            ((TextView) row.findViewById(R.id.uvBandBody)).setText(band.getAdviceRes());

            ImageView icon = row.findViewById(R.id.uvBandIcon);
            ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(
                ContextCompat.getColor(this, band.getColorRes())));

            list.addView(row);
        }
    }

    /** "UV 8–10", or "UV 11 and up" for the band the scale stops counting at. */
    private String range(UvLevel band) {
        Integer max = band.getMax();
        return max == null
            ? getString(R.string.uv_band_range_open, band.getMin())
            : getString(R.string.uv_band_range, band.getMin(), max);
    }
}
