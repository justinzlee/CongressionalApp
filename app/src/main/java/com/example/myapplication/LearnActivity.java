package com.example.myapplication;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.StingGuideActivity.Sting;
import com.example.myapplication.data.PracticeStore;
import com.example.myapplication.util.EdgeToEdge;

/**
 * The Learn tab: the app's lessons in one place, so knowing what a rip current
 * looks like is a tab away rather than buried in Settings. Lessons are grouped
 * by when you need them — before you get in, once you are in the water, and
 * what lives there.
 */
public class LearnActivity extends AppCompatActivity {

    /** One lesson: a row on this screen, and a full screen of its own. */
    private static final class Lesson {
        @StringRes final int titleRes;
        @StringRes final int subtitleRes;
        @DrawableRes final int iconRes;
        final Class<? extends Activity> target;
        /** Set when the lesson is one animal's first aid, null otherwise. */
        final Sting sting;

        Lesson(int titleRes, int subtitleRes, int iconRes, Class<? extends Activity> target) {
            this(titleRes, subtitleRes, iconRes, target, null);
        }

        Lesson(int titleRes, int subtitleRes, int iconRes,
               Class<? extends Activity> target, Sting sting) {
            this.titleRes = titleRes;
            this.subtitleRes = subtitleRes;
            this.iconRes = iconRes;
            this.target = target;
            this.sting = sting;
        }

        /** Builds the lesson row for one stinging animal. */
        static Lesson of(Sting sting) {
            return new Lesson(sting.titleRes, sting.subtitleRes, sting.iconRes,
                StingGuideActivity.class, sting);
        }
    }

    /** A headed group of lessons, drawn as one card. */
    private static final class Group {
        @StringRes final int headingRes;
        final Lesson[] lessons;

        Group(int headingRes, Lesson[] lessons) {
            this.headingRes = headingRes;
            this.lessons = lessons;
        }
    }

    private static final Group[] GROUPS = {
        // The flag board is on the sand, not in the water, so it belongs with
        // what you check on the way down rather than with rip currents.
        new Group(R.string.learn_group_before, new Lesson[] {
            new Lesson(R.string.flag_entry_title, R.string.flag_entry_subtitle,
                R.drawable.ic_flag, FlagGuideActivity.class),
        }),
        new Group(R.string.learn_group_water, new Lesson[] {
            new Lesson(R.string.guide_entry_title, R.string.guide_entry_subtitle,
                R.drawable.ic_wave, RipGuideActivity.class),
        }),
        // Each animal is its own lesson: someone who has just been stung wants
        // that one set of steps, not a list of five to pick through.
        new Group(R.string.learn_group_marine_life, new Lesson[] {
            new Lesson(R.string.wildlife_entry_title, R.string.wildlife_entry_subtitle,
                R.drawable.ic_fish, WildlifeGuideActivity.class),
            Lesson.of(Sting.STINGRAY),
            Lesson.of(Sting.JELLYFISH),
            Lesson.of(Sting.MAN_O_WAR),
            Lesson.of(Sting.SEA_URCHIN),
            Lesson.of(Sting.SEA_LICE),
        }),
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_learn);

        EdgeToEdge.applyTopInset(findViewById(R.id.learnHeader));
        EdgeToEdge.applyBottomInset(findViewById(R.id.tabBar));

        TabBar.setup(this, TabBar.Tab.LEARN);

        findViewById(R.id.learnPracticeCard).setOnClickListener(
            v -> startActivity(new Intent(this, PracticeActivity.class)));

        buildLessons(LayoutInflater.from(this));
    }

    private void buildLessons(LayoutInflater inflater) {
        ViewGroup container = findViewById(R.id.learnGroups);
        for (Group group : GROUPS) {
            View card = inflater.inflate(R.layout.item_learn_group, container, false);
            ((TextView) card.findViewById(R.id.learnGroupHeading)).setText(group.headingRes);

            ViewGroup rows = card.findViewById(R.id.learnGroupRows);
            for (Lesson lesson : group.lessons) {
                View row = inflater.inflate(R.layout.item_learn_row, rows, false);
                ((TextView) row.findViewById(R.id.learnRowTitle)).setText(lesson.titleRes);
                ((TextView) row.findViewById(R.id.learnRowSubtitle)).setText(lesson.subtitleRes);
                ((ImageView) row.findViewById(R.id.learnRowIcon)).setImageResource(lesson.iconRes);
                row.setOnClickListener(v -> startActivity(lesson.sting != null
                    ? StingGuideActivity.intentFor(this, lesson.sting)
                    : new Intent(this, lesson.target)));
                rows.addView(row);
            }

            container.addView(card);
        }
    }

    /**
     * The practice card is the one thing on this screen that changes, so its
     * subtitle is written fresh each time the tab comes forward — including
     * after finishing a round, which happens while this screen is behind it.
     */
    private void bindPracticeCard() {
        PracticeStore store = new PracticeStore(this);
        TextView subtitle = findViewById(R.id.learnPracticeSubtitle);

        if (!store.isDoneToday()) {
            subtitle.setText(R.string.practice_entry_subtitle);
            return;
        }

        int streak = store.getStreak();
        if (streak <= 1) {
            subtitle.setText(R.string.practice_entry_streak_one);
        } else {
            subtitle.setText(getString(R.string.practice_entry_streak, streak));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Alerts may have been read since this screen was last in front.
        TabBar.refreshAlertBadge(this);
        bindPracticeCard();
    }
}
