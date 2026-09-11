package com.example.myapplication.data;

import androidx.annotation.VisibleForTesting;

import com.example.myapplication.model.*;
import com.example.myapplication.network.WeatherClient;
import com.example.myapplication.util.UnitConverter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class BeachRepository {

    /**
     * How long fetched conditions are treated as current. Every screen used to
     * build its own repository and refetch the whole beach list on entry, so
     * conditions reset to "--" each time you came back to a tab and the wait
     * started over. One shared instance keeps what it has already learned;
     * this window then stops rapid tab-switching from re-hitting the network
     * for readings that only move every fifteen minutes upstream anyway.
     *
     * <p>Public so the beach screen, which fetches its one beach itself, holds
     * to the same window.
     */
    public static final long FRESH_FOR_MS = 5 * 60 * 1000L;

    private static volatile BeachRepository instance;

    /**
     * The one repository for the process. Conditions fetched on one screen are
     * cached on it, so a screen opening second already has them.
     */
    public static BeachRepository getInstance() {
        BeachRepository local = instance;
        if (local == null) {
            synchronized (BeachRepository.class) {
                local = instance;
                if (local == null) {
                    local = new BeachRepository();
                    instance = local;
                }
            }
        }
        return local;
    }

    /**
     * A standalone repository with its own copy of the beach list, for tests
     * that filter and search without touching — or being touched by — the
     * shared instance. Production code goes through {@link #getInstance()}, so
     * that conditions fetched on one screen are already there on the next.
     */
    @VisibleForTesting
    public static BeachRepository newForTesting() {
        return new BeachRepository();
    }

    private final List<Beach> beaches;
    private final WeatherClient weatherClient = new WeatherClient();

    /** Guards against a second screen kicking off a duplicate fan-out. */
    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    /** Listeners waiting on the in-flight refresh; cleared when it finishes. */
    private final List<OnDataRefreshListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicInteger outstanding = new AtomicInteger();
    private volatile long refreshedAt;

    public interface OnDataRefreshListener {
        void onDataRefreshed();
    }

    private BeachRepository() {
        beaches = new ArrayList<>();

        // Conditions start null so the live fetch is what fills them.
        //
        // Photos are curated Wikimedia Commons files, one per beach, each
        // checked by eye to be a picture of that beach. Beaches without one
        // carry null and fall back to the drawn placeholder.
        //
        // They used to be generic stock photos, which showed an unrelated
        // beach under the name of this one, and refreshAllData() then tried to
        // improve on that by looking the beach name up on Wikipedia. That
        // search is unreliable for exactly the names in this list: it returned
        // the Hotel del Coronado for Coronado Beach, a car park for Huntington
        // Beach, Ocean Drive for South Beach, and a cartographic *map* for
        // Galveston Island. A wrong photo is worse than no photo on a screen
        // people use to decide whether to get in the water, so the lookup no
        // longer runs and the list below is the only source.
        //
        // The last argument is the NOAA station each beach's tides are read
        // from. It is deliberately not always the nearest one: the nearest is
        // often up a river or behind an inlet, where the tide turns well after
        // it does on the open beach — Fort Pond Bay, three kilometres from
        // Ditch Plains, turns over an hour later than the ocean side. Each
        // station was checked against the open-coast stations either side of it.
        //
        // The coordinates were checked against OpenStreetMap's mapped sand and
        // each sits on it or within a few hundred metres. They are what the
        // SOS sheet sends as the user's location, so a point in the middle of
        // town or out in the bay is not a harmless approximation.
        beaches.add(new Beach("CA1", "La Jolla Shores", 32.8589, -117.2558,
            Collections.singletonList(new Hazard(HazardType.RIP_CURRENT, Severity.MODERATE, "Strong rips near the pier.", Collections.singletonList("Swim near lifeguard"))),
            null,
            // The City of San Diego's address for its La Jolla Shores
            // lifeguards, 8260 Camino del Oro. It used to sit on the beach's
            // own pin, three hundred metres up the sand.
            Collections.singletonList(new HelpLocation("La Jolla Shores Lifeguard Station", HelpType.LIFEGUARD_TOWER, 32.8562, -117.2569)),
            "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c3/La_Jolla_Shores_photo_Don_Ramey_Logan.jpg/1280px-La_Jolla_Shores_photo_Don_Ramey_Logan.jpg",
            "9410230"));

        beaches.add(new Beach("CA2", "Coronado Beach", 32.6859, -117.1831, null, null, null,
            "https://img2.10bestmedia.com/Images/Photos/422054/Coronado-Beach_54_990x660.jpg?auto=webp&width=800&quality=75",
            "TWC0405"));
        beaches.add(new Beach("CA3", "Ocean Beach", 32.7495, -117.2553, null, null, null,
            "https://upload.wikimedia.org/wikipedia/commons/9/9f/Sunset_pier.jpg", "TWC0405"));
        beaches.add(new Beach("CA4", "Carlsbad State Beach", 33.1527, -117.3503, null, null, null,
            "https://upload.wikimedia.org/wikipedia/commons/c/ca/Carlsbad_State_Beach.jpg", "9410230"));
        beaches.add(new Beach("CA5", "Moonlight State Beach", 33.0484, -117.2981, null, null, null,
            "https://upload.wikimedia.org/wikipedia/commons/thumb/2/28/Moonlight_Beach%2C_view_from_the_south.JPG/1280px-Moonlight_Beach%2C_view_from_the_south.JPG",
            "9410230"));

        beaches.add(new Beach("LA1", "Santa Monica Pier", 34.0099, -118.4960, null, null, null,
            "https://upload.wikimedia.org/wikipedia/commons/thumb/3/36/Santa_monica_pier_entrance_evening.jpg/1280px-Santa_monica_pier_entrance_evening.jpg",
            "9410840"));
        beaches.add(new Beach("LA2", "Huntington Beach", 33.6557, -118.0043, null, null, null,
            null, "9410583"));
        beaches.add(new Beach("LA3", "Malibu Lagoon", 34.0322, -118.6775, null, null, null,
            null, "9410840"));
        beaches.add(new Beach("LA4", "Venice Beach", 33.9850, -118.4695, null, null, null,
            null, "9410840"));

        // South Beach used to carry a hand-written "High UV today" at extreme
        // severity. Written once and never cleared, it held the beach at "Not
        // safe to swim" around the clock, midnight included, and hid the live
        // UV reading behind it. UV comes from the live reading here as it does
        // everywhere else.
        beaches.add(new Beach("FL1", "South Beach", 25.7826, -80.1285,
            null,
            null,
            // Ocean Rescue's headquarters on the Lummus Park boardwalk. It was
            // recorded as a hospital, at the beach's own coordinates.
            Collections.singletonList(new HelpLocation("Miami Beach Ocean Rescue", HelpType.LIFEGUARD_TOWER, 25.7803, -80.1302)),
            null, "8723170"));

        // The Commons photograph this used was of Haulover's clothing-optional
        // section, so it draws the placeholder instead.
        beaches.add(new Beach("FL2", "Haulover Beach", 25.9035, -80.1213, null, null, null,
            null, "8723080"));
        beaches.add(new Beach("FL4", "Clearwater Beach", 27.9765, -82.8318, null, null, null,
            "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cf/Clearwater_Beach_%2854357452307%29.jpg/1280px-Clearwater_Beach_%2854357452307%29.jpg",
            "8726724"));
        beaches.add(new Beach("NY1", "Rockaway Beach", 40.5843, -73.8197, null, null, null,
            "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b9/Rockaway_Beach_Queens.JPG/1280px-Rockaway_Beach_Queens.JPG",
            "8516881"));
        beaches.add(new Beach("NY2", "Jones Beach State Park", 40.5909, -73.5121, null, null, null,
            null, "8516385"));
        beaches.add(new Beach("TX1", "Galveston Island", 29.2876, -94.7906, null, null, null,
            null, "8771510"));
        beaches.add(new Beach("TX2", "South Padre Island", 26.1118, -97.1681, null, null, null,
            null, "8779750"));
        beaches.add(new Beach("OR1", "Cannon Beach", 45.8918, -123.9615, null, null, null,
            "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e3/Haystack_Rock%2C_Oregon_%28drone_photograph%29.jpg/1280px-Haystack_Rock%2C_Oregon_%28drone_photograph%29.jpg",
            "9437585"));

        // The rest of the coastline. Everything above was Southern California
        // with three other states for company, so a user anywhere else opened
        // the app to beaches two thousand miles away. These carry no photo —
        // the curated Commons files above were each checked by eye and there
        // is no such file for these yet, so they draw the placeholder, which
        // is honest in a way a stock photo of somewhere else is not.
        //
        // Note the id prefixes are historical, not postal: "LA" here has
        // always meant Los Angeles, so Louisiana is "LOU".

        // San Diego County
        add("CA6", "Pacific Beach", 32.7970, -117.2560, "9410230");
        add("CA7", "Mission Beach", 32.7708, -117.2520, "9410230");
        add("CA8", "Del Mar City Beach", 32.9595, -117.2673, "9410230");
        add("CA9", "Torrey Pines State Beach", 32.9337, -117.2590, "9410230");
        add("CA10", "Imperial Beach", 32.5793, -117.1350, "9410120");
        add("CA11", "Oceanside Beach", 33.1928, -117.3861, "TWC0419");

        // Los Angeles and Orange County
        add("LA5", "Newport Beach", 33.6076, -117.9290, "9410583");
        add("LA6", "Laguna Main Beach", 33.5427, -117.7854, "9410580");
        add("LA7", "Zuma Beach", 34.0158, -118.8210, "TWC0445");
        add("LA8", "Manhattan Beach", 33.8847, -118.4109, "9410777");
        add("LA9", "Redondo Beach", 33.8130, -118.3930, "9410738");
        add("LA10", "Seal Beach", 33.7414, -118.1050, "9410678");

        // Central Coast
        add("CC1", "East Beach, Santa Barbara", 34.4150, -119.6650, "9411340");
        add("CC2", "Pismo Beach", 35.1400, -120.6450, "9412110");
        add("CC3", "Carmel Beach", 36.5550, -121.9290, "9413375");
        add("CC4", "Monterey State Beach", 36.6050, -121.8880, "9413450");
        add("CC5", "Santa Cruz Main Beach", 36.9620, -122.0180, "9413745");

        // San Francisco Bay Area
        add("SF1", "Ocean Beach, San Francisco", 37.7594, -122.5107, "9414275");
        add("SF2", "Stinson Beach", 37.8981, -122.6431, "9414275");
        add("SF3", "Half Moon Bay State Beach", 37.4640, -122.4430, "9414131");

        // Oregon
        add("OR2", "Seaside Beach", 45.9932, -123.9226, "9440574");
        add("OR3", "Cape Kiwanda", 45.2140, -123.9700, "TWC0857");
        add("OR4", "Bandon Beach", 43.1190, -124.4270, "9431647");

        // Washington
        add("WA1", "Alki Beach", 47.5810, -122.4090, "9447130");
        add("WA2", "Ocean Shores", 46.9755, -124.1734, "TWC0965");
        add("WA3", "Ruby Beach", 47.7100, -124.4160, "TWC0965");
        add("WA4", "Long Beach, Washington", 46.3517, -124.0624, "9440574");

        // Hawaii
        add("HI1", "Waikiki Beach", 21.2760, -157.8270, "1612340");
        add("HI2", "Hanauma Bay", 21.2690, -157.6940, "1612301");
        add("HI3", "Waimea Bay", 21.6420, -158.0640, "1612668");
        add("HI4", "Poipu Beach", 21.8740, -159.4550, "1611400");
        add("HI5", "Kaanapali Beach", 20.9250, -156.6950, "TPT2799");
        add("HI6", "Hapuna Beach", 19.9930, -155.8250, "1617433");

        // Texas
        add("TX3", "Port Aransas Beach", 27.8277, -97.0528, "8775270");
        add("TX4", "Corpus Christi Beach", 27.8180, -97.3890, "8775296");

        // Louisiana, Mississippi, Alabama
        add("LOU1", "Grand Isle State Park", 29.2603, -89.9513, "8761724");
        add("MS1", "Biloxi Beach", 30.3900, -88.8900, "8744117");
        add("AL1", "Gulf Shores Public Beach", 30.2460, -87.7010, "8731269");
        add("AL2", "Orange Beach", 30.2690, -87.5730, "8731269");

        // Florida — Atlantic side
        add("FL3", "Fort Lauderdale Beach", 26.1300, -80.1030, "8722899");
        add("FL8", "Daytona Beach", 29.2260, -81.0100, "8721120");
        add("FL9", "Cocoa Beach", 28.3200, -80.6070, "8721649");
        add("FL10", "Smathers Beach, Key West", 24.5510, -81.7660, "8724557");
        add("FL11", "St. Augustine Beach", 29.8500, -81.2650, "8720587");
        add("FL14", "Jacksonville Beach", 30.2889, -81.3888, "8720291");

        // Florida — Gulf side and Panhandle
        add("FL5", "Siesta Key Beach", 27.2660, -82.5500, "8726034");
        add("FL6", "Panama City Beach", 30.1770, -85.8060, "8729210");
        add("FL7", "Pensacola Beach", 30.3305, -87.1402, "8729807");
        add("FL12", "Naples Beach", 26.1310, -81.8070, "8725110");
        add("FL13", "Fort Myers Beach", 26.4520, -81.9500, "8725383");
        add("FL15", "Sanibel Island", 26.4315, -82.1149, "8725383");

        // Georgia and the Carolinas
        add("GA1", "Tybee Island Beach", 32.0000, -80.8450, "8670892");
        add("GA2", "St. Simons Island", 31.1500, -81.3700, "TEC3499");
        add("SC1", "Myrtle Beach", 33.6890, -78.8860, "8661070");
        add("SC2", "Folly Beach", 32.6550, -79.9400, "TEC3127");
        add("SC3", "Coligny Beach, Hilton Head", 32.1400, -80.7500, "8669167");
        add("NC1", "Wrightsville Beach", 34.2080, -77.7960, "8658163");
        add("NC2", "Nags Head", 35.9570, -75.6240, "8652226");
        add("NC3", "Cape Hatteras", 35.2250, -75.5280, "TEC2789");
        add("NC4", "Atlantic Beach", 34.6990, -76.7400, "8656590");

        // Virginia, Maryland, Delaware
        add("VA1", "Virginia Beach", 36.8530, -75.9780, "8639168");
        add("VA2", "Sandbridge Beach", 36.7284, -75.9369, "8639428");
        add("MD1", "Ocean City, Maryland", 38.3360, -75.0840, "8570280");
        add("MD2", "Assateague Island", 38.2372, -75.1366, "8570280");
        add("DE1", "Rehoboth Beach", 38.7160, -75.0760, "8557863");
        add("DE2", "Bethany Beach", 38.5390, -75.0550, "8557863");

        // New Jersey
        add("NJ1", "Asbury Park Beach", 40.2200, -74.0000, "8532337");
        add("NJ2", "Cape May Beach", 38.9330, -74.9200, "8535962");
        add("NJ3", "Atlantic City Beach", 39.3540, -74.4240, "8534720");
        add("NJ4", "Point Pleasant Beach", 40.0900, -74.0380, "8532337");
        add("NJ5", "Ocean City, New Jersey", 39.2750, -74.5750, "8534770");

        // New York
        add("NY3", "Coney Island Beach", 40.5720, -73.9790, "8517741");
        add("NY4", "Long Beach, New York", 40.5830, -73.6580, "8516385");
        add("NY5", "Robert Moses State Park", 40.6250, -73.2660, "8515228");
        add("NY6", "Ditch Plains, Montauk", 41.0350, -71.9250, "8459681");

        // New England
        add("CT1", "Hammonasset Beach State Park", 41.2600, -72.5400, "8463701");
        add("RI1", "Narragansett Town Beach", 41.4380, -71.4470, "8454658");
        add("RI2", "Misquamicut State Beach", 41.3232, -71.8025, "8458694");
        add("MA1", "Revere Beach", 42.4130, -70.9900, "8443970");
        add("MA2", "Nauset Beach", 41.7900, -69.9350, "8447435");
        add("MA3", "Coast Guard Beach", 41.8390, -69.9460, "8447435");
        add("MA4", "Good Harbor Beach", 42.6180, -70.6390, "8441841");
        add("NH1", "Hampton Beach", 42.9080, -70.8100, "8429489");
        add("ME1", "Old Orchard Beach", 43.5150, -70.3770, "8418557");
        add("ME2", "Ogunquit Beach", 43.2500, -70.5900, "8419399");
        add("ME3", "Popham Beach", 43.7360, -69.7960, "8417177");

        // The Great Lakes. The wave model reaches these — off Chicago it agreed
        // with the Wilmette buoy to within a few inches — except the far western
        // tip of Lake Superior, so Park Point's surf tile stays blank. A lake
        // has no tide worth the name, so none of them names a station.
        add("MI1", "Sleeping Bear Dunes Beach", 44.8110, -86.0680, null);
        add("MI2", "Grand Haven State Park", 43.0530, -86.2480, null);
        add("IL1", "North Avenue Beach", 41.9180, -87.6320, null);
        add("IN1", "West Beach, Indiana Dunes", 41.6245, -87.2191, null);
        add("WI1", "Bradford Beach", 43.0570, -87.8770, null);
        add("OH1", "Headlands Beach State Park", 41.7565, -81.2909, null);
        add("MN1", "Park Point Beach", 46.7300, -92.0600, null);
        add("NY7", "Hamlin Beach State Park", 43.3620, -77.9490, null);

        // Puerto Rico
        add("PR1", "Flamenco Beach", 18.3270, -65.3160, "9752235");
        add("PR2", "Condado Beach", 18.4600, -66.0730, "9755371");
        add("PR3", "Isla Verde Beach", 18.4460, -66.0120, "9754986");
    }

    /**
     * Adds a beach with nothing hand-recorded against it: no curated photo, no
     * standing hazard, no help point. Conditions arrive from the live fetch and
     * the drawn placeholder stands in for the picture.
     *
     * @param tideStation the NOAA station its tides are read from, or null on a
     *                    lake, where there is no tide to read
     */
    private void add(String id, String name, double latitude, double longitude, String tideStation) {
        beaches.add(new Beach(id, name, latitude, longitude, null, null, null, null, tideStation));
    }

    /**
     * Brings every beach's conditions up to date, then reports back as each
     * batch lands so the list fills in progressively.
     *
     * <p>Cheap to call from anywhere: if the data is still fresh the listener
     * is told immediately and nothing touches the network, and if a refresh is
     * already running the listener simply joins it rather than starting a
     * second copy.
     */
    public void refreshAllData(OnDataRefreshListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }

        boolean fresh = System.currentTimeMillis() - refreshedAt < FRESH_FOR_MS;
        if (fresh || !refreshing.compareAndSet(false, true)) {
            // Already current, or someone else's fan-out is in flight. Hand
            // back what is cached now; an in-flight refresh will call again as
            // its results arrive.
            if (listener != null) {
                listener.onDataRefreshed();
                if (fresh) listeners.remove(listener);
            }
            return;
        }

        List<Beach> snapshot = getBeaches();
        if (snapshot.isEmpty()) {
            settle(false);
            return;
        }

        // The list is fetched in batches rather than one request per beach.
        // Open-Meteo takes many coordinates in a single call, so this is a
        // handful of requests for the whole coastline instead of one per beach
        // — which at this size would be hundreds of round trips on every
        // refresh, most of them for beaches nobody is looking at. Photos are
        // curated rather than fetched, so weather is all that goes out.
        int batchSize = WeatherClient.BATCH_SIZE;
        outstanding.set((snapshot.size() + batchSize - 1) / batchSize);

        for (int start = 0; start < snapshot.size(); start += batchSize) {
            final int offset = start;
            int size = Math.min(batchSize, snapshot.size() - start);
            double[] latitudes = new double[size];
            double[] longitudes = new double[size];
            for (int i = 0; i < size; i++) {
                latitudes[i] = snapshot.get(start + i).getLatitude();
                longitudes[i] = snapshot.get(start + i).getLongitude();
            }

            weatherClient.getBeachConditions(latitudes, longitudes,
                new WeatherClient.BatchCallback() {
                    /** Whether this batch changed anything worth redrawing for. */
                    private boolean changed;

                    @Override
                    public void onPoint(int index, BeachConditions conditions) {
                        if (conditions == null) {
                            // Leave the beach without conditions: SafetyLevel
                            // reads that as "no verdict yet" rather than
                            // inventing one.
                            return;
                        }
                        synchronized (beaches) {
                            int at = offset + index;
                            beaches.set(at, beaches.get(at).withConditions(conditions));
                        }
                        changed = true;
                    }

                    @Override
                    public void onComplete() {
                        settle(changed);
                    }
                });
        }
    }

    /**
     * Records one finished request: tells the waiting screens when it changed
     * something, and closes the refresh out once the last one is in.
     */
    private void settle(boolean changed) {
        if (changed) {
            for (OnDataRefreshListener waiting : listeners) {
                waiting.onDataRefreshed();
            }
        }
        if (outstanding.decrementAndGet() <= 0) {
            refreshedAt = System.currentTimeMillis();
            refreshing.set(false);
            // Screens that have since been left must not be retained until the
            // next refresh happens to come along.
            listeners.clear();
        }
    }

    /** Drops the freshness window so the next call refetches. */
    public void invalidate() {
        refreshedAt = 0L;
    }

    public List<Beach> getBeaches() {
        synchronized (beaches) {
            return new ArrayList<>(beaches);
        }
    }

    /**
     * Stores conditions fetched for a single beach, so a screen that refreshes
     * one beach on its own — the detail screen does — hands the reading back
     * to the rest of the app instead of keeping it to itself.
     */
    public void updateConditions(String beachId, BeachConditions conditions) {
        synchronized (beaches) {
            for (int i = 0; i < beaches.size(); i++) {
                if (Objects.equals(beaches.get(i).getId(), beachId)) {
                    beaches.set(i, beaches.get(i).withConditions(conditions));
                    return;
                }
            }
        }
    }


    public List<Beach> searchBeaches(String query) {
        synchronized (beaches) {
            if (query == null || query.isEmpty()) return new ArrayList<>(beaches);
            return beaches.stream()
                .filter(b -> b.getName().toLowerCase().contains(query.toLowerCase()))
                .collect(Collectors.toList());
        }
    }

    /**
     * Beaches within radiusMiles of (lat, lon), closest first. If none fall within
     * the radius (e.g. the user is nowhere near a covered coastline), falls back to
     * the closest fallbackCount beaches so a location-based report is never empty.
     */
    public List<Beach> findNearbyBeaches(double lat, double lon, double radiusMiles, int fallbackCount) {
        Comparator<Beach> byDistance = Comparator.comparingDouble(
            b -> UnitConverter.calculateDistance(lat, lon, b.getLatitude(), b.getLongitude()));

        List<Beach> withinRadius = getBeaches().stream()
            .filter(b -> UnitConverter.calculateDistance(lat, lon, b.getLatitude(), b.getLongitude()) <= radiusMiles)
            .sorted(byDistance)
            .collect(Collectors.toList());

        if (!withinRadius.isEmpty()) return withinRadius;

        return getBeaches().stream()
            .sorted(byDistance)
            .limit(fallbackCount)
            .collect(Collectors.toList());
    }
}
