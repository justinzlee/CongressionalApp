package com.example.myapplication;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.myapplication.StingGuideActivity.Sting;
import com.example.myapplication.data.BeachRepository;
import com.example.myapplication.model.Beach;
import com.example.myapplication.util.EdgeToEdge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * What lives at the beaches this app covers, one coast at a time. Static
 * guidance like the other lessons, but the beach names under each coast are
 * read back out of {@link BeachRepository}, so the guide cannot drift out of
 * step with the beaches the rest of the app actually shows.
 */
public class WildlifeGuideActivity extends AppCompatActivity {

    /**
     * How to behave around an animal, not how dangerous it is. "Protected"
     * is a legal duty rather than a risk, but it belongs on the same scale
     * because it answers the same question: how close can I get?
     */
    private enum Risk {
        HARMLESS(R.string.wildlife_risk_harmless, R.color.safe, R.color.safe_tint),
        GIVE_ROOM(R.string.wildlife_risk_room, R.color.caution, R.color.caution_tint),
        CAN_STING(R.string.wildlife_risk_sting, R.color.danger, R.color.danger_tint),
        PROTECTED(R.string.wildlife_risk_protected, R.color.primary, R.color.accent_tint);

        @StringRes final int labelRes;
        @ColorRes final int colorRes;
        @ColorRes final int tintRes;

        Risk(int labelRes, int colorRes, int tintRes) {
            this.labelRes = labelRes;
            this.colorRes = colorRes;
            this.tintRes = tintRes;
        }
    }

    private static final class Species {
        @StringRes final int titleRes;
        @StringRes final int bodyRes;
        final Risk risk;
        /**
         * The first-aid lesson for this animal, or {@code null} where the app
         * has none. Meeting a jellyfish here and having to go hunting through
         * the Learn tab for what to do about it is the wrong way round, so a
         * card that has first aid links straight to it.
         */
        @Nullable final Sting firstAid;

        Species(int titleRes, int bodyRes, Risk risk) {
            this(titleRes, bodyRes, risk, null);
        }

        Species(int titleRes, int bodyRes, Risk risk, @Nullable Sting firstAid) {
            this.titleRes = titleRes;
            this.bodyRes = bodyRes;
            this.risk = risk;
            this.firstAid = firstAid;
        }
    }

    /** One stretch of coastline: the app's beaches on it, and what lives there. */
    private static final class Coast {
        @StringRes final int nameRes;
        /** Beach ids from {@link BeachRepository}, resolved to names at runtime. */
        final String[] beachIds;
        @StringRes final int noteTitleRes;
        @StringRes final int noteBodyRes;
        final Species[] species;

        Coast(int nameRes, String[] beachIds, int noteTitleRes, int noteBodyRes,
              Species[] species) {
            this.nameRes = nameRes;
            this.beachIds = beachIds;
            this.noteTitleRes = noteTitleRes;
            this.noteBodyRes = noteBodyRes;
            this.species = species;
        }
    }

