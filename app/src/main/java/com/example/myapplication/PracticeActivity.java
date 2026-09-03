package com.example.myapplication;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.data.PracticeStore;
import com.example.myapplication.util.EdgeToEdge;

import java.util.Arrays;

/**
 * Three questions a day, drawn from one pool and rotated by the date.
 *
 * <p>The lessons on the Learn tab are there for someone who goes looking. This
 * is for everyone else: a minute a day that puts the same handful of facts in
 * front of people often enough to stick, so they are already known on the day
 * they matter. Questions are deliberately gettable without having read
 * anything — a wrong answer teaches, it doesn't grade.
 */
public class PracticeActivity extends AppCompatActivity {

    /** How many questions a day. Enough to be worth opening, few enough to finish. */
    private static final int QUESTIONS_PER_DAY = 3;

    /** One question, its answers, and what to say once it has been answered. */
    private static final class Question {
        @StringRes final int questionRes;
        @StringRes final int[] optionsRes;
        final int correct;
        @StringRes final int explanationRes;

        Question(int questionRes, int[] optionsRes, int correct, int explanationRes) {
            this.questionRes = questionRes;
            this.optionsRes = optionsRes;
            this.correct = correct;
            this.explanationRes = explanationRes;
        }
    }

    /**
     * The pool. The right answer is not always first — it moves around so the
     * shape of the question, rather than its position, is what gets learned.
     */
    private static final Question[] POOL = {
        new Question(R.string.practice_q_rip_escape_q,
            new int[] { R.string.practice_q_rip_escape_a, R.string.practice_q_rip_escape_b,
                R.string.practice_q_rip_escape_c }, 0, R.string.practice_q_rip_escape_x),
        new Question(R.string.practice_q_thunder_q,
            new int[] { R.string.practice_q_thunder_b, R.string.practice_q_thunder_a,
                R.string.practice_q_thunder_c }, 1, R.string.practice_q_thunder_x),
        new Question(R.string.practice_q_shuffle_q,
            new int[] { R.string.practice_q_shuffle_b, R.string.practice_q_shuffle_c,
                R.string.practice_q_shuffle_a }, 2, R.string.practice_q_shuffle_x),
        new Question(R.string.practice_q_red_flag_q,
            new int[] { R.string.practice_q_red_flag_b, R.string.practice_q_red_flag_a,
                R.string.practice_q_red_flag_c }, 1, R.string.practice_q_red_flag_x),
        new Question(R.string.practice_q_jelly_rinse_q,
            new int[] { R.string.practice_q_jelly_rinse_a, R.string.practice_q_jelly_rinse_b,
                R.string.practice_q_jelly_rinse_c }, 0, R.string.practice_q_jelly_rinse_x),
        new Question(R.string.practice_q_child_reach_q,
            new int[] { R.string.practice_q_child_reach_b, R.string.practice_q_child_reach_a,
                R.string.practice_q_child_reach_c }, 1, R.string.practice_q_child_reach_x),
        new Question(R.string.practice_q_rip_look_q,
            new int[] { R.string.practice_q_rip_look_b, R.string.practice_q_rip_look_c,
                R.string.practice_q_rip_look_a }, 2, R.string.practice_q_rip_look_x),
        new Question(R.string.practice_q_sunscreen_q,
            new int[] { R.string.practice_q_sunscreen_a, R.string.practice_q_sunscreen_b,
                R.string.practice_q_sunscreen_c }, 0, R.string.practice_q_sunscreen_x),
        new Question(R.string.practice_q_seal_pup_q,
            new int[] { R.string.practice_q_seal_pup_b, R.string.practice_q_seal_pup_a,
                R.string.practice_q_seal_pup_c }, 1, R.string.practice_q_seal_pup_x),
        new Question(R.string.practice_q_purple_flag_q,
            new int[] { R.string.practice_q_purple_flag_b, R.string.practice_q_purple_flag_c,
                R.string.practice_q_purple_flag_a }, 2, R.string.practice_q_purple_flag_x),
        new Question(R.string.practice_q_float_q,
            new int[] { R.string.practice_q_float_a, R.string.practice_q_float_b,
                R.string.practice_q_float_c }, 0, R.string.practice_q_float_x),
        new Question(R.string.practice_q_uv_cloud_q,
            new int[] { R.string.practice_q_uv_cloud_b, R.string.practice_q_uv_cloud_a,
                R.string.practice_q_uv_cloud_c }, 1, R.string.practice_q_uv_cloud_x),
        new Question(R.string.practice_q_manowar_sand_q,
            new int[] { R.string.practice_q_manowar_sand_b, R.string.practice_q_manowar_sand_c,
                R.string.practice_q_manowar_sand_a }, 2, R.string.practice_q_manowar_sand_x),
        new Question(R.string.practice_q_help_signal_q,
            new int[] { R.string.practice_q_help_signal_a, R.string.practice_q_help_signal_b,
                R.string.practice_q_help_signal_c }, 0, R.string.practice_q_help_signal_x),
        new Question(R.string.practice_q_no_flags_q,
            new int[] { R.string.practice_q_no_flags_b, R.string.practice_q_no_flags_a,
                R.string.practice_q_no_flags_c }, 1, R.string.practice_q_no_flags_x),
        new Question(R.string.practice_q_hot_water_q,
            new int[] { R.string.practice_q_hot_water_b, R.string.practice_q_hot_water_c,
                R.string.practice_q_hot_water_a }, 2, R.string.practice_q_hot_water_x),
        new Question(R.string.practice_q_swim_alone_q,
            new int[] { R.string.practice_q_swim_alone_a, R.string.practice_q_swim_alone_b,
                R.string.practice_q_swim_alone_c }, 0, R.string.practice_q_swim_alone_x),
        new Question(R.string.practice_q_rain_water_q,
            new int[] { R.string.practice_q_rain_water_b, R.string.practice_q_rain_water_a,
                R.string.practice_q_rain_water_c }, 1, R.string.practice_q_rain_water_x),
        new Question(R.string.practice_q_jelly_pee_q,
            new int[] { R.string.practice_q_jelly_pee_b, R.string.practice_q_jelly_pee_c,
                R.string.practice_q_jelly_pee_a }, 2, R.string.practice_q_jelly_pee_x),
        new Question(R.string.practice_q_swim_between_q,
            new int[] { R.string.practice_q_swim_between_a, R.string.practice_q_swim_between_b,
                R.string.practice_q_swim_between_c }, 0, R.string.practice_q_swim_between_x),
        new Question(R.string.practice_q_feed_wildlife_q,
            new int[] { R.string.practice_q_feed_wildlife_b, R.string.practice_q_feed_wildlife_a,
                R.string.practice_q_feed_wildlife_c }, 1, R.string.practice_q_feed_wildlife_x),
        new Question(R.string.practice_q_urchin_spine_q,
            new int[] { R.string.practice_q_urchin_spine_b, R.string.practice_q_urchin_spine_c,
                R.string.practice_q_urchin_spine_a }, 2, R.string.practice_q_urchin_spine_x),
    };


