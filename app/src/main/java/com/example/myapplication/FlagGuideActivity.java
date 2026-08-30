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

import com.example.myapplication.util.EdgeToEdge;

/**
 * What each lifeguard warning flag means, and a one-question check at the end.
 * Static safety guidance, like the rip-current guide — no live data.
 */
public class FlagGuideActivity extends AppCompatActivity {

    /** A row of the flag key. Colour never carries the meaning on its own:
     *  every row spells the flag out in its title and in its icon label. */
    private static final class Flag {
        final int titleRes;
        final int bodyRes;
        final int colorRes;
        final int colorNameRes;
        /** Double red flies two flags; every other warning flies one. */
        final boolean doubled;

        Flag(int titleRes, int bodyRes, int colorRes, int colorNameRes, boolean doubled) {
            this.titleRes = titleRes;
            this.bodyRes = bodyRes;
            this.colorRes = colorRes;
            this.colorNameRes = colorNameRes;
            this.doubled = doubled;
        }
    }

    /** In the order the flags escalate, closed first. */
    private static final Flag[] FLAGS = {
        new Flag(R.string.flag_double_red_title, R.string.flag_double_red_body,
            R.color.flag_red, R.string.flag_color_red, true),
        new Flag(R.string.flag_red_title, R.string.flag_red_body,
            R.color.flag_red, R.string.flag_color_red, false),
        new Flag(R.string.flag_yellow_title, R.string.flag_yellow_body,
            R.color.flag_yellow, R.string.flag_color_yellow, false),
        new Flag(R.string.flag_green_title, R.string.flag_green_body,
            R.color.flag_green, R.string.flag_color_green, false),
        new Flag(R.string.flag_purple_title, R.string.flag_purple_body,
            R.color.flag_purple, R.string.flag_color_purple, false),
    };

    private static final int[] QUIZ_OPTIONS = {
        R.string.flag_quiz_option_a,
        R.string.flag_quiz_option_b,
        R.string.flag_quiz_option_c,
    };

    private static final int CORRECT_OPTION = 0;

    private final View[] optionRows = new View[QUIZ_OPTIONS.length];
    private boolean answered;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flag_guide);

        EdgeToEdge.applyTopInset(findViewById(R.id.flagGuideHeader));
        EdgeToEdge.applyBottomInset(findViewById(R.id.flagGuideScrollView));

        findViewById(R.id.flagGuideBackButton).setOnClickListener(
            v -> getOnBackPressedDispatcher().onBackPressed());

        LayoutInflater inflater = LayoutInflater.from(this);
        buildFlagKey(inflater);
        buildQuiz(inflater);
    }

    private void buildFlagKey(LayoutInflater inflater) {
        ViewGroup list = findViewById(R.id.flagList);
        for (Flag flag : FLAGS) {
            View row = inflater.inflate(R.layout.item_flag, list, false);
            ((TextView) row.findViewById(R.id.flagTitle)).setText(flag.titleRes);
            ((TextView) row.findViewById(R.id.flagBody)).setText(flag.bodyRes);

            ColorStateList tint = ColorStateList.valueOf(
                ContextCompat.getColor(this, flag.colorRes));
            ImageView primary = row.findViewById(R.id.flagIconPrimary);
            ImageViewCompat.setImageTintList(primary, tint);

            if (flag.doubled) {
                ImageView secondary = row.findViewById(R.id.flagIconSecondary);
                ImageViewCompat.setImageTintList(secondary, tint);
                secondary.setVisibility(View.VISIBLE);
            }

            // Screen readers get the flag by name, not by the colour they
            // cannot see; the pair of icons is announced once, together.
            row.findViewById(R.id.flagIcons).setContentDescription(flag.doubled
                ? getString(R.string.flag_double_red_icon_desc)
                : getString(R.string.flag_icon_desc, getString(flag.colorNameRes)));

            list.addView(row);
        }
    }

    private void buildQuiz(LayoutInflater inflater) {
        ViewGroup options = findViewById(R.id.flagQuizOptions);
        for (int i = 0; i < QUIZ_OPTIONS.length; i++) {
            View row = inflater.inflate(R.layout.item_quiz_option, options, false);
            ((TextView) row.findViewById(R.id.quizOptionLetter))
                .setText(String.valueOf((char) ('A' + i)));
            ((TextView) row.findViewById(R.id.quizOptionText)).setText(QUIZ_OPTIONS[i]);

            final int chosen = i;
            row.setOnClickListener(v -> reveal(chosen));

            optionRows[i] = row;
            options.addView(row);
        }
    }

    /** Marks the right answer either way, so a wrong tap still teaches it. */
    private void reveal(int chosen) {
        if (answered) {
            return;
        }
        answered = true;

        mark(optionRows[CORRECT_OPTION], R.drawable.ic_check_circle, R.color.safe);
        if (chosen != CORRECT_OPTION) {
            mark(optionRows[chosen], R.drawable.ic_x_circle, R.color.danger);
        }
        for (View row : optionRows) {
            row.setClickable(false);
        }

        ((TextView) findViewById(R.id.flagQuizAnswerTitle)).setText(chosen == CORRECT_OPTION
            ? R.string.flag_quiz_correct : R.string.flag_quiz_incorrect);
        findViewById(R.id.flagQuizAnswer).setVisibility(View.VISIBLE);
    }

    private void mark(View row, int iconRes, int colorRes) {
        ImageView markView = row.findViewById(R.id.quizOptionMark);
        markView.setImageResource(iconRes);
        ImageViewCompat.setImageTintList(markView,
            ColorStateList.valueOf(ContextCompat.getColor(this, colorRes)));
        markView.setVisibility(View.VISIBLE);
    }
}