    private static final Coast[] COASTS = {
        new Coast(R.string.wildlife_region_socal,
            new String[] {
                "CA1", "CA2", "CA3", "CA4", "CA5", "CA6", "CA7", "CA8", "CA9", "CA10", "CA11",
                "CA12", "CA13", "CA14", "CA15", "CA16", "CA17", "CA18", "CA19", "CA20", "CA21",
                "CA22", "CA23",
                "LA1", "LA2", "LA3", "LA4", "LA5", "LA6", "LA7", "LA8", "LA9", "LA10",
                "LA11", "LA12", "LA13", "LA14", "LA15", "LA16", "LA17", "LA18", "LA19",
                "LA20", "LA21", "LA22", "LA23", "LA24", "LA25", "LA26", "LA27", "LA28",
                // Santa Barbara and Ventura county, still inside the Bight.
                "CC1", "CC6", "CC7", "CC8", "CC9", "CC10", "CC11", "CC12" },
            R.string.wildlife_socal_note_title, R.string.wildlife_socal_note_body,
            new Species[] {
                new Species(R.string.wildlife_socal_ray_title,
                    R.string.wildlife_socal_ray_body, Risk.CAN_STING, Sting.STINGRAY),
                new Species(R.string.wildlife_socal_jelly_title,
                    R.string.wildlife_socal_jelly_body, Risk.CAN_STING, Sting.JELLYFISH),
                new Species(R.string.wildlife_socal_sealion_title,
                    R.string.wildlife_socal_sealion_body, Risk.GIVE_ROOM),
                new Species(R.string.wildlife_socal_seal_title,
                    R.string.wildlife_socal_seal_body, Risk.PROTECTED),
                new Species(R.string.wildlife_socal_leopard_title,
                    R.string.wildlife_socal_leopard_body, Risk.HARMLESS),
                new Species(R.string.wildlife_socal_velella_title,
                    R.string.wildlife_socal_velella_body, Risk.HARMLESS),
                new Species(R.string.wildlife_socal_grunion_title,
                    R.string.wildlife_socal_grunion_body, Risk.HARMLESS),
            }),

        // Everything north of Point Conception, where the water turns cold and
        // the animals change with it. The Southern California list stops at the
        // point for the same reason.
        new Coast(R.string.wildlife_region_norcal,
            new String[] {
                "CC2", "CC3", "CC4", "CC5",
                "CC13", "CC14", "CC15", "CC16", "CC17", "CC18", "CC19", "CC20",
                "SF1", "SF2", "SF3", "SF4", "SF5", "SF6", "SF7", "SF8", "SF9", "SF10",
                "NCA1", "NCA2", "NCA3" },
            R.string.wildlife_norcal_note_title, R.string.wildlife_norcal_note_body,
            new Species[] {
                new Species(R.string.wildlife_norcal_nettle_title,
                    R.string.wildlife_norcal_nettle_body, Risk.CAN_STING, Sting.JELLYFISH),
                new Species(R.string.wildlife_norcal_white_title,
                    R.string.wildlife_norcal_white_body, Risk.GIVE_ROOM),
                new Species(R.string.wildlife_norcal_eseal_title,
                    R.string.wildlife_norcal_eseal_body, Risk.GIVE_ROOM),
                new Species(R.string.wildlife_norcal_otter_title,
                    R.string.wildlife_norcal_otter_body, Risk.PROTECTED),
                new Species(R.string.wildlife_norcal_seal_title,
                    R.string.wildlife_norcal_seal_body, Risk.PROTECTED),
                new Species(R.string.wildlife_norcal_tidepool_title,
                    R.string.wildlife_norcal_tidepool_body, Risk.HARMLESS),
            }),

        new Coast(R.string.wildlife_region_florida,
            new String[] {
                "FL1", "FL2", "FL3", "FL8", "FL9", "FL10", "FL11", "FL14", "FL16" },
            R.string.wildlife_fl_note_title, R.string.wildlife_fl_note_body,
            new Species[] {
                new Species(R.string.wildlife_fl_manowar_title,
                    R.string.wildlife_fl_manowar_body, Risk.CAN_STING, Sting.MAN_O_WAR),
                new Species(R.string.wildlife_fl_sealice_title,
                    R.string.wildlife_fl_sealice_body, Risk.CAN_STING, Sting.SEA_LICE),
                new Species(R.string.wildlife_fl_ray_title,
                    R.string.wildlife_fl_ray_body, Risk.CAN_STING, Sting.STINGRAY),
                new Species(R.string.wildlife_fl_blacktip_title,
                    R.string.wildlife_fl_blacktip_body, Risk.GIVE_ROOM),
                new Species(R.string.wildlife_fl_turtle_title,
                    R.string.wildlife_fl_turtle_body, Risk.PROTECTED),
                new Species(R.string.wildlife_fl_moon_title,
                    R.string.wildlife_fl_moon_body, Risk.HARMLESS, Sting.JELLYFISH),
            }),

        new Coast(R.string.wildlife_region_gulf,
            new String[] {
                "FL4", "FL5", "FL6", "FL7", "FL12", "FL13", "FL15",
                "TX1", "TX2", "TX3", "TX4", "LOU1", "MS1", "AL1", "AL2" },
            R.string.wildlife_gulf_note_title, R.string.wildlife_gulf_note_body,
            new Species[] {
                new Species(R.string.wildlife_gulf_ray_title,
                    R.string.wildlife_gulf_ray_body, Risk.CAN_STING, Sting.STINGRAY),
                new Species(R.string.wildlife_gulf_manowar_title,
                    R.string.wildlife_gulf_manowar_body, Risk.CAN_STING, Sting.MAN_O_WAR),
                new Species(R.string.wildlife_gulf_dolphin_title,
                    R.string.wildlife_gulf_dolphin_body, Risk.PROTECTED),
                new Species(R.string.wildlife_gulf_manatee_title,
                    R.string.wildlife_gulf_manatee_body, Risk.PROTECTED),
                new Species(R.string.wildlife_gulf_turtle_title,
                    R.string.wildlife_gulf_turtle_body, Risk.PROTECTED),
                new Species(R.string.wildlife_gulf_sargassum_title,
                    R.string.wildlife_gulf_sargassum_body, Risk.HARMLESS),
            }),

        new Coast(R.string.wildlife_region_northeast,
            new String[] {
                "NY1", "NY2", "NY3", "NY4", "NY5", "NY6",
                "NJ1", "NJ2", "NJ3", "NJ4", "NJ5",
                "CT1", "RI1", "RI2", "MA1", "MA2", "MA3", "MA4", "MA5", "NH1", "ME1", "ME2", "ME3" },
            R.string.wildlife_ne_note_title, R.string.wildlife_ne_note_body,
            new Species[] {
                new Species(R.string.wildlife_ne_lionsmane_title,
                    R.string.wildlife_ne_lionsmane_body, Risk.CAN_STING, Sting.JELLYFISH),
                new Species(R.string.wildlife_ne_nettle_title,
                    R.string.wildlife_ne_nettle_body, Risk.CAN_STING, Sting.JELLYFISH),
                new Species(R.string.wildlife_ne_bluefish_title,
                    R.string.wildlife_ne_bluefish_body, Risk.GIVE_ROOM),
                new Species(R.string.wildlife_ne_seal_title,
                    R.string.wildlife_ne_seal_body, Risk.PROTECTED),
                new Species(R.string.wildlife_ne_dogfish_title,
                    R.string.wildlife_ne_dogfish_body, Risk.HARMLESS),
                new Species(R.string.wildlife_ne_horseshoe_title,
                    R.string.wildlife_ne_horseshoe_body, Risk.HARMLESS),
            }),

        new Coast(R.string.wildlife_region_pnw,
            new String[] {
                "OR1", "OR2", "OR3", "OR4", "OR5", "WA1", "WA2", "WA3", "WA4", "WA5" },
            R.string.wildlife_pnw_note_title, R.string.wildlife_pnw_note_body,
            new Species[] {
                new Species(R.string.wildlife_pnw_jelly_title,
                    R.string.wildlife_pnw_jelly_body, Risk.CAN_STING, Sting.JELLYFISH),
                new Species(R.string.wildlife_pnw_seal_title,
                    R.string.wildlife_pnw_seal_body, Risk.PROTECTED),
                new Species(R.string.wildlife_pnw_puffin_title,
                    R.string.wildlife_pnw_puffin_body, Risk.PROTECTED),
                new Species(R.string.wildlife_pnw_tidepool_title,
                    R.string.wildlife_pnw_tidepool_body, Risk.HARMLESS),
                new Species(R.string.wildlife_pnw_eagle_title,
                    R.string.wildlife_pnw_eagle_body, Risk.HARMLESS),
                new Species(R.string.wildlife_pnw_whale_title,
                    R.string.wildlife_pnw_whale_body, Risk.HARMLESS),
            }),
    };

