package com.example.myapplication;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.animation.PathInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.myapplication.util.UnitConverter;

/**
 * The wave-height card on the beach detail screen: the swell as a sea you can
 * look at rather than a number in a tile. {@link WaveHeightView} draws the
 * water, {@link WaveScaleView} the ruler beside it, and this puts the title,
 * severity tag and advice line around them.
 *
 * The reading always arrives by animation. The first one rises out of a flat
 * sea, so opening a beach fills the card up to its swell height; later ones,
 * when the live forecast lands, ease from wherever the water already was.
 */
public class WaveHeightCard extends LinearLayout {

    private static final long RISE_MS = 900L;
    private static final long UPDATE_MS = 700L;
    /**
     * Where the sea rests when there is no reading to show — a tenth of the
     * way up, so the card looks like calm water rather than an empty panel.
     * It is a look, not a measurement, so it tracks the scale's range.
     */
    private static final float NO_READING_FT = WaveScale.MAX_FT / 10f;

    private final TextView title;
    private final TextView tag;
    private final TextView advice;
    private final TextView scaleLabel;
    private final SwellLoaderView loader;
    private final WaveHeightView water;
    private final WaveScaleView scale;

    private ValueAnimator animator;
    private float shownFt;
    private boolean firstReading = true;

    public WaveHeightCard(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setBackgroundResource(R.drawable.bg_wave_card);
        int padding = Math.round(16f * getResources().getDisplayMetrics().density);
        setPadding(padding, padding, padding, padding);
        LayoutInflater.from(context).inflate(R.layout.view_wave_height_card, this, true);

        title = findViewById(R.id.waveCardTitle);
        tag = findViewById(R.id.waveCardTag);
        advice = findViewById(R.id.waveCardAdvice);
        scaleLabel = findViewById(R.id.waveCardScaleLabel);
        loader = findViewById(R.id.waveCardLoader);
        water = findViewById(R.id.waveCardWater);
        scale = findViewById(R.id.waveCardScale);

        scaleLabel.setText(context.getString(R.string.wave_card_scale,
            UnitConverter.waveHeightTick(context, WaveScale.MAX_FT),
            UnitConverter.waveHeightUnit(context)));
    }

    public void setBeachName(String beachName) {
        String headline = getContext().getString(R.string.wave_card_headline,
            beachName, getContext().getString(R.string.wave_card_today));

        // The day is a qualifier on the beach, not part of its name, so it and
        // the dash before it drop back to the secondary colour - the two-tone
        // headline the design uses.
        int nameEnd = headline.indexOf(beachName) + beachName.length();
        SpannableString styled = new SpannableString(headline);
        styled.setSpan(
            new ForegroundColorSpan(ContextCompat.getColor(getContext(), R.color.text_secondary)),
            nameEnd, headline.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        title.setText(styled);
    }

    /**
     * While the forecast is still on its way: the sea settles at rest with no
     * number on it, the loader stands where the severity tag will go, and the
     * advice line says what is happening rather than that there is no reading.
     */
    public void setLoading() {
        tag.setVisibility(GONE);
        loader.setVisibility(VISIBLE);
        advice.setText(R.string.wave_advice_loading);
        water.setContentDescription(getContext().getString(R.string.wave_advice_loading));
        water.setLoading(true);
        animateTo(NO_READING_FT, false);
    }

    /** @param feet the beach's swell height, or null when none could be had */
    public void setReading(Double feet) {
        boolean known = feet != null;
        // A reading past the top of the scale still shows its real number - the
        // sea and the marker just saturate.
        float target = known ? feet.floatValue() : NO_READING_FT;

        loader.setVisibility(GONE);
        water.setLoading(false);
        if (known) {
            WaveScale.Level level = WaveScale.levelFor(target);
            tag.setVisibility(VISIBLE);
            tag.setText(level.getLabelRes());
            tag.setTextColor(ContextCompat.getColor(getContext(), level.getColorRes()));
            tag.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(getContext(), level.getTintRes())));
            advice.setText(level.getAdviceRes());
            water.setContentDescription(getContext().getString(R.string.wave_card_reading,
                UnitConverter.formatWaveHeight(getContext(), feet)));
        } else {
            tag.setVisibility(GONE);
            advice.setText(R.string.wave_advice_unknown);
            water.setContentDescription(
                getContext().getString(R.string.wave_card_reading_unknown));
        }
        animateTo(target, known);
    }

    /** Eases the sea to a height: up from flat the first time, from where it is after. */
    private void animateTo(float target, boolean known) {
        if (animator != null) animator.cancel();
        float from = firstReading ? 0f : shownFt;
        long duration = firstReading ? RISE_MS : UPDATE_MS;
        firstReading = false;
        apply(from, known);

        animator = ValueAnimator.ofFloat(from, target);
        animator.setDuration(duration);
        animator.setInterpolator(new PathInterpolator(0.22f, 0.8f, 0.28f, 1f));
        animator.addUpdateListener(a -> apply((Float) a.getAnimatedValue(), known));
        animator.start();
    }

    private void apply(float feet, boolean known) {
        shownFt = feet;
        water.setReading(feet, known);
        scale.setReading(feet, known);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) animator.cancel();
    }
}
