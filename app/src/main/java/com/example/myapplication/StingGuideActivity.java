package com.example.myapplication;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ArrayRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.example.myapplication.util.EdgeToEdge;

/**
 * First aid for one stinging animal. Static safety guidance, like the
 * rip-current and flag guides — no live data, so it still works with no
 * signal, which is when it is most needed.
 *
 * <p>Each animal is its own lesson rather than one card in a list of five:
 * someone reaching for this has already been stung by one specific thing, and
 * should land on those steps without scrolling past four they don't need.
 */
public class StingGuideActivity extends AppCompatActivity {

    private static final String EXTRA_STING = "extra_sting";

    /**
     * The stinging animals someone at an American beach is actually likely to
     * meet. Each is a lesson on the Learn tab and a screen of its own.
     */
    public enum Sting {
        STINGRAY(R.string.sting_ray_title, R.string.sting_ray_subtitle,
            R.string.sting_ray_feels, R.array.sting_ray_do, R.array.sting_ray_dont,
            R.drawable.ic_stingray),
        JELLYFISH(R.string.sting_jelly_title, R.string.sting_jelly_subtitle,
            R.string.sting_jelly_feels, R.array.sting_jelly_do, R.array.sting_jelly_dont,
            R.drawable.ic_jellyfish),
        MAN_O_WAR(R.string.sting_manowar_title, R.string.sting_manowar_subtitle,
            R.string.sting_manowar_feels, R.array.sting_manowar_do, R.array.sting_manowar_dont,
            R.drawable.ic_manowar),
        SEA_URCHIN(R.string.sting_urchin_title, R.string.sting_urchin_subtitle,
            R.string.sting_urchin_feels, R.array.sting_urchin_do, R.array.sting_urchin_dont,
            R.drawable.ic_urchin),
        SEA_LICE(R.string.sting_sealice_title, R.string.sting_sealice_subtitle,
            R.string.sting_sealice_feels, R.array.sting_sealice_do, R.array.sting_sealice_dont,
            R.drawable.ic_sea_lice);

        @StringRes public final int titleRes;
        @StringRes public final int subtitleRes;
        @StringRes final int feelsRes;
        @ArrayRes final int doRes;
        @ArrayRes final int dontRes;
        @DrawableRes public final int iconRes;

        Sting(int titleRes, int subtitleRes, int feelsRes, int doRes, int dontRes, int iconRes) {
            this.titleRes = titleRes;
            this.subtitleRes = subtitleRes;
            this.feelsRes = feelsRes;
            this.doRes = doRes;
            this.dontRes = dontRes;
            this.iconRes = iconRes;
        }
    }

    /** Opens the first aid for one animal. */
    public static Intent intentFor(Context context, Sting sting) {
        return new Intent(context, StingGuideActivity.class)
            .putExtra(EXTRA_STING, sting.name());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sting_guide);

        Sting sting = stingFromIntent();
        if (sting == null) {
            // Launched without a valid animal — nothing to teach, so don't sit
            // on a blank screen.
            finish();
            return;
        }

        EdgeToEdge.applyTopInset(findViewById(R.id.stingGuideHeader));
        EdgeToEdge.applyBottomInset(findViewById(R.id.stingGuideScrollView));

        findViewById(R.id.stingGuideBackButton).setOnClickListener(
            v -> getOnBackPressedDispatcher().onBackPressed());

        setTitle(sting.titleRes);
        ((TextView) findViewById(R.id.stingTitle)).setText(sting.titleRes);
        ((TextView) findViewById(R.id.stingFeels)).setText(sting.feelsRes);
        ((ImageView) findViewById(R.id.stingIcon)).setImageResource(sting.iconRes);

        LayoutInflater inflater = LayoutInflater.from(this);
        fillSteps(inflater, findViewById(R.id.stingDoList),
            getResources().getStringArray(sting.doRes), true);
        fillSteps(inflater, findViewById(R.id.stingDontList),
            getResources().getStringArray(sting.dontRes), false);
    }

    @Nullable
    private Sting stingFromIntent() {
        String name = getIntent().getStringExtra(EXTRA_STING);
        if (name == null) {
            return null;
        }
        try {
            return Sting.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void fillSteps(LayoutInflater inflater, ViewGroup list,
                           String[] steps, boolean numbered) {
        for (int i = 0; i < steps.length; i++) {
            View row = inflater.inflate(R.layout.item_first_aid_step, list, false);
            TextView number = row.findViewById(R.id.stepNumber);
            ImageView mark = row.findViewById(R.id.stepMark);

            if (numbered) {
                number.setText(String.valueOf(i + 1));
            } else {
                // The "don't" list has no order to it, so it takes a cross
                // rather than a number that would imply one.
                number.setVisibility(View.GONE);
                mark.setVisibility(View.VISIBLE);
                ImageViewCompat.setImageTintList(mark, ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.danger)));
            }

            ((TextView) row.findViewById(R.id.stepText)).setText(steps[i]);
            list.addView(row);
        }
    }
}
