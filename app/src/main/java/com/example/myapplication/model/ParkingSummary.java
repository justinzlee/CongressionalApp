package com.example.myapplication.model;

import androidx.annotation.StringRes;

import com.example.myapplication.R;

/**
 * How hard it looks to park at a beach, from what OpenStreetMap knows about the
 * car parks around it.
 *
 * <p>Read the limits before trusting the number. This counts parking that
 * exists; it cannot see whether any of it is free right now. There is no public
 * feed of live beach-lot occupancy, so "easy to park" here means "there is a lot
 * of parking near this beach and much of it is public", not "you will get a
 * space at noon on the Fourth of July". The two come apart exactly when it
 * matters most, which is why the screen says how many spaces were found rather
 * than just showing a verdict.
 *
 * <p>Cost is thinner still. OpenStreetMap records whether a car park charges,
 * far more often than it records how much, so this reports free versus paid and
 * does not invent a price. A beach whose lots are all untagged comes back with
 * low confidence and says so, rather than being scored as if it had none.
 */
public final class ParkingSummary {

    /** How far out to look for parking. Roughly a ten-minute walk. */
    public static final int SEARCH_RADIUS_M = 1200;

    /** Below this many tagged features, the reading is a guess worth flagging. */
    private static final int CONFIDENT_FROM = 3;

    private final int lots;
    private final int freeLots;
    private final int paidLots;
    private final int knownCapacity;
    private final int streetSide;

    public ParkingSummary(int lots, int freeLots, int paidLots, int knownCapacity, int streetSide) {
        this.lots = lots;
        this.freeLots = freeLots;
        this.paidLots = paidLots;
        this.knownCapacity = knownCapacity;
        this.streetSide = streetSide;
    }

    /** Every public car park found within {@link #SEARCH_RADIUS_M}. */
    public int getLots() { return lots; }

    /** Of those, how many are recorded as charging nothing. */
    public int getFreeLots() { return freeLots; }

    /** And how many charge. A lot with no fee tag is in neither count. */
    public int getPaidLots() { return paidLots; }

    /** Spaces, added up across the lots that record a capacity. Often 0. */
    public int getKnownCapacity() { return knownCapacity; }

    /** Stretches of marked street parking, which is what fills up first. */
    public int getStreetSide() { return streetSide; }

    public boolean isEmpty() { return lots == 0 && streetSide == 0; }

    /**
     * Whether enough of the surrounding parking is actually tagged to say
     * anything. OpenStreetMap coverage is uneven — a well-mapped city beach has
     * every lot with a fee and a capacity, a rural one has a single untagged
     * rectangle — and a confident-sounding score off one feature would be
     * worse than admitting the gap.
     */
    public boolean isConfident() { return lots + streetSide >= CONFIDENT_FROM; }

    /** "12 car parks nearby", or "nothing mapped near here". */
    @StringRes
    public int getSummaryRes() {
        if (isEmpty()) return R.string.parking_none_mapped;
        if (!isConfident()) return R.string.parking_thin;
        if (freeLots > paidLots) return R.string.parking_mostly_free;
        if (paidLots > 0 && freeLots == 0) return R.string.parking_paid;
        return R.string.parking_mixed;
    }

    /**
     * 0-100 for the activity scores: how easy this beach looks to arrive at.
     *
     * <p>Supply carries most of it, because more parking really is easier
     * parking, with a bonus for it being free. A beach nothing is mapped near
     * scores at the middle rather than at zero — an unmapped beach is an
     * unknown, and scoring it as though it had no parking would push every
     * rural beach to the bottom of the picnic ranking for a reason that is
     * about OpenStreetMap rather than about the beach.
     */
    public int easeScore() {
        if (isEmpty()) return 50;

        int supply = Math.min(60, (lots * 12) + (streetSide * 4));
        int capacityBonus = knownCapacity >= 400 ? 20 : knownCapacity >= 150 ? 12 : 0;

        int costBonus = 0;
        if (freeLots > 0 && paidLots == 0) {
            costBonus = 20;
        } else if (freeLots > paidLots) {
            costBonus = 12;
        } else if (freeLots > 0) {
            costBonus = 6;
        }

        int score = supply + capacityBonus + costBonus;
        // A thin reading is pulled back towards the middle rather than trusted.
        if (!isConfident()) {
            score = (score + 50) / 2;
        }
        return Math.min(100, score);
    }
}