    private static final String STATE_CHOSEN = "chosen";

    /** Which option was tapped for each of today's questions, -1 while unanswered. */
    private int[] chosen;
    private int answered;
    private int correct;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_practice);

        EdgeToEdge.applyTopInset(findViewById(R.id.practiceHeader));
        EdgeToEdge.applyBottomInset(findViewById(R.id.practiceScrollView));

        findViewById(R.id.practiceBackButton).setOnClickListener(
            v -> getOnBackPressedDispatcher().onBackPressed());

        // Rotating mid-round used to wipe the answers and the explanations
        // with them, which is the part worth reading.
        chosen = savedInstanceState != null
            ? savedInstanceState.getIntArray(STATE_CHOSEN) : null;

        buildQuestions(LayoutInflater.from(this));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putIntArray(STATE_CHOSEN, chosen);
    }


    private void buildQuestions(LayoutInflater inflater) {
        ViewGroup list = findViewById(R.id.practiceQuestions);
        Question[] todays = todaysQuestions();

        if (chosen == null || chosen.length != todays.length) {
            chosen = new int[todays.length];
            Arrays.fill(chosen, KnowledgeCheck.UNANSWERED);
        }
        int[] restoring = chosen.clone();

        for (int i = 0; i < todays.length; i++) {
            Question question = todays[i];
            View card = inflater.inflate(R.layout.item_practice_question, list, false);

            ((TextView) card.findViewById(R.id.practiceProgress))
                .setText(getString(R.string.practice_progress, i + 1, todays.length));
            ((TextView) card.findViewById(R.id.practiceQuestion)).setText(question.questionRes);
            ((TextView) card.findViewById(R.id.practiceExplanation))
                .setText(question.explanationRes);

            final int index = i;
            KnowledgeCheck.install(this,
                card.findViewById(R.id.practiceOptions),
                card.findViewById(R.id.practiceAnswer),
                card.findViewById(R.id.practiceAnswerTitle),
                question.optionsRes, question.correct,
                (chosenOption, wasCorrect) ->
                    onAnswered(index, chosenOption, wasCorrect, todays.length),
                restoring[i]);

            list.addView(card);
        }
    }

    /**
     * Also runs for each answer replayed while restoring, which is what rebuilds
     * the score and the finished state without any separate restore path.
     */
    private void onAnswered(int index, int chosenOption, boolean wasCorrect, int total) {
        chosen[index] = chosenOption;
        answered++;
        if (wasCorrect) {
            correct++;
        }
        if (answered < total) {
            return;
        }

        // All of today's are done: bank the streak and show the summary.
        new PracticeStore(this).markDoneToday();

        ((TextView) findViewById(R.id.practiceScore))
            .setText(getString(R.string.practice_score, correct, total));
        findViewById(R.id.practiceDone).setVisibility(View.VISIBLE);
    }

    /**
     * Today's slice of the pool. Walking the pool by date rather than picking
     * at random means no repeat within a day, none from yesterday either, and
     * everything comes round again in the same order — which is what makes it
     * revision rather than a shuffle.
     */
    private static Question[] todaysQuestions() {
        int count = Math.min(QUESTIONS_PER_DAY, POOL.length);
        Question[] todays = new Question[count];
        long start = PracticeStore.today() * count;
        for (int i = 0; i < count; i++) {
            todays[i] = POOL[(int) Math.floorMod(start + i, (long) POOL.length)];
        }
        return todays;
    }
}