    private static final String STATE_COAST = "selectedCoast";

    private final List<TextView> coastChips = new ArrayList<>();
    private int selectedCoast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wildlife_guide);

        EdgeToEdge.applyTopInset(findViewById(R.id.wildlifeHeader));
        EdgeToEdge.applyBottomInset(findViewById(R.id.wildlifeScrollView));

        findViewById(R.id.wildlifeBackButton).setOnClickListener(
            v -> getOnBackPressedDispatcher().onBackPressed());


        LayoutInflater inflater = LayoutInflater.from(this);
        buildCoastChips(inflater);
        if (savedInstanceState != null) {
            selectedCoast = savedInstanceState.getInt(STATE_COAST, 0);
        }
        showCoast(selectedCoast);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_COAST, selectedCoast);
    }

    private void buildCoastChips(LayoutInflater inflater) {
        ViewGroup row = findViewById(R.id.wildlifeCoastRow);
        for (int i = 0; i < COASTS.length; i++) {
            TextView chip = (TextView) inflater.inflate(R.layout.item_coast_chip, row, false);
            chip.setText(COASTS[i].nameRes);

            final int index = i;
            chip.setOnClickListener(v -> showCoast(index));

            coastChips.add(chip);
            row.addView(chip);
        }
    }

    private void showCoast(int index) {
        selectedCoast = index;
        Coast coast = COASTS[index];

        for (int i = 0; i < coastChips.size(); i++) {
            paintChip(coastChips.get(i), i == index);
        }

        TextView beaches = findViewById(R.id.wildlifeBeaches);
        String named = namesOf(coast.beachIds);
        if (named.isEmpty()) {
            beaches.setVisibility(View.GONE);
        } else {
            beaches.setVisibility(View.VISIBLE);
            beaches.setText(getString(R.string.wildlife_beaches_label, named));
        }

        View note = findViewById(R.id.wildlifeNote);
        if (coast.noteTitleRes == 0) {
            note.setVisibility(View.GONE);
        } else {
            note.setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.wildlifeNoteTitle)).setText(coast.noteTitleRes);
            ((TextView) findViewById(R.id.wildlifeNoteBody)).setText(coast.noteBodyRes);
        }

        ViewGroup list = findViewById(R.id.wildlifeSpeciesList);
        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Species species : coast.species) {
            View card = inflater.inflate(R.layout.item_species, list, false);
            ((TextView) card.findViewById(R.id.speciesName)).setText(species.titleRes);
            ((TextView) card.findViewById(R.id.speciesBody)).setText(species.bodyRes);
            paintTag(card.findViewById(R.id.speciesRisk), species.risk, species.risk.labelRes);

            if (species.firstAid != null) {
                card.findViewById(R.id.speciesFirstAid).setVisibility(View.VISIBLE);
                card.setClickable(true);
                card.setFocusable(true);
                card.setOnClickListener(
                    v -> startActivity(StingGuideActivity.intentFor(this, species.firstAid)));
            }

            list.addView(card);
        }
    }

    /** The selected coast fills with the brand colour; the rest stay hairlines. */
    private void paintChip(TextView chip, boolean selected) {
        int color = ContextCompat.getColor(this,
            selected ? R.color.primary : R.color.surface);
        int edge = ContextCompat.getColor(this,
            selected ? R.color.primary : R.color.divider);
        chip.setTextColor(ContextCompat.getColor(this,
            selected ? R.color.on_primary : R.color.text_secondary));

        Drawable background = chip.getBackground();
        if (background instanceof GradientDrawable) {
            GradientDrawable shape = (GradientDrawable) background.mutate();
            shape.setColor(color);
            shape.setStroke(Math.round(getResources().getDisplayMetrics().density), edge);
        }
    }

    /** The tag always carries its meaning in words, never in colour alone. */
    private void paintTag(TextView tag, Risk risk, @StringRes int labelRes) {
        tag.setText(labelRes);
        tag.setTextColor(ContextCompat.getColor(this, risk.colorRes));

        Drawable background = tag.getBackground();
        if (background instanceof GradientDrawable) {
            ((GradientDrawable) background.mutate())
                .setColor(ContextCompat.getColor(this, risk.tintRes));
        }
    }

    /** Beaches named outright before the label falls back to counting them. */
    private static final int NAMED_BEACHES = 4;

    /**
     * Beach names for the coast, in the repository's own order. Ids that are
     * no longer in the repository simply drop out, so removing a beach from
     * the app cannot leave this guide naming one that isn't there.
     *
     * <p>A coast can cover twenty beaches, and twenty names is a paragraph
     * where a line will do, so past a handful the rest are counted instead.
     */
    private String namesOf(String[] beachIds) {
        List<String> wanted = Arrays.asList(beachIds);
        List<String> names = new ArrayList<>();
        for (Beach beach : BeachRepository.getInstance().getBeaches()) {
            if (wanted.contains(beach.getId())) {
                names.add(beach.getName());
            }
        }
        if (names.size() <= NAMED_BEACHES) {
            return TextUtils.join(", ", names);
        }
        return getString(R.string.wildlife_beaches_and_more,
            TextUtils.join(", ", names.subList(0, NAMED_BEACHES)),
            names.size() - NAMED_BEACHES);
    }
}
