package com.example.myapplication.data;

import androidx.annotation.VisibleForTesting;

import com.example.myapplication.model.*;
import com.example.myapplication.network.WeatherClient;
import com.example.myapplication.util.UnitConverter;
import java.util.ArrayList;
import java.util.Arrays;
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
     */
    private static final long FRESH_FOR_MS = 5 * 60 * 1000L;

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
        beaches.add(new Beach("CA1", "La Jolla Shores", 32.8589, -117.2558,
            Collections.singletonList(new Hazard(HazardType.RIP_CURRENT, Severity.MODERATE, "Strong rips near the pier.", Collections.singletonList("Swim near lifeguard"))),
            null, 
            Collections.singletonList(new HelpLocation("La Jolla Lifeguard Station", HelpType.LIFEGUARD_TOWER, 32.8589, -117.2558)),
            "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c3/La_Jolla_Shores_photo_Don_Ramey_Logan.jpg/1280px-La_Jolla_Shores_photo_Don_Ramey_Logan.jpg"));
        
        beaches.add(new Beach("CA2", "Coronado Beach", 32.6859, -117.1831, null, null, null, 
            null));
        beaches.add(new Beach("CA3", "Ocean Beach", 32.7495, -117.2553, null, null, null,
            "https://upload.wikimedia.org/wikipedia/commons/9/9f/Sunset_pier.jpg"));
        beaches.add(new Beach("CA4", "Carlsbad State Beach", 33.1506, -117.3551, null, null, null,
            "https://upload.wikimedia.org/wikipedia/commons/c/ca/Carlsbad_State_Beach.jpg"));
        beaches.add(new Beach("CA5", "Moonlight State Beach", 33.0484, -117.2981, null, null, null,
            "https://upload.wikimedia.org/wikipedia/commons/thumb/2/28/Moonlight_Beach%2C_view_from_the_south.JPG/1280px-Moonlight_Beach%2C_view_from_the_south.JPG"));

        beaches.add(new Beach("LA1", "Santa Monica Pier", 34.0099, -118.4960, null, null, null,
            "https://upload.wikimedia.org/wikipedia/commons/thumb/3/36/Santa_monica_pier_entrance_evening.jpg/1280px-Santa_monica_pier_entrance_evening.jpg"));
        beaches.add(new Beach("LA2", "Huntington Beach", 33.6599, -117.9992, null, null, null,
            null));
        beaches.add(new Beach("LA3", "Malibu Lagoon", 34.0322, -118.6775, null, null, null,
            null));
        beaches.add(new Beach("LA4", "Venice Beach", 33.9850, -118.4695, null, null, null,
            null));
        
        beaches.add(new Beach("FL1", "South Beach", 25.7826, -80.1285, 
            Collections.singletonList(new Hazard(HazardType.UV_INDEX, Severity.EXTREME, "High UV today.", Arrays.asList("Apply SPF 50", "Reapply every 2h"))),
            null,
            Collections.singletonList(new HelpLocation("Miami Beach Rescue", HelpType.HOSPITAL, 25.7826, -80.1285)),
            null));
        
        beaches.add(new Beach("FL2", "Haulover Beach", 25.9035, -80.1213, null, null, null,
            "https://upload.wikimedia.org/wikipedia/commons/thumb/b/bc/Haulover-beach-2.JPG/1280px-Haulover-beach-2.JPG"));
        beaches.add(new Beach("FL4", "Clearwater Beach", 27.9765, -82.8318, null, null, null,
            "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cf/Clearwater_Beach_%2854357452307%29.jpg/1280px-Clearwater_Beach_%2854357452307%29.jpg"));
        beaches.add(new Beach("NY1", "Rockaway Beach", 40.5843, -73.8197, null, null, null,
            "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b9/Rockaway_Beach_Queens.JPG/1280px-Rockaway_Beach_Queens.JPG"));
        beaches.add(new Beach("NY2", "Jones Beach State Park", 40.5909, -73.5121, null, null, null, 
            null));
        beaches.add(new Beach("TX1", "Galveston Island", 29.2991, -94.7946, null, null, null, 
            null));
        beaches.add(new Beach("TX2", "South Padre Island", 26.1118, -97.1681, null, null, null,
            null));
        beaches.add(new Beach("OR1", "Cannon Beach", 45.8918, -123.9615, null, null, null,
            "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e3/Haystack_Rock%2C_Oregon_%28drone_photograph%29.jpg/1280px-Haystack_Rock%2C_Oregon_%28drone_photograph%29.jpg"));

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
        add("CA6", "Pacific Beach", 32.7970, -117.2560);
        add("CA7", "Mission Beach", 32.7708, -117.2520);
        add("CA8", "Del Mar City Beach", 32.9595, -117.2673);
        add("CA9", "Torrey Pines State Beach", 32.9337, -117.2590);
        add("CA10", "Imperial Beach", 32.5793, -117.1350);
        add("CA11", "Oceanside Beach", 33.1928, -117.3861);

        // Los Angeles and Orange County
        add("LA5", "Newport Beach", 33.6189, -117.9298);
        add("LA6", "Laguna Main Beach", 33.5427, -117.7854);
        add("LA7", "Zuma Beach", 34.0158, -118.8210);
        add("LA8", "Manhattan Beach", 33.8847, -118.4109);
        add("LA9", "Redondo Beach", 33.8130, -118.3930);
        add("LA10", "Seal Beach", 33.7414, -118.1050);

        // Central Coast
        add("CC1", "East Beach, Santa Barbara", 34.4150, -119.6650);
        add("CC2", "Pismo Beach", 35.1400, -120.6450);
        add("CC3", "Carmel Beach", 36.5550, -121.9290);
        add("CC4", "Monterey State Beach", 36.6050, -121.8880);
        add("CC5", "Santa Cruz Main Beach", 36.9620, -122.0180);

        // San Francisco Bay Area
        add("SF1", "Ocean Beach, San Francisco", 37.7594, -122.5107);
        add("SF2", "Stinson Beach", 37.9005, -122.6420);
        add("SF3", "Half Moon Bay State Beach", 37.4640, -122.4430);

        // Oregon
        add("OR2", "Seaside Beach", 45.9932, -123.9226);
        add("OR3", "Cape Kiwanda", 45.2140, -123.9700);
        add("OR4", "Bandon Beach", 43.1190, -124.4270);

        // Washington
        add("WA1", "Alki Beach", 47.5810, -122.4090);
        add("WA2", "Ocean Shores", 46.9740, -124.1600);
        add("WA3", "Ruby Beach", 47.7100, -124.4160);
        add("WA4", "Long Beach, Washington", 46.3520, -124.0540);

        // Hawaii
        add("HI1", "Waikiki Beach", 21.2760, -157.8270);
        add("HI2", "Hanauma Bay", 21.2690, -157.6940);
        add("HI3", "Waimea Bay", 21.6420, -158.0640);
        add("HI4", "Poipu Beach", 21.8740, -159.4550);
        add("HI5", "Kaanapali Beach", 20.9250, -156.6950);
        add("HI6", "Hapuna Beach", 19.9930, -155.8250);

        // Texas
        add("TX3", "Port Aransas Beach", 27.8330, -97.0610);
        add("TX4", "Corpus Christi Beach", 27.8180, -97.3890);

        // Louisiana, Mississippi, Alabama
        add("LOU1", "Grand Isle State Park", 29.2340, -89.9580);
        add("MS1", "Biloxi Beach", 30.3900, -88.8900);
        add("AL1", "Gulf Shores Public Beach", 30.2460, -87.7010);
        add("AL2", "Orange Beach", 30.2690, -87.5730);

        // Florida — Atlantic side
        add("FL3", "Fort Lauderdale Beach", 26.1300, -80.1030);
        add("FL8", "Daytona Beach", 29.2260, -81.0100);
        add("FL9", "Cocoa Beach", 28.3200, -80.6070);
        add("FL10", "Smathers Beach, Key West", 24.5510, -81.7660);
        add("FL11", "St. Augustine Beach", 29.8500, -81.2650);
        add("FL14", "Jacksonville Beach", 30.2900, -81.3930);

        // Florida — Gulf side and Panhandle
        add("FL5", "Siesta Key Beach", 27.2660, -82.5500);
        add("FL6", "Panama City Beach", 30.1770, -85.8060);
        add("FL7", "Pensacola Beach", 30.3270, -87.1390);
        add("FL12", "Naples Beach", 26.1310, -81.8070);
        add("FL13", "Fort Myers Beach", 26.4520, -81.9500);
        add("FL15", "Sanibel Island", 26.4400, -82.1100);

        // Georgia and the Carolinas
        add("GA1", "Tybee Island Beach", 32.0000, -80.8450);
        add("GA2", "St. Simons Island", 31.1500, -81.3700);
        add("SC1", "Myrtle Beach", 33.6890, -78.8860);
        add("SC2", "Folly Beach", 32.6550, -79.9400);
        add("SC3", "Coligny Beach, Hilton Head", 32.1400, -80.7500);
        add("NC1", "Wrightsville Beach", 34.2080, -77.7960);
        add("NC2", "Nags Head", 35.9570, -75.6240);
        add("NC3", "Cape Hatteras", 35.2250, -75.5280);
        add("NC4", "Atlantic Beach", 34.6990, -76.7400);

        // Virginia, Maryland, Delaware
        add("VA1", "Virginia Beach", 36.8530, -75.9780);
        add("VA2", "Sandbridge Beach", 36.7300, -75.9300);
        add("MD1", "Ocean City, Maryland", 38.3360, -75.0840);
        add("MD2", "Assateague Island", 38.2500, -75.1500);
        add("DE1", "Rehoboth Beach", 38.7160, -75.0760);
        add("DE2", "Bethany Beach", 38.5390, -75.0550);

        // New Jersey
        add("NJ1", "Asbury Park Beach", 40.2200, -74.0000);
        add("NJ2", "Cape May Beach", 38.9330, -74.9200);
        add("NJ3", "Atlantic City Beach", 39.3540, -74.4240);
        add("NJ4", "Point Pleasant Beach", 40.0900, -74.0380);
        add("NJ5", "Ocean City, New Jersey", 39.2750, -74.5750);

        // New York
        add("NY3", "Coney Island Beach", 40.5720, -73.9790);
        add("NY4", "Long Beach, New York", 40.5830, -73.6580);
        add("NY5", "Robert Moses State Park", 40.6250, -73.2660);
        add("NY6", "Ditch Plains, Montauk", 41.0350, -71.9250);

        // New England
        add("CT1", "Hammonasset Beach State Park", 41.2600, -72.5400);
        add("RI1", "Narragansett Town Beach", 41.4380, -71.4470);
        add("RI2", "Misquamicut State Beach", 41.3260, -71.8280);
        add("MA1", "Revere Beach", 42.4130, -70.9900);
        add("MA2", "Nauset Beach", 41.7900, -69.9350);
        add("MA3", "Coast Guard Beach", 41.8390, -69.9460);
        add("MA4", "Good Harbor Beach", 42.6180, -70.6390);
        add("NH1", "Hampton Beach", 42.9080, -70.8100);
        add("ME1", "Old Orchard Beach", 43.5150, -70.3770);
        add("ME2", "Ogunquit Beach", 43.2500, -70.5900);
        add("ME3", "Popham Beach", 43.7360, -69.7960);

        // The Great Lakes. There is no wave model out here, so the surf tile
        // stays blank on these — which is correct: they have swimmers, rip
        // currents and drownings, but no marine forecast to read.
        add("MI1", "Sleeping Bear Dunes Beach", 44.8110, -86.0680);
        add("MI2", "Grand Haven State Park", 43.0530, -86.2480);
        add("IL1", "North Avenue Beach", 41.9180, -87.6320);
        add("IN1", "West Beach, Indiana Dunes", 41.6170, -87.2170);
        add("WI1", "Bradford Beach", 43.0570, -87.8770);
        add("OH1", "Headlands Beach State Park", 41.7530, -81.2860);
        add("MN1", "Park Point Beach", 46.7300, -92.0600);
        add("NY7", "Hamlin Beach State Park", 43.3620, -77.9490);

        // Puerto Rico
        add("PR1", "Flamenco Beach", 18.3270, -65.3160);
        add("PR2", "Condado Beach", 18.4600, -66.0730);
        add("PR3", "Isla Verde Beach", 18.4460, -66.0120);
    }

    /**
     * Adds a beach with nothing hand-recorded against it: no curated photo, no
     * standing hazard, no help point. Conditions arrive from the live fetch and
     * the drawn placeholder stands in for the picture.
     */
    private void add(String id, String name, double latitude, double longitude) {
        beaches.add(new Beach(id, name, latitude, longitude, null, null, null, null));
    }

    /**
     * Brings every beach's conditions and photo up to date, then reports back
     * as each one lands so the list fills in progressively.
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
