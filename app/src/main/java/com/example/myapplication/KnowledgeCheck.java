package com.example.myapplication;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

/**
 * One multiple-choice question: inflate the answers, mark the right one on the
 * first tap whichever was chosen, then lock it. Both the flag lesson and the
 * daily practice screen ask questions the same way, so the wiring lives here
 * rather than being copied into each.
 */
public final class KnowledgeCheck {

    /**
     * Told when a question is answered, so a quiz can keep score — and which
     * option was tapped, so it can put the answer back after a rotation.
     */
    public interface OnAnsweredListener {
        void onAnswered(int chosenOption, boolean correct);
    }

    private final Context context;
    private final View[] optionRows;
    private final int correctOption;
    private final View answerPanel;
    private final TextView answerTitle;
    @Nullable private final OnAnsweredListener listener;
    private boolean answered;

    /**
     * Builds a question out of views already in the layout, looked up by id.
     *
     * @param optionsId     empty container the answer rows are added to
     * @param answerId      panel revealed once the question is answered
     * @param answerTitleId headline inside that panel, set to right/wrong
     * @param optionsRes    answers, in the order they are shown
     * @param correctOption index into optionsRes of the right answer
     */
    public static void install(android.app.Activity activity, int optionsId, int answerId,
                               int answerTitleId, @StringRes int[] optionsRes,
                               int correctOption) {
        install(activity, activity.findViewById(optionsId), activity.findViewById(answerId),
            activity.findViewById(answerTitleId), optionsRes, correctOption, null);
    }

    /**
     * Builds a question out of views passed directly, for screens that inflate
     * several questions rather than laying one out in XML.
     */
    public static void install(Context context, ViewGroup options, View answerPanel,
                               TextView answerTitle, @StringRes int[] optionsRes,
                               int correctOption, @Nullable OnAnsweredListener listener) {
        install(context, options, answerPanel, answerTitle, optionsRes,
            correctOption, listener, UNANSWERED);
    }

    /** Passed as {@code alreadyChosen} for a question nobody has answered yet. */
    public static final int UNANSWERED = -1;

    /**
     * As above, but replays an answer already given — for a screen rebuilding
     * itself after a rotation, so the marks and the explanation come back
     * rather than the question silently resetting.
     */
    public static void install(Context context, ViewGroup options, View answerPanel,
                               TextView answerTitle, @StringRes int[] optionsRes,
                               int correctOption, @Nullable OnAnsweredListener listener,
                               int alreadyChosen) {
        KnowledgeCheck check = new KnowledgeCheck(context, options, answerPanel,
            answerTitle, optionsRes, correctOption, listener);
        if (alreadyChosen != UNANSWERED) {
            check.reveal(alreadyChosen);
        }
    }

    private KnowledgeCheck(Context context, ViewGroup options, View answerPanel,
                           TextView answerTitle, @StringRes int[] optionsRes,
                           int correctOption, @Nullable OnAnsweredListener listener) {
        this.context = context;
        this.correctOption = correctOption;
        this.optionRows = new View[optionsRes.length];
        this.answerPanel = answerPanel;
        this.answerTitle = answerTitle;
        this.listener = listener;

        LayoutInflater inflater = LayoutInflater.from(context);
        for (int i = 0; i < optionsRes.length; i++) {
            View row = inflater.inflate(R.layout.item_quiz_option, options, false);
            ((TextView) row.findViewById(R.id.quizOptionLetter))
                .setText(String.valueOf((char) ('A' + i)));
            ((TextView) row.findViewById(R.id.quizOptionText)).setText(optionsRes[i]);

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

        mark(optionRows[correctOption], R.drawable.ic_check_circle, R.color.safe);
        if (chosen != correctOption) {
            mark(optionRows[chosen], R.drawable.ic_x_circle, R.color.danger);
        }
        for (View row : optionRows) {
            row.setClickable(false);
        }

        answerTitle.setText(chosen == correctOption
            ? R.string.flag_quiz_correct : R.string.flag_quiz_incorrect);
        answerPanel.setVisibility(View.VISIBLE);

        if (listener != null) {
            listener.onAnswered(chosen, chosen == correctOption);
        }
    }

    private void mark(View row, int iconRes, int colorRes) {
        ImageView markView = row.findViewById(R.id.quizOptionMark);
        markView.setImageResource(iconRes);
        ImageViewCompat.setImageTintList(markView,
            ColorStateList.valueOf(ContextCompat.getColor(context, colorRes)));
        markView.setVisibility(View.VISIBLE);
    }
}
