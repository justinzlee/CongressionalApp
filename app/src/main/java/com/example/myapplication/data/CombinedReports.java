package com.example.myapplication.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.StringRes;

import com.example.myapplication.R;
import com.example.myapplication.model.Beach;
import com.example.myapplication.model.RatingBlend;
import com.example.myapplication.model.RockRating;
import com.example.myapplication.model.SeagullRating;
import com.example.myapplication.model.WildlifeTally;
import com.example.myapplication.network.EBirdClient;
import com.example.myapplication.network.INaturalistClient;
import com.example.myapplication.network.ShoreSurfaceClient;

/**
 * What a beach's rocks, gulls and wildlife come out as once the real-world
 * starting point and this phone's own reports are put together.
 *
 * <p>The starting point is the same for everyone — OpenStreetMap for what the
 * shore is made of, eBird for how many gulls were counted, iNaturalist for
 * what animals were photographed — and is fetched in the background and
 * cached. What somebody files on this phone is then weighed against it by
 * {@link RatingBlend}, or for wildlife added to it, and every screen that reads
 * a rating reads it from here, so the beach card, the hazard list and the
 * activity scores all move together when a report comes in.
 *
 * <p>The reading methods never touch the network: they combine whatever the
 * caches hold, so the hazard list and the ranking, which work out every beach
 * at once, can call them freely. {@link #refresh} is what fills the caches.
 */
public final class CombinedReports {

    /** One combined rating, and where it came from. */
    public static final class Rating {
        /** On the rating's own scale, or {@code -1} when there is nothing at all. */
        public final int value;
        public final boolean fromBase;
        public final boolean fromUser;
        /** The line naming the source, or 0 when it is only this phone's rating. */
        @StringRes public final int sourceRes;

        Rating(int value, boolean fromBase, boolean fromUser, @StringRes int sourceRes) {
            this.value = value;
            this.fromBase = fromBase;
            this.fromUser = fromUser;
            this.sourceRes = sourceRes;
        }

        public boolean isRated() {
            return value >= 0;
        }
    }

    private final RockRatingStore rocks;
    private final SeagullRatingStore gulls;
    private final SightingStore sightings;
    private final ShoreSurfaceClient shore;
    private final EBirdClient ebird;
    private final INaturalistClient inat;

    public CombinedReports(Context context) {
        rocks = new RockRatingStore(context);
        gulls = new SeagullRatingStore(context);
        sightings = new SightingStore(context);
        shore = new ShoreSurfaceClient(context);
        ebird = new EBirdClient(context);
        inat = new INaturalistClient(context);
    }

    public Rating rocks(String beachId) {
        int base = shore.cachedRating(beachId);
        int user = rocks.ratingFor(beachId);
        int value = RatingBlend.blend(base, user, age(rocks.ratedAt(beachId)),
            RatingBlend.ROCK_USER_WEIGHT, RatingBlend.ROCK_HALF_LIFE_DAYS);
        boolean fromBase = RockRating.isRated(base);
        boolean fromUser = RockRating.isRated(user);
        return new Rating(value, fromBase, fromUser, !fromBase ? 0
            : fromUser ? R.string.source_osm_and_you : R.string.source_osm);
    }

    public Rating gulls(String beachId) {
        int base = ebird.cachedGullLevel(beachId);
        int user = gulls.ratingFor(beachId);
        int value = RatingBlend.blend(base, user, age(gulls.ratedAt(beachId)),
            RatingBlend.GULL_USER_WEIGHT, RatingBlend.GULL_HALF_LIFE_DAYS);
        boolean fromBase = SeagullRating.isRated(base);
        boolean fromUser = SeagullRating.isRated(user);
        return new Rating(value, fromBase, fromUser, !fromBase ? 0
            : fromUser ? R.string.source_ebird_and_you : R.string.source_ebird);
    }

    /** The last fortnight's sightings: iNaturalist's, plus this phone's reports. */
    public WildlifeTally wildlife(String beachId) {
        WildlifeTally base = inat.cachedTally(beachId);
        return (base == null ? WildlifeTally.empty() : base)
            .plus(sightings.forBeach(beachId), System.currentTimeMillis());
    }

    /** Whether any of the fortnight's count came from iNaturalist rather than this phone. */
    public boolean wildlifeHasBase(String beachId) {
        WildlifeTally base = inat.cachedTally(beachId);
        return base != null && !base.isEmpty();
    }

    /**
     * Fetches whichever starting points are missing or stale, and runs {@code
     * onChanged} on the main thread after each one that arrives, so the screen
     * redraws as the answers come in rather than waiting for the slowest.
     */
    public void refresh(Beach beach, Runnable onChanged) {
        Handler main = new Handler(Looper.getMainLooper());
        Runnable post = () -> main.post(onChanged);
        double lat = beach.getLatitude();
        double lon = beach.getLongitude();
        shore.getRating(beach.getId(), lat, lon, rating -> post.run());
        ebird.getGullLevel(beach.getId(), lat, lon, level -> post.run());
        inat.getTally(beach.getId(), lat, lon, tally -> post.run());
    }

    private static long age(long ratedAt) {
        return ratedAt <= 0 ? 0 : System.currentTimeMillis() - ratedAt;
    }
}
