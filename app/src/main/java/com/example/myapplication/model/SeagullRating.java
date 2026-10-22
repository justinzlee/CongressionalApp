package com.example.myapplication.model;

import androidx.annotation.StringRes;

import com.example.myapplication.R;

/**
 * How many seagulls are about at a beach, on a five-step scale from none to
 * swarming.
 *
 * <p>Like {@link RockRating}, this is something no API reports and only
 * somebody standing on the sand can say. Unlike rocks it is not a number out of
 * ten: nobody counts gulls, and the steps that matter are the ones where what
 * you should do with your lunch changes. Each step therefore carries a word
 * for the label and a line of advice, and the stored value is just which step
 * it was.
 *
 * <p>Never a hazard. Gulls snatching sandwiches are a nuisance, and putting them
 * in the Alerts tab beside a rip-current warning would teach people to skim
 * both. Where the rating does count is in how good a beach is for a picnic —
 * see {@code ActivityScorer}.
 */
public final class SeagullRating {

    public static final int NONE = 0;
    public static final int MAX = 4;

    /** Nobody has said. Distinct from {@link #NONE}, which is a real answer. */
    public static final int UNRATED = -1;

    private SeagullRating() {
    }

    public static boolean isRated(int rating) {
        return rating >= NONE && rating <= MAX;
    }

    /** "A few", "Swarming". */
    @StringRes
    public static int labelRes(int rating) {
        switch (rating) {
            case 0: return R.string.gulls_level_none;
            case 1: return R.string.gulls_level_few;
            case 2: return R.string.gulls_level_plenty;
            case 3: return R.string.gulls_level_lots;
            default: return R.string.gulls_level_swarming;
        }
    }

    /** What the step means for anyone bringing food. */
    @StringRes
    public static int adviceRes(int rating) {
        switch (rating) {
            case 0: return R.string.gulls_advice_none;
            case 1: return R.string.gulls_advice_few;
            case 2: return R.string.gulls_advice_plenty;
            case 3: return R.string.gulls_advice_lots;
            default: return R.string.gulls_advice_swarming;
        }
    }

    /**
     * The step a gull count falls on: what eBird's birders recorded near the
     * beach recently, summed across every gull species. Zero is a real answer
     * — birders were there and listed no gulls — so it is {@link #NONE}, not
     * unrated.
     *
     * <p>The bands widen as they go because a count is noisier the bigger it
     * gets: nobody tells 60 gulls from 80, and everyone tells 2 from 20.
     */
    public static int levelForCount(int gulls) {
        if (gulls <= 0) return NONE;
        if (gulls <= 5) return 1;
        if (gulls <= 25) return 2;
        if (gulls <= 100) return 3;
        return MAX;
    }

    /**
     * 0-100 for a picnic, where 100 is no gulls at all. Falls by a quarter per
     * step, so a swarming beach scores nothing for it.
     */
    public static int picnicScore(int rating) {
        return 100 - rating * 25;
    }
}
