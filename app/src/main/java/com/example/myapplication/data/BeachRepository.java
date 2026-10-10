package com.example.myapplication.data;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.example.myapplication.R;
import com.example.myapplication.model.*;
import com.example.myapplication.network.WeatherClient;
import com.example.myapplication.util.UnitConverter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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

    /**
     * How soon a beach that came back with nothing is asked about again, while
     * a screen is open on it. Long enough that a whole batch failing does not
     * turn into a tight loop, short enough that a reading dropped on the way in
     * lands before the user has finished reading the list.
     */
    private static final long RETRY_AFTER_MS = 20 * 1000L;

    /**
     * The ceiling that wait doubles towards while readings keep failing. At the
     * freshness window, a phone that has been out of signal for a while is
     * asking about as often as a phone whose readings simply went stale.
     */
    private static final long MAX_RETRY_WAIT_MS = FRESH_FOR_MS;

    /**
     * Runs the retry after its wait. One daemon thread for the process: it is
     * idle but for the few seconds a retry takes to hand work to the network
     * pool, and a daemon can never hold the process open.
     */
    private static final ScheduledExecutorService RETRY_CLOCK =
        Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "beach-conditions-retry");
            thread.setDaemon(true);
            return thread;
        });

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
    /** The screens watching for readings, until each drops its own. */
    private final List<OnDataRefreshListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicInteger outstanding = new AtomicInteger();
    /** Whether a retry is already on the clock, so a pass queues one, not one per batch. */
    private final AtomicBoolean retryScheduled = new AtomicBoolean(false);
    /** How long the next retry waits; doubles on failure, reset on success. */
    private volatile long retryWaitMs = RETRY_AFTER_MS;

    public interface OnDataRefreshListener {
        void onDataRefreshed();
    }

    private BeachRepository() {
        beaches = new ArrayList<>();

        // Conditions start null so the live fetch is what fills them.
        //
        // A beach only gets a photo once it has been checked to be of that beach:
        // geotagged on the sand on Wikimedia Commons, or showing a landmark
        // nowhere else has. The stock photos once dealt round this list turned
        // out to be the Maldives, Yosemite and a mosque in Malaysia, and a
        // picture of somewhere else under a beach's name misleads the one
        // judgement this app exists for. Every other beach shows the drawn
        // shoreline, and no photo is used for two beaches.
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
            Collections.singletonList(new Hazard(HazardType.RIP_CURRENT, Severity.MODERATE,
                R.string.hazard_recorded_lajolla_rips,
                R.string.hazard_recorded_step_swim_near_lifeguard)),
            null,
            // The City of San Diego's address for its La Jolla Shores
            // lifeguards, 8260 Camino del Oro. It used to sit on the beach's
            // own pin, three hundred metres up the sand.
            Collections.singletonList(new HelpLocation("La Jolla Shores Lifeguard Station", HelpType.LIFEGUARD_TOWER, 32.8562, -117.2569)),
            "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c3/La_Jolla_Shores_photo_Don_Ramey_Logan.jpg/1280px-La_Jolla_Shores_photo_Don_Ramey_Logan.jpg",
            "9410230"));

        // Coronado's picture used to be hotlinked off a travel publisher's
        // image CDN — the one photo in this list not from Wikimedia Commons,
        // served at somebody else's pleasure and under nobody's licence. This
        // one is CC BY-SA and geotagged on Coronado's own sand.
        beaches.add(new Beach("CA2", "Coronado Beach", 32.6859, -117.1831, null, null, null,
            "https://upload.wikimedia.org/wikipedia/commons/thumb/0/07/Coronado_beach_lifeguard_station.jpg/1280px-Coronado_beach_lifeguard_station.jpg",
            "TWC0405"));
        beaches.add(new Beach("CA3", "Ocean Beach", 32.7495, -117.2553, null, null, null,
            "https://upload.wikimedia.org/wikipedia/commons/9/9f/Sunset_pier.jpg", "TWC0405"));
        beaches.add(new Beach("CA4", "Carlsbad State Beach", 33.1527, -117.3503, null, null, null,
            "https://upload.wikimedia.org/wikipedia/commons/c/ca/Carlsbad_State_Beach.jpg", "9410230"));
        beaches.add(new Beach("CA5", "Moonlight State Beach", 33.0484, -117.2981, null, null, null,
            "https://upload.wikimedia.org/wikipedia/commons/thumb/2/28/Moonlight_Beach%2C_view_from_the_south.JPG/1280px-Moonlight_Beach%2C_view_from_the_south.JPG",
            "9410230"));

        beaches.add(new Beach("LA1", "Santa Monica Pier", 34.0099, -118.4960, null, null, null,
            "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b8/Beach_Volleyball_and_the_Pier%2C_Santa_Monica%2C_CA_05.21.2007.jpg/1280px-Beach_Volleyball_and_the_Pier%2C_Santa_Monica%2C_CA_05.21.2007.jpg",
            "9410840"));
        beaches.add(new Beach("LA2", "Huntington Beach", 33.6557, -118.0043, null, null, null,
            "https://upload.wikimedia.org/wikipedia/commons/2/2a/Huntington_Beach_%288256933262%29.jpg",
            "9410583"));
        beaches.add(new Beach("LA3", "Malibu Lagoon", 34.0322, -118.6775, null, null, null,
            "https://upload.wikimedia.org/wikipedia/commons/thumb/5/59/Malibu_Lagoon_-_panoramio.jpg/1280px-Malibu_Lagoon_-_panoramio.jpg",
            "9410840"));
        beaches.add(new Beach("LA4", "Venice Beach", 33.9850, -118.4695, null, null, null,
            null, "9410840"));

        beaches.add(new Beach("FL1", "South Beach", 25.7826, -80.1285,
            null,
            null,
            Collections.singletonList(new HelpLocation("Miami Beach Ocean Rescue", HelpType.LIFEGUARD_TOWER, 25.7803, -80.1302)),
            "https://upload.wikimedia.org/wikipedia/commons/thumb/8/88/Lifeguards_stand_South_Beach_2.jpg/1280px-Lifeguards_stand_South_Beach_2.jpg",
            "8723170"));

        beaches.add(new Beach("FL2", "Haulover Beach", 25.9035, -80.1213, null, null, null,
            "https://upload.wikimedia.org/wikipedia/commons/thumb/0/0d/Miami-Dade_County%2C_FL%2C_USA_-_panoramio_%288%29.jpg/1280px-Miami-Dade_County%2C_FL%2C_USA_-_panoramio_%288%29.jpg",
            "8723080"));
        beaches.add(new Beach("FL4", "Clearwater Beach", 27.9765, -82.8318, null, null, null,
            "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cf/Clearwater_Beach_%2854357452307%29.jpg/1280px-Clearwater_Beach_%2854357452307%29.jpg",
            "8726724"));
        beaches.add(new Beach("NY1", "Rockaway Beach", 40.5843, -73.8197, null, null, null,
            "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b9/Rockaway_Beach_Queens.JPG/1280px-Rockaway_Beach_Queens.JPG",
            "8516881"));
        beaches.add(new Beach("NY2", "Jones Beach State Park", 40.5909, -73.5121, null, null, null,
            null, "8516385"));
        beaches.add(new Beach("TX1", "Galveston Island", 29.2876, -94.7906, null, null, null,
            "https://upload.wikimedia.org/wikipedia/commons/thumb/3/31/Galveston_Island_Historic_Pleasure_Pier_-_Texas_Gulf_Coast_%2854182554969%29.jpg/1280px-Galveston_Island_Historic_Pleasure_Pier_-_Texas_Gulf_Coast_%2854182554969%29.jpg",
            "8771510"));
        beaches.add(new Beach("TX2", "South Padre Island", 26.1118, -97.1681, null, null, null,
            "https://upload.wikimedia.org/wikipedia/commons/thumb/d/da/South_Padre_Island_beach_view_from_condo.jpg/1280px-South_Padre_Island_beach_view_from_condo.jpg",
            "8779750"));
        beaches.add(new Beach("OR1", "Cannon Beach", 45.8918, -123.9615, null, null, null,
            "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e3/Haystack_Rock%2C_Oregon_%28drone_photograph%29.jpg/1280px-Haystack_Rock%2C_Oregon_%28drone_photograph%29.jpg",
            "9437585"));

        // San Diego County
        add("CA6", "Pacific Beach", 32.7970, -117.2560, "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3d/San_Diego_coastline%2C_California%2C_USA26.jpg/1280px-San_Diego_coastline%2C_California%2C_USA26.jpg", "9410230");
        add("CA7", "Mission Beach", 32.7708, -117.2520, "https://upload.wikimedia.org/wikipedia/commons/thumb/7/7c/Mission_Beach%2C_San_Diego%2C_CA_92109%2C_USA_-_panoramio_%284%29.jpg/1280px-Mission_Beach%2C_San_Diego%2C_CA_92109%2C_USA_-_panoramio_%284%29.jpg", "9410230");
        add("CA8", "Del Mar City Beach", 32.9595, -117.2673, "https://upload.wikimedia.org/wikipedia/commons/thumb/7/72/Del_Mar%2C_CA_-_panoramio_%281%29.jpg/1280px-Del_Mar%2C_CA_-_panoramio_%281%29.jpg", "9410230");
        add("CA9", "Torrey Pines State Beach", 32.9337, -117.2590, "https://upload.wikimedia.org/wikipedia/commons/thumb/f/fc/Torrey_Pines_Sunset_%2816018375041%29.jpg/1280px-Torrey_Pines_Sunset_%2816018375041%29.jpg", "9410230");
        add("CA10", "Imperial Beach", 32.5793, -117.1350, "https://upload.wikimedia.org/wikipedia/commons/thumb/0/02/Imperial_Beach%2C_CA%2C_USA_-_panoramio_%2829%29.jpg/1280px-Imperial_Beach%2C_CA%2C_USA_-_panoramio_%2829%29.jpg", "9410120");
        add("CA11", "Oceanside Beach", 33.1928, -117.3861, "https://upload.wikimedia.org/wikipedia/commons/thumb/3/36/Oceanside%2C_California_Beach.jpg/1280px-Oceanside%2C_California_Beach.jpg", "TWC0419");
        add("CA12", "Silver Strand State Beach", 32.6322, -117.1393, "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f3/Spotlight_-_panoramio.jpg/1280px-Spotlight_-_panoramio.jpg", "9410120");
        add("CA13", "Cardiff State Beach", 33.0128, -117.2810, "https://upload.wikimedia.org/wikipedia/commons/thumb/0/0b/Cardiff_Beach_looking_south.jpg/1280px-Cardiff_Beach_looking_south.jpg", "9410230");
        add("CA14", "Swami's Beach", 33.0345, -117.2945, "https://upload.wikimedia.org/wikipedia/commons/thumb/1/12/Swami%27s_Beach%2C_Encinitas_%288133630046%29.jpg/1280px-Swami%27s_Beach%2C_Encinitas_%288133630046%29.jpg", "9410230");
        add("CA15", "Fletcher Cove, Solana Beach", 32.9925, -117.2740, "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3e/San_Diego_County_%2829165960554%29.jpg/1280px-San_Diego_County_%2829165960554%29.jpg", "9410230");
        add("CA16", "South Carlsbad State Beach", 33.1081, -117.3234, "https://upload.wikimedia.org/wikipedia/commons/thumb/5/56/Powerplant_Paradise_%28Unsplash%29.jpg/1280px-Powerplant_Paradise_%28Unsplash%29.jpg", "9410230");
        add("CA17", "Windansea Beach", 32.8318, -117.2807, "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4f/Windansea_Beach%2C_San_Diego%2C_United_States_%28Unsplash_HkZKfLKzlq4%29.jpg/1280px-Windansea_Beach%2C_San_Diego%2C_United_States_%28Unsplash_HkZKfLKzlq4%29.jpg", "9410230");
        add("CA18", "La Jolla Cove", 32.8503, -117.2717, "https://upload.wikimedia.org/wikipedia/commons/thumb/2/21/La_Jolla_-_panoramio_-_tarosys.jpg/1280px-La_Jolla_-_panoramio_-_tarosys.jpg", "9410230");
        add("CA19", "Black's Beach", 32.8890, -117.2530, "https://upload.wikimedia.org/wikipedia/commons/thumb/9/93/Black%27s_Beach_4_2017-10-19.jpg/1280px-Black%27s_Beach_4_2017-10-19.jpg", "9410230");
        add("CA20", "Tourmaline Surfing Park", 32.8055, -117.2617, "9410230");
        // San Onofre is nearer the San Clemente station than anything in San
        // Diego Bay: La Jolla is fifty miles down the coast.
        add("CA21", "San Onofre State Beach", 33.3770, -117.5680, "https://upload.wikimedia.org/wikipedia/commons/thumb/0/0f/Nuke_Plant%2C_Great_White_Shark_Breeding_Ground%2C_%5E_Surfspot%2C_Dogpatch_San_Onofre_-_panoramio.jpg/1280px-Nuke_Plant%2C_Great_White_Shark_Breeding_Ground%2C_%5E_Surfspot%2C_Dogpatch_San_Onofre_-_panoramio.jpg", "TWC0419");
        // The two halves of Ponto, at the south end of Carlsbad, either
        // side of the Batiquitos Lagoon outlet. OpenStreetMap maps South
        // Ponto as a beach of its own and carries North Ponto on the same
        // sand as the South Carlsbad campground, whose own strand runs on
        // north of both.
        add("CA22", "North Ponto Beach", 33.0930, -117.3148, "9410230");
        add("CA23", "South Ponto Beach", 33.0844, -117.3123, "https://upload.wikimedia.org/wikipedia/commons/thumb/6/6e/SouthCarlsbadStateBeach2019.jpg/1280px-SouthCarlsbadStateBeach2019.jpg", "9410230");

        // Los Angeles and Orange County
        add("LA5", "Newport Beach", 33.6076, -117.9290, "https://upload.wikimedia.org/wikipedia/commons/thumb/6/63/Near_Tower_20_and_Newport_Pier_%2849081452016%29.jpg/1280px-Near_Tower_20_and_Newport_Pier_%2849081452016%29.jpg", "9410583");
        add("LA6", "Laguna Main Beach", 33.5427, -117.7854, "https://upload.wikimedia.org/wikipedia/commons/thumb/9/9c/Cloudy_Laguna_Main_Beach.jpg/1280px-Cloudy_Laguna_Main_Beach.jpg", "9410580");
        add("LA7", "Zuma Beach", 34.0158, -118.8210, "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a2/ZumaBeachCountyPark.jpg/1280px-ZumaBeachCountyPark.jpg", "TWC0445");
        add("LA8", "Manhattan Beach", 33.8847, -118.4109, "https://upload.wikimedia.org/wikipedia/commons/thumb/4/47/Manhattan_Beach%2C_California_35_2018-05-20.jpg/1280px-Manhattan_Beach%2C_California_35_2018-05-20.jpg", "9410777");
        // Redondo's pin used to sit at 33.8130, which OpenStreetMap has as
        // Torrance County Beach — a mile and a half south of Redondo's own
        // sand, and now its own entry below.
        add("LA9", "Redondo Beach", 33.8443, -118.3944, "https://upload.wikimedia.org/wikipedia/commons/thumb/9/91/Race_Start_for_Wet_Suit_Swimmers_%2849171557952%29.jpg/1280px-Race_Start_for_Wet_Suit_Swimmers_%2849171557952%29.jpg", "9410738");
        add("LA10", "Seal Beach", 33.7414, -118.1050, "https://upload.wikimedia.org/wikipedia/commons/thumb/2/28/Overcast_Summer_Day_%28218581039%29.jpeg/1280px-Overcast_Summer_Day_%28218581039%29.jpeg", "9410678");

        // Orange County, from the county line up. San Clemente covers
        // the south end, Corona del Mar — the harmonic station at the
        // Newport jetties — the Laguna coves, and Balboa Pier the long
        // sand north of Newport.
        add("LA11", "San Clemente State Beach", 33.4037, -117.6062, "https://upload.wikimedia.org/wikipedia/commons/thumb/5/5d/Calafia_cliffs%2C_San_Clemente%2C_California_-_panoramio.jpg/1280px-Calafia_cliffs%2C_San_Clemente%2C_California_-_panoramio.jpg", "TWC0419");
        add("LA12", "Doheny State Beach", 33.4608, -117.6858, "TWC0419");
        add("LA13", "Salt Creek Beach", 33.4823, -117.7283, "https://upload.wikimedia.org/wikipedia/commons/thumb/2/2b/Saltcreekbeach.JPG/1280px-Saltcreekbeach.JPG", "TWC0419");
        add("LA14", "Aliso Beach", 33.5093, -117.7548, "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a3/Aliso_Beach_-_panoramio.jpg/1280px-Aliso_Beach_-_panoramio.jpg", "9410580");
        add("LA15", "Crystal Cove State Beach", 33.5715, -117.8370, "https://upload.wikimedia.org/wikipedia/commons/thumb/0/00/Crystal_Cove_Beach_Cottages_35_Crystal_Cove_Newport_Beach%2C_CA_92657%2C_USA_-_panoramio_%2816%29.jpg/1280px-Crystal_Cove_Beach_Cottages_35_Crystal_Cove_Newport_Beach%2C_CA_92657%2C_USA_-_panoramio_%2816%29.jpg", "9410580");
        add("LA16", "Corona del Mar State Beach", 33.5930, -117.8730, "https://upload.wikimedia.org/wikipedia/commons/thumb/e/ec/Corona_Del_Mar%2C_Newport_Beach%2C_CA%2C_USA_-_panoramio.jpg/1280px-Corona_Del_Mar%2C_Newport_Beach%2C_CA%2C_USA_-_panoramio.jpg", "9410580");
        add("LA17", "Bolsa Chica State Beach", 33.6880, -118.0400, "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d4/CA_Orange_County_%2826115241105%29.jpg/1280px-CA_Orange_County_%2826115241105%29.jpg", "9410583");
        add("LA18", "Sunset Beach, California", 33.7180, -118.0710, "https://upload.wikimedia.org/wikipedia/commons/thumb/2/2d/Sunset-Beach.jpg/1280px-Sunset-Beach.jpg", "9410583");

        // Los Angeles County. Alamitos Beach and Cabrillo Beach both face into
        // San Pedro Bay, so they read from the harbour stations rather than
        // from Santa Monica Bay on the other side of the peninsula.
        add("LA19", "Hermosa Beach", 33.8620, -118.4022, "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3c/Hermosa_Beach_%288260461388%29.jpg/1280px-Hermosa_Beach_%288260461388%29.jpg", "9410738");
        add("LA20", "Torrance Beach", 33.8099, -118.3927, "https://upload.wikimedia.org/wikipedia/commons/thumb/b/bb/Palos_Verdes_%2831198254890%29.jpg/1280px-Palos_Verdes_%2831198254890%29.jpg", "9410738");
        add("LA21", "Dockweiler State Beach", 33.9393, -118.4425, "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c6/Pacific_Ocean_with_sand_at_Dockweiler_Beach.jpg/1280px-Pacific_Ocean_with_sand_at_Dockweiler_Beach.jpg", "9410777");
        add("LA22", "Cabrillo Beach, San Pedro", 33.7080, -118.2810, "9410650");
        add("LA23", "Alamitos Beach, Long Beach", 33.7615, -118.1650, "https://upload.wikimedia.org/wikipedia/commons/thumb/3/39/Long_Beach_%2831036781442%29.jpg/1280px-Long_Beach_%2831036781442%29.jpg", "9410678");
        add("LA24", "Will Rogers State Beach", 34.0325, -118.5330, "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f6/Will_Rogers_State_Beach_1.JPG/1280px-Will_Rogers_State_Beach_1.JPG", "9410840");
        add("LA25", "Point Dume State Beach", 34.0012, -118.8060, "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d8/Cliffs_At_Point_Dume%2C_Malibu%2C_CA_-_panoramio.jpg/1280px-Cliffs_At_Point_Dume%2C_Malibu%2C_CA_-_panoramio.jpg", "TWC0445");
        add("LA26", "El Matador State Beach", 34.0378, -118.8730, "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e6/EL_MATADOR_BEACH%2C_MALIBU%2C_CA._2015_-_panoramio.jpg/1280px-EL_MATADOR_BEACH%2C_MALIBU%2C_CA._2015_-_panoramio.jpg", "TWC0445");
        add("LA27", "Leo Carrillo State Beach", 34.0450, -118.9350, "https://upload.wikimedia.org/wikipedia/commons/thumb/6/69/Leo_Carrillo_State_Beach_-_panoramio.jpg/1280px-Leo_Carrillo_State_Beach_-_panoramio.jpg", "TWC0445");
        add("LA28", "Avalon Beach, Catalina Island", 33.3455, -118.3260, "9410079");

        // Central Coast
        add("CC1", "East Beach, Santa Barbara", 34.4150, -119.6650, "https://upload.wikimedia.org/wikipedia/commons/thumb/4/49/Morning_Stroll_%283148737532%29.jpg/1280px-Morning_Stroll_%283148737532%29.jpg", "9411340");
        add("CC2", "Pismo Beach", 35.1400, -120.6450, "https://upload.wikimedia.org/wikipedia/commons/thumb/8/89/Pismo_Beach_Pier%2C_North_view_20110805_1.jpg/1280px-Pismo_Beach_Pier%2C_North_view_20110805_1.jpg", "9412110");
        add("CC3", "Carmel Beach", 36.5550, -121.9290, "https://upload.wikimedia.org/wikipedia/commons/thumb/7/72/Carmel-by-the-Sea_1_-_panoramio.jpg/1280px-Carmel-by-the-Sea_1_-_panoramio.jpg", "9413375");
        add("CC4", "Monterey State Beach", 36.6050, -121.8880, "9413450");
        add("CC5", "Santa Cruz Main Beach", 36.9620, -122.0180, "https://upload.wikimedia.org/wikipedia/commons/thumb/2/21/Santa_Cruz_Beach_13_2012-12-18.jpg/1280px-Santa_Cruz_Beach_13_2012-12-18.jpg", "9413745");

        // Ventura and Santa Barbara County. Still inside the Southern
        // California Bight, so the water is the warm, sheltered kind, but the
        // coast turns east-west here and the stations run with it.
        add("CC6", "San Buenaventura State Beach", 34.2658, -119.2807, "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4b/San_Buenaventura_State_Beach_Ventura_Pier_2015-01-04.jpg/1280px-San_Buenaventura_State_Beach_Ventura_Pier_2015-01-04.jpg", "9411189");
        add("CC7", "Hollywood Beach, Oxnard", 34.1615, -119.2290, "9411065");
        add("CC8", "Carpinteria State Beach", 34.3900, -119.5200, "https://upload.wikimedia.org/wikipedia/commons/7/72/Carpinteria_Beach_%2812.2010%29_-_panoramio.jpg", "9411270");
        add("CC9", "Leadbetter Beach", 34.4030, -119.6905, "9411340");
        add("CC10", "Goleta Beach", 34.4160, -119.8290, "https://upload.wikimedia.org/wikipedia/commons/8/87/Goleta_Pier_%282305063157%29.jpg", "9411340");
        add("CC11", "Refugio State Beach", 34.4632, -120.0702, "https://upload.wikimedia.org/wikipedia/commons/thumb/9/92/Refugio_State_Beach_California_%2828788392072%29.jpg/1280px-Refugio_State_Beach_California_%2828788392072%29.jpg", "9411399");
        add("CC12", "El Capitan State Beach", 34.4595, -120.0250, "https://upload.wikimedia.org/wikipedia/commons/thumb/3/35/El-capitan-east-beach.jpg/1280px-El-capitan-east-beach.jpg", "9411399");

        // North of Point Conception. Port San Luis is the only station between
        // Point Arguello and San Simeon, so Morro Rock reads from it too.
        add("CC13", "Avila Beach", 35.1790, -120.7320, "9412110");
        add("CC14", "Morro Rock Beach", 35.3700, -120.8650, "https://upload.wikimedia.org/wikipedia/commons/thumb/9/91/The_Jetty_at_Morro_Bay_-_panoramio.jpg/1280px-The_Jetty_at_Morro_Bay_-_panoramio.jpg", "9412110");
        add("CC15", "Moonstone Beach, Cambria", 35.5748, -121.1140, "https://upload.wikimedia.org/wikipedia/commons/thumb/0/01/Big_Surf%21_%2846214181982%29.jpg/1280px-Big_Surf%21_%2846214181982%29.jpg", "9412553");
        add("CC16", "Pfeiffer Beach, Big Sur", 36.2378, -121.8155, "https://upload.wikimedia.org/wikipedia/commons/thumb/4/46/Pfeiffer_Beach_%288611951581%29.jpg/1280px-Pfeiffer_Beach_%288611951581%29.jpg", "9413375");
        add("CC17", "Asilomar State Beach", 36.6210, -121.9395, "https://upload.wikimedia.org/wikipedia/commons/thumb/d/df/Asilomar_State_Beach_-_2023-02-22_-_1.jpg/1280px-Asilomar_State_Beach_-_2023-02-22_-_1.jpg", "9413450");
        add("CC18", "Marina State Beach", 36.6930, -121.8090, "9413450");
        add("CC19", "Capitola Beach", 36.9710, -121.9530, "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e4/Capitola_Beach_1_2018-07-02.jpg/1280px-Capitola_Beach_1_2018-07-02.jpg", "9413745");
        add("CC20", "Natural Bridges State Beach", 36.9500, -122.0570, "https://upload.wikimedia.org/wikipedia/commons/thumb/9/91/Santa_Cruz_Natural_Bridges_beach.JPG/1280px-Santa_Cruz_Natural_Bridges_beach.JPG", "9413745");

        // San Francisco Bay Area
        add("SF1", "Ocean Beach, San Francisco", 37.7594, -122.5107, "https://upload.wikimedia.org/wikipedia/commons/thumb/e/ed/Sunset_at_Judah%2C_Ocean_Beach_-_panoramio.jpg/1280px-Sunset_at_Judah%2C_Ocean_Beach_-_panoramio.jpg", "9414275");
        add("SF2", "Stinson Beach", 37.8981, -122.6431, "https://upload.wikimedia.org/wikipedia/commons/thumb/b/bc/Stinson_Beach%2C_CA_-_panoramio.jpg/1280px-Stinson_Beach%2C_CA_-_panoramio.jpg", "9414275");
        add("SF3", "Half Moon Bay State Beach", 37.4640, -122.4430, "https://upload.wikimedia.org/wikipedia/commons/thumb/0/01/Half_Moon_Bay_State_Beach_8_2019-06-12.jpg/1280px-Half_Moon_Bay_State_Beach_8_2019-06-12.jpg", "9414131");
        // Baker Beach is inside the Gate, so it reads from the Golden Gate
        // station rather than from Ocean Beach on the ocean side of the head.
        add("SF4", "Baker Beach", 37.7935, -122.4840, "https://upload.wikimedia.org/wikipedia/commons/thumb/4/48/Golden_Gate_Bridge_from_Baker_Beach.JPG/1280px-Golden_Gate_Bridge_from_Baker_Beach.JPG", "9414290");
        add("SF5", "Rodeo Beach", 37.8320, -122.5360, "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f1/Rodeo_Beach%2C_United_States_%28Unsplash%29.jpg/1280px-Rodeo_Beach%2C_United_States_%28Unsplash%29.jpg", "9414906");
        add("SF6", "Muir Beach", 37.8595, -122.5770, "https://upload.wikimedia.org/wikipedia/commons/thumb/5/59/Muir_Beach_in_Marin_County%2C_CA.jpg/1280px-Muir_Beach_in_Marin_County%2C_CA.jpg", "9414906");
        add("SF7", "Pacifica State Beach", 37.5960, -122.5050, "https://upload.wikimedia.org/wikipedia/commons/thumb/0/0c/Pacifica_State_Beach_1_2021-09-09.jpeg/1280px-Pacifica_State_Beach_1_2021-09-09.jpeg", "9414131");
        add("SF8", "Montara State Beach", 37.5520, -122.5140, "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c8/Montara_Surf_Check_%28Unsplash%29.jpg/1280px-Montara_Surf_Check_%28Unsplash%29.jpg", "9414131");
        add("SF9", "Drakes Beach", 38.0270, -122.9600, "https://upload.wikimedia.org/wikipedia/commons/thumb/3/38/Drakes_Bay_Seacliffs.JPG/1280px-Drakes_Bay_Seacliffs.JPG", "9415020");
        add("SF10", "Doran Beach, Bodega Bay", 38.3140, -123.0350, "9415625");

        // The North Coast
        add("NCA1", "Glass Beach, Fort Bragg", 39.4508, -123.8140, "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c5/Cove_At_Glass_Beach_Fort_Bragg_Ca._-_panoramio.jpg/1280px-Cove_At_Glass_Beach_Fort_Bragg_Ca._-_panoramio.jpg", "9417426");
        add("NCA2", "Trinidad State Beach", 41.0590, -124.1480, "https://upload.wikimedia.org/wikipedia/commons/thumb/8/8d/TrinidadOceanPano2.jpg/1280px-TrinidadOceanPano2.jpg", "9419059");
        add("NCA3", "Crescent Beach, Crescent City", 41.7269, -124.1621, "9419750");

        // Oregon
        add("OR2", "Seaside Beach", 45.9932, -123.9226, "https://upload.wikimedia.org/wikipedia/commons/thumb/6/6b/Seaside%2C_Oregon_Coast_-_Pacific_Ocean_Coastal_Beach_%2820152855151%29.jpg/1280px-Seaside%2C_Oregon_Coast_-_Pacific_Ocean_Coastal_Beach_%2820152855151%29.jpg", "9440574");
        add("OR3", "Cape Kiwanda", 45.2140, -123.9700, "https://upload.wikimedia.org/wikipedia/commons/e/e1/Cape_Kiwanda%2C_Oregon_%2852018819076%29.jpg", "TWC0857");
        add("OR4", "Bandon Beach", 43.1190, -124.4270, "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a3/Beach_in_Bandon%2C_OR_%28TK18%29.JPG/1280px-Beach_in_Bandon%2C_OR_%28TK18%29.JPG", "9431647");
        add("OR5", "Nye Beach, Newport", 44.6392, -124.0620, "https://upload.wikimedia.org/wikipedia/commons/thumb/0/0c/Nye_Beach_%28Newport%2C_Oregon%29.jpg/1280px-Nye_Beach_%28Newport%2C_Oregon%29.jpg", "9435385");

        // Washington
        add("WA1", "Alki Beach", 47.5810, -122.4090, "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cb/Alki_Beach%2C_West_Seattle_-_Flickr_-_brewbooks.jpg/1280px-Alki_Beach%2C_West_Seattle_-_Flickr_-_brewbooks.jpg", "9447130");
        add("WA2", "Ocean Shores", 46.9755, -124.1734, "https://upload.wikimedia.org/wikipedia/commons/thumb/7/7a/Grays_Harbor_County%2C_WA%2C_USA_-_panoramio_%2814%29.jpg/1280px-Grays_Harbor_County%2C_WA%2C_USA_-_panoramio_%2814%29.jpg", "TWC0965");
        add("WA3", "Ruby Beach", 47.7100, -124.4160, "https://upload.wikimedia.org/wikipedia/commons/thumb/7/72/Sunset_ruby_beach_rocks_coast_scenic_r_mckenna_march_04_2015_%2816691114474%29.jpg/1280px-Sunset_ruby_beach_rocks_coast_scenic_r_mckenna_march_04_2015_%2816691114474%29.jpg", "TWC0965");
        add("WA4", "Long Beach, Washington", 46.3517, -124.0624, "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b8/Long_Beach%2C_WA_-_panoramio_-_Gabriele_Giuseppini.jpg/1280px-Long_Beach%2C_WA_-_panoramio_-_Gabriele_Giuseppini.jpg", "9440574");
        add("WA5", "Rialto Beach", 47.9175, -124.6395, "https://upload.wikimedia.org/wikipedia/commons/thumb/d/db/Rialto_Beach.jpg/1280px-Rialto_Beach.jpg", "9442396");

        // Hawaii
        add("HI1", "Waikiki Beach", 21.2760, -157.8270, "https://upload.wikimedia.org/wikipedia/commons/thumb/d/db/Waikiki_Beach_2.jpg/1280px-Waikiki_Beach_2.jpg", "1612340");
        add("HI2", "Hanauma Bay", 21.2690, -157.6940, "https://upload.wikimedia.org/wikipedia/commons/thumb/0/00/Hanauma_Bay_James_Brennan_Hawaii_%28137045391%29.jpeg/1280px-Hanauma_Bay_James_Brennan_Hawaii_%28137045391%29.jpeg", "1612301");
        add("HI3", "Waimea Bay", 21.6420, -158.0640, "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c0/Waimea_Bay_Beach_%2812598979504%29.jpg/1280px-Waimea_Bay_Beach_%2812598979504%29.jpg", "1612668");
        add("HI4", "Poipu Beach", 21.8740, -159.4550, "https://upload.wikimedia.org/wikipedia/commons/thumb/1/10/Poipu_Beach%2C_Koloa_%28502724%29_%2816135704164%29.jpg/1280px-Poipu_Beach%2C_Koloa_%28502724%29_%2816135704164%29.jpg", "1611400");
        add("HI5", "Kaanapali Beach", 20.9250, -156.6950, "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cd/Kaanapali_beach_Hawaii_%2845689920562%29.jpg/1280px-Kaanapali_beach_Hawaii_%2845689920562%29.jpg", "TPT2799");
        add("HI6", "Hapuna Beach", 19.9930, -155.8250, "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a3/Hapuna_Strand_Big_island_Hawaii_%2844460194830%29.jpg/1280px-Hapuna_Strand_Big_island_Hawaii_%2844460194830%29.jpg", "1617433");
        add("HI7", "Lanikai Beach", 21.3925, -157.7150, "https://upload.wikimedia.org/wikipedia/commons/thumb/3/31/Lanikai_beach%2C_Oahu_Hawaii.JPG/1280px-Lanikai_beach%2C_Oahu_Hawaii.JPG", "1612376");

        // Texas
        add("TX3", "Port Aransas Beach", 27.8277, -97.0528, "https://upload.wikimedia.org/wikipedia/commons/thumb/d/dd/Port_Aransas_-Mustang_Island_Beach_-_panoramio.jpg/1280px-Port_Aransas_-Mustang_Island_Beach_-_panoramio.jpg", "8775270");
        add("TX4", "Corpus Christi Beach", 27.8180, -97.3890, "8775296");

        // Louisiana, Mississippi, Alabama
        add("LOU1", "Grand Isle State Park", 29.2603, -89.9513, "https://upload.wikimedia.org/wikipedia/commons/thumb/5/59/Grand_Isle_State_Park.jpg/1280px-Grand_Isle_State_Park.jpg", "8761724");
        add("MS1", "Biloxi Beach", 30.3900, -88.8900, "https://upload.wikimedia.org/wikipedia/commons/thumb/9/98/Biloxi_Beach.jpg/1280px-Biloxi_Beach.jpg", "8744117");
        add("AL1", "Gulf Shores Public Beach", 30.2460, -87.7010, "8731269");
        add("AL2", "Orange Beach", 30.2690, -87.5730, "8731269");

        // Florida — Atlantic side
        add("FL3", "Fort Lauderdale Beach", 26.1300, -80.1030, "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b9/All_clear_at_Fort_Lauderdale_Beach_-_panoramio_%281%29.jpg/1280px-All_clear_at_Fort_Lauderdale_Beach_-_panoramio_%281%29.jpg", "8722899");
        add("FL8", "Daytona Beach", 29.2260, -81.0100, "https://upload.wikimedia.org/wikipedia/commons/thumb/9/93/Beach-at-daytona.jpg_-_panoramio.jpg/1280px-Beach-at-daytona.jpg_-_panoramio.jpg", "8721120");
        add("FL9", "Cocoa Beach", 28.3200, -80.6070, "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Cocoa_Beach_Surf_%283879446691%29.jpg/1280px-Cocoa_Beach_Surf_%283879446691%29.jpg", "8721649");
        add("FL10", "Smathers Beach, Key West", 24.5510, -81.7660, "https://upload.wikimedia.org/wikipedia/commons/thumb/b/bc/Early_morning_Smathers_Beach_Key_West.JPG/1280px-Early_morning_Smathers_Beach_Key_West.JPG", "8724557");
        add("FL11", "St. Augustine Beach", 29.8500, -81.2650, "https://upload.wikimedia.org/wikipedia/commons/thumb/8/85/Dune_Rise_-_panoramio.jpg/1280px-Dune_Rise_-_panoramio.jpg", "8720587");
        add("FL14", "Jacksonville Beach", 30.2889, -81.3888, "https://upload.wikimedia.org/wikipedia/commons/thumb/2/29/Fishing_Pier%2C_Jacksonville_Beach_%28Florida%29.jpg/1280px-Fishing_Pier%2C_Jacksonville_Beach_%28Florida%29.jpg", "8720291");
        add("FL16", "Vero Beach", 27.6501, -80.3540, "8722105");

        // Florida — Gulf side and Panhandle
        add("FL5", "Siesta Key Beach", 27.2660, -82.5500, "https://upload.wikimedia.org/wikipedia/commons/thumb/2/26/Siesta_Key_Beach._Sarasota_-_panoramio_-_4net_%286%29.jpg/1280px-Siesta_Key_Beach._Sarasota_-_panoramio_-_4net_%286%29.jpg", "8726034");
        add("FL6", "Panama City Beach", 30.1770, -85.8060, "https://upload.wikimedia.org/wikipedia/commons/thumb/0/02/30795376590_Jpg_%28197628539%29.jpeg/1280px-30795376590_Jpg_%28197628539%29.jpeg", "8729210");
        add("FL7", "Pensacola Beach", 30.3305, -87.1402, "https://upload.wikimedia.org/wikipedia/commons/thumb/c/ca/PensacolaBeachgulfpier.jpg/1280px-PensacolaBeachgulfpier.jpg", "8729807");
        add("FL12", "Naples Beach", 26.1310, -81.8070, "https://upload.wikimedia.org/wikipedia/commons/thumb/6/6b/Naples_-_a_dream_beach%2C_the_Naples_Municipal_Beach_%2815871623455%29.jpg/1280px-Naples_-_a_dream_beach%2C_the_Naples_Municipal_Beach_%2815871623455%29.jpg", "8725110");
        add("FL13", "Fort Myers Beach", 26.4520, -81.9500, "https://upload.wikimedia.org/wikipedia/commons/thumb/5/5b/Fort_Myers_Beach_-_panoramio_%281%29.jpg/1280px-Fort_Myers_Beach_-_panoramio_%281%29.jpg", "8725383");
        add("FL15", "Sanibel Island", 26.4315, -82.1149, "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f7/Sanibel_S_-_panoramio.jpg/1280px-Sanibel_S_-_panoramio.jpg", "8725383");

        // Georgia and the Carolinas
        add("GA1", "Tybee Island Beach", 32.0000, -80.8450, "8670892");
        add("GA2", "St. Simons Island", 31.1500, -81.3700, "TEC3499");
        add("SC1", "Myrtle Beach", 33.6890, -78.8860, "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3a/Myrtle_Beach%2C_SC%2C_USA_-_panoramio_%281%29.jpg/1280px-Myrtle_Beach%2C_SC%2C_USA_-_panoramio_%281%29.jpg", "8661070");
        add("SC2", "Folly Beach", 32.6550, -79.9400, "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f7/Folly_Pier_-_panoramio_%281%29.jpg/1280px-Folly_Pier_-_panoramio_%281%29.jpg", "TEC3127");
        add("SC3", "Coligny Beach, Hilton Head", 32.1400, -80.7500, "8669167");
        add("NC1", "Wrightsville Beach", 34.2080, -77.7960, "https://upload.wikimedia.org/wikipedia/commons/thumb/6/65/Surfer_Dude_-_Flickr_-_Michael_A._Richardson.jpg/1280px-Surfer_Dude_-_Flickr_-_Michael_A._Richardson.jpg", "8658163");
        add("NC2", "Nags Head", 35.9570, -75.6240, "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f6/Nags_Head%2C_United_States_%28Unsplash%29.jpg/1280px-Nags_Head%2C_United_States_%28Unsplash%29.jpg", "8652226");
        add("NC3", "Cape Hatteras", 35.2250, -75.5280, "https://upload.wikimedia.org/wikipedia/commons/thumb/1/19/Cape_Point_looking_west_toward_South_Beach_at_mid-tide_01-26-2021_%2851759671005%29.jpg/1280px-Cape_Point_looking_west_toward_South_Beach_at_mid-tide_01-26-2021_%2851759671005%29.jpg", "TEC2789");
        add("NC4", "Atlantic Beach", 34.6990, -76.7400, "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f1/Sandy_Atlantic_beach_%28Unsplash%29.jpg/1280px-Sandy_Atlantic_beach_%28Unsplash%29.jpg", "8656590");
        add("NC5", "Carolina Beach", 34.0350, -77.8880, "https://upload.wikimedia.org/wikipedia/commons/thumb/1/19/Entrance_to_the_beach_%2852974421823%29.jpg/1280px-Entrance_to_the_beach_%2852974421823%29.jpg", "8658559");

        // Virginia, Maryland, Delaware
        add("VA1", "Virginia Beach", 36.8530, -75.9780, "https://upload.wikimedia.org/wikipedia/commons/thumb/9/9c/Virginia_Beach%2C_United_States_%28Unsplash%29.jpg/1280px-Virginia_Beach%2C_United_States_%28Unsplash%29.jpg", "8639168");
        add("VA2", "Sandbridge Beach", 36.7284, -75.9369, "8639428");
        add("MD1", "Ocean City, Maryland", 38.3360, -75.0840, "https://upload.wikimedia.org/wikipedia/commons/thumb/5/5f/Ocean_City%2C_Maryland_-_panoramio_%284%29.jpg/1280px-Ocean_City%2C_Maryland_-_panoramio_%284%29.jpg", "8570280");
        add("MD2", "Assateague Island", 38.2372, -75.1366, "8570280");
        add("DE1", "Rehoboth Beach", 38.7160, -75.0760, "https://upload.wikimedia.org/wikipedia/commons/thumb/c/ca/Blue_Morning_at_Rehoboth.jpg/1280px-Blue_Morning_at_Rehoboth.jpg", "8557863");
        add("DE2", "Bethany Beach", 38.5390, -75.0550, "https://upload.wikimedia.org/wikipedia/commons/thumb/7/7c/Storm_at_Bethany_Beach.jpg/1280px-Storm_at_Bethany_Beach.jpg", "8557863");

        // New Jersey
        add("NJ1", "Asbury Park Beach", 40.2200, -74.0000, "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d9/Asbury_Park_July_10_Early_Morning_04.jpg/1280px-Asbury_Park_July_10_Early_Morning_04.jpg", "8532337");
        add("NJ2", "Cape May Beach", 38.9330, -74.9200, "8535962");
        add("NJ3", "Atlantic City Beach", 39.3540, -74.4240, "8534720");
        add("NJ4", "Point Pleasant Beach", 40.0900, -74.0380, "https://upload.wikimedia.org/wikipedia/commons/thumb/5/5b/2025-08-23_12_32_31_Rough_surf_along_Jenkinson%27s_Beach_in_Point_Pleasant_Beach%2C_Ocean_County%2C_New_Jersey.jpg/1280px-2025-08-23_12_32_31_Rough_surf_along_Jenkinson%27s_Beach_in_Point_Pleasant_Beach%2C_Ocean_County%2C_New_Jersey.jpg", "8532337");
        add("NJ5", "Ocean City, New Jersey", 39.2750, -74.5750, "https://upload.wikimedia.org/wikipedia/commons/thumb/f/fc/Ocean_City_NJ_beach_looking_south_from_Music_Pier.jpeg/1280px-Ocean_City_NJ_beach_looking_south_from_Music_Pier.jpeg", "8534770");

        // New York
        add("NY3", "Coney Island Beach", 40.5720, -73.9790, "https://upload.wikimedia.org/wikipedia/commons/thumb/b/ba/2008-05-17_Coney_Island%2C_Long_Island_017_Coney_Island%2C_Boardwalk%2C_Beach_%282677864525%29.jpg/1280px-2008-05-17_Coney_Island%2C_Long_Island_017_Coney_Island%2C_Boardwalk%2C_Beach_%282677864525%29.jpg", "8517741");
        add("NY4", "Long Beach, New York", 40.5830, -73.6580, "https://upload.wikimedia.org/wikipedia/commons/thumb/7/7a/Long_Beach%2C_NY_boardwalk_at_sunset.jpg/1280px-Long_Beach%2C_NY_boardwalk_at_sunset.jpg", "8516385");
        add("NY5", "Robert Moses State Park", 40.6250, -73.2660, "8515228");
        add("NY6", "Ditch Plains, Montauk", 41.0350, -71.9250, "8459681");

        // New England
        add("CT1", "Hammonasset Beach State Park", 41.2600, -72.5400, "8463701");
        add("RI1", "Narragansett Town Beach", 41.4380, -71.4470, "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4b/Narragansett%2C_Rhode_Island_%2853845090729%29.jpg/1280px-Narragansett%2C_Rhode_Island_%2853845090729%29.jpg", "8454658");
        add("RI2", "Misquamicut State Beach", 41.3232, -71.8025, "https://upload.wikimedia.org/wikipedia/commons/thumb/7/7d/Misquamicut_Beach%2C_RI_2010-08-07_2.JPG/1280px-Misquamicut_Beach%2C_RI_2010-08-07_2.JPG", "8458694");
        add("MA1", "Revere Beach", 42.4130, -70.9900, "https://upload.wikimedia.org/wikipedia/commons/thumb/1/17/Revere%2C_MA%2C_USA_-_panoramio_%2810%29.jpg/1280px-Revere%2C_MA%2C_USA_-_panoramio_%2810%29.jpg", "8443970");
        add("MA2", "Nauset Beach", 41.7900, -69.9350, "8447435");
        add("MA3", "Coast Guard Beach", 41.8390, -69.9460, "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d1/1CoastGuardBeachMA20160922_063241.jpg/1280px-1CoastGuardBeachMA20160922_063241.jpg", "8447435");
        add("MA4", "Good Harbor Beach", 42.6180, -70.6390, "8441841");
        add("MA5", "Crane Beach", 42.6820, -70.7660, "8441241");
        add("NH1", "Hampton Beach", 42.9080, -70.8100, "8429489");
        add("ME1", "Old Orchard Beach", 43.5150, -70.3770, "https://upload.wikimedia.org/wikipedia/commons/thumb/f/fa/Stormy_Weather_%2827885794643%29.jpg/1280px-Stormy_Weather_%2827885794643%29.jpg", "8418557");
        add("ME2", "Ogunquit Beach", 43.2500, -70.5900, "https://upload.wikimedia.org/wikipedia/commons/thumb/7/76/Qgunquit-beach-high-tide.jpg/1280px-Qgunquit-beach-high-tide.jpg", "8419399");
        add("ME3", "Popham Beach", 43.7360, -69.7960, "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d8/Surf_%2825138358805%29.jpg/1280px-Surf_%2825138358805%29.jpg", "8417177");

        // The Great Lakes. The wave model reaches these — off Chicago it agreed
        // with the Wilmette buoy to within a few inches — except the far western
        // tip of Lake Superior, so Park Point's surf tile stays blank. A lake
        // has no tide worth the name, so none of them names a station.
        add("MI1", "Sleeping Bear Dunes Beach", 44.8110, -86.0680, "https://upload.wikimedia.org/wikipedia/commons/thumb/9/9a/Sleeping_bear_walk_%28161333600%29.jpg/1280px-Sleeping_bear_walk_%28161333600%29.jpg", null);
        add("MI2", "Grand Haven State Park", 43.0530, -86.2480, "https://upload.wikimedia.org/wikipedia/commons/thumb/2/20/Grand_Haven_Beach_-_20260623.jpg/1280px-Grand_Haven_Beach_-_20260623.jpg", null);
        add("IL1", "North Avenue Beach", 41.9180, -87.6320, null);
        add("IN1", "West Beach, Indiana Dunes", 41.6245, -87.2191, null);
        add("WI1", "Bradford Beach", 43.0570, -87.8770, "https://upload.wikimedia.org/wikipedia/commons/thumb/8/89/Milwaukee_February_2026_59_%28Bradford_Beach%29.jpg/1280px-Milwaukee_February_2026_59_%28Bradford_Beach%29.jpg", null);
        add("OH1", "Headlands Beach State Park", 41.7565, -81.2909, "https://upload.wikimedia.org/wikipedia/commons/thumb/2/2e/Headlands_Beach_%2814231171650%29.jpg/1280px-Headlands_Beach_%2814231171650%29.jpg", null);
        add("MN1", "Park Point Beach", 46.7300, -92.0600, null);
        add("NY7", "Hamlin Beach State Park", 43.3620, -77.9490, null);

        // Puerto Rico
        add("PR1", "Flamenco Beach", 18.3270, -65.3160, "https://upload.wikimedia.org/wikipedia/commons/thumb/e/ea/Culebra%2C_Puerto_Rico_-_panoramio.jpg/1280px-Culebra%2C_Puerto_Rico_-_panoramio.jpg", "9752235");
        add("PR2", "Condado Beach", 18.4600, -66.0730, "https://upload.wikimedia.org/wikipedia/commons/thumb/2/20/Condado_Beach.jpg/1280px-Condado_Beach.jpg", "9755371");
        add("PR3", "Isla Verde Beach", 18.4460, -66.0120, "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c4/View_from_the_room_in_San_Juan%2C_Puerto_Rico.jpg/1280px-View_from_the_room_in_San_Juan%2C_Puerto_Rico.jpg", "9754986");
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
        add(id, name, latitude, longitude, null, tideStation);
    }

    private void add(String id, String name, double latitude, double longitude, String imageUrl, String tideStation) {
        beaches.add(new Beach(id, name, latitude, longitude, null, null, null, imageUrl, tideStation));
    }

    /**
     * Brings the beaches whose readings are missing or stale up to date, then
     * reports back as each batch lands so the list fills in progressively.
     *
     * <p>Cheap to call from anywhere: a beach whose reading is still current is
     * not asked about again, so with everything fresh this touches no network
     * at all, and a caller arriving while a pass is in flight joins it rather
     * than starting a second copy.
     *
     * <p>The listener stays registered until {@link #stopListening} drops it,
     * so a screen left sitting open is told when a reading that failed the
     * first time finally lands.
     */
    public void refreshAllData(OnDataRefreshListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
        // A screen has just come to the front, so whatever is missing is worth
        // trying for promptly again, however long the last wait had grown to.
        retryWaitMs = RETRY_AFTER_MS;
        fetchDue(listener);
    }

    /**
     * Stops telling a screen about arriving readings. Screens drop their
     * listener as they leave the front: one held past that keeps the activity
     * alive and redraws a screen nobody is looking at.
     */
    public void stopListening(OnDataRefreshListener listener) {
        listeners.remove(listener);
    }

    /**
     * Asks about every beach that needs asking about, in batches.
     *
     * @param listener told at once when there is nothing to fetch, so a screen
     *                 that asked still gets to draw what is already cached
     */
    private void fetchDue(@Nullable OnDataRefreshListener listener) {
        List<Beach> due = beachesDue(System.currentTimeMillis());
        if (due.isEmpty() || !refreshing.compareAndSet(false, true)) {
            // Everything current, or someone else's fan-out is already in
            // flight. Hand back what is cached now; a pass in flight will call
            // again as its results arrive.
            if (listener != null) {
                listener.onDataRefreshed();
            }
            return;
        }

        // The beaches are fetched in batches rather than one request each.
        // Open-Meteo takes many coordinates in a single call, so this is a
        // handful of requests for the whole coastline instead of one per beach
        // — which at this size would be hundreds of round trips. Photos are
        // curated rather than fetched, so weather is all that goes out.
        int batchSize = WeatherClient.BATCH_SIZE;
        outstanding.set((due.size() + batchSize - 1) / batchSize);

        for (int start = 0; start < due.size(); start += batchSize) {
            List<Beach> batch = due.subList(start, Math.min(start + batchSize, due.size()));
            double[] latitudes = new double[batch.size()];
            double[] longitudes = new double[batch.size()];
            for (int i = 0; i < batch.size(); i++) {
                latitudes[i] = batch.get(i).getLatitude();
                longitudes[i] = batch.get(i).getLongitude();
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
                            // inventing one. It stays due, so the retry below
                            // asks about it again.
                            return;
                        }
                        // Matched by id rather than by position: a pass covers
                        // only the beaches that were due, which is neither the
                        // whole list nor in its order.
                        updateConditions(batch.get(index).getId(), conditions);
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
     * The beaches worth asking about: those showing nothing yet, and those
     * whose reading has aged past {@link #FRESH_FOR_MS}.
     *
     * <p>Freshness is per beach, read off the reading's own timestamp, rather
     * than one window over the whole list. A single window made a batch that
     * failed indistinguishable from one that had just been fetched: the
     * twenty-five beaches in it kept the "Checking conditions" they started
     * with, and because the list as a whole counted as current, nothing asked
     * about them again. Opening one was the only thing that still fetched it,
     * the beach screen fetching its own beach — so a beach nobody opened could
     * sit there unchecked for as long as the app stayed open.
     */
    @VisibleForTesting
    List<Beach> beachesDue(long now) {
        List<Beach> due = new ArrayList<>();
        for (Beach beach : getBeaches()) {
            BeachConditions conditions = beach.getConditions();
            if (conditions == null || now - conditions.getFetchedAtMillis() >= FRESH_FOR_MS) {
                due.add(beach);
            }
        }
        return due;
    }

    /**
     * Records one finished request: tells the watching screens when it changed
     * something, and closes the pass out once the last one is in.
     */
    private void settle(boolean changed) {
        if (changed) {
            // Something got through, so the network is there: whatever is still
            // missing deserves the short wait again rather than the long one a
            // run of failures had backed off to.
            retryWaitMs = RETRY_AFTER_MS;
            for (OnDataRefreshListener waiting : listeners) {
                waiting.onDataRefreshed();
            }
        }
        if (outstanding.decrementAndGet() <= 0) {
            refreshing.set(false);
            scheduleRetry();
        }
    }

    /**
     * Asks again, shortly, about the beaches that came back with nothing.
     *
     * <p>Without this a dropped request left its beaches on "Checking
     * conditions" for as long as the screen stayed open, because the only thing
     * that ever started a pass was a screen coming to the front. Runs only
     * while a screen is watching, stops as soon as every beach has a reading,
     * and backs off while they keep coming back empty, so a phone out of signal
     * is not asking every twenty seconds indefinitely.
     */
    private void scheduleRetry() {
        if (listeners.isEmpty() || !anyWithoutConditions()) {
            return;
        }
        if (!retryScheduled.compareAndSet(false, true)) {
            return;
        }
        long wait = retryWaitMs;
        retryWaitMs = Math.min(wait * 2, MAX_RETRY_WAIT_MS);
        RETRY_CLOCK.schedule(() -> {
            retryScheduled.set(false);
            if (!listeners.isEmpty()) {
                fetchDue(null);
            }
        }, wait, TimeUnit.MILLISECONDS);
    }

    /** Whether any beach is still showing nothing at all. */
    private boolean anyWithoutConditions() {
        for (Beach beach : getBeaches()) {
            if (beach.getConditions() == null) {
                return true;
            }
        }
        return false;
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


    /** The beach with this id, or null if there is none. */
    @Nullable
    public Beach findById(@Nullable String beachId) {
        if (beachId == null) return null;
        synchronized (beaches) {
            for (Beach beach : beaches) {
                if (beachId.equals(beach.getId())) return beach;
            }
        }
        return null;
    }

    /**
     * Beaches whose name contains the query, ignoring case and punctuation.
     *
     * <p>Case is folded against {@link Locale#ROOT} rather than the phone's
     * own locale. The beach names are English, and a locale whose casing rules
     * differ from English's would fold the two sides of this comparison
     * apart: on a Turkish phone the default rules lowercase the I of Imperial
     * Beach to a dotless ı, which the typed "imperial" then no longer matches.
     *
     * <p>Apostrophes and full stops are dropped from both sides, and the query
     * is trimmed, because nobody types them the way the list spells them:
     * "swamis" has to find Swami's Beach and "st augustine" St. Augustine
     * Beach, and a space left after the last word should not empty the list.
     */
    public List<Beach> searchBeaches(String query) {
        String needle = query == null ? "" : fold(query).trim();
        synchronized (beaches) {
            if (needle.isEmpty()) return new ArrayList<>(beaches);
            return beaches.stream()
                .filter(b -> fold(b.getName()).contains(needle))
                .collect(Collectors.toList());
        }
    }

    @VisibleForTesting
    static String fold(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("['\u2019.]", "");
    }

    /**
     * The home list: these beaches within {@code radiusMiles} of a point,
     * nearest first, cut at {@code limit}. A radius of a thousand miles or
     * more filters nothing, which is what "All" asks for.
     *
     * <p>Each beach's distance is worked out once. The list used to sort on a
     * comparator that worked out two for every comparison it made - a few
     * thousand trigonometry calls over the whole coast on every keystroke in
     * the search box and every location fix.
     */
    public static List<Beach> nearestFirst(List<Beach> beaches, double lat, double lon,
                                           double radiusMiles, int limit) {
        boolean all = radiusMiles >= 1000.0;
        return beaches.stream()
            .map(b -> new AbstractMap.SimpleImmutableEntry<>(b, UnitConverter.calculateDistance(
                lat, lon, b.getLatitude(), b.getLongitude())))
            .filter(entry -> all || entry.getValue() <= radiusMiles)
            .sorted(Map.Entry.comparingByValue())
            .limit(limit)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    /**
     * The beach nearest a point, if one lies within {@code withinMiles} of it,
     * else null. For the SOS sheet, which must not name a beach the user is
     * nowhere near.
     */
    @Nullable
    public Beach nearestTo(double lat, double lon, double withinMiles) {
        Beach nearest = null;
        double best = withinMiles;
        synchronized (beaches) {
            for (Beach beach : beaches) {
                double miles = UnitConverter.calculateDistance(
                    lat, lon, beach.getLatitude(), beach.getLongitude());
                if (miles <= best) {
                    best = miles;
                    nearest = beach;
                }
            }
        }
        return nearest;
    }
}
