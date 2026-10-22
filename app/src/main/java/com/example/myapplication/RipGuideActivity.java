package com.example.myapplication;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.util.EdgeToEdge;

/**
 * How to spot a rip current, what to do if you are caught in one, and what to
 * tell the lifeguards once you are out. Static safety guidance — the one screen
 * here that needs no live data at all.
 */
public class RipGuideActivity extends AppCompatActivity {

    /** Paired title/body string resources, in the order they are shown. */
    private static final int[][] SIGNS = {
        { R.string.guide_sign_1_title, R.string.guide_sign_1_body },
        { R.string.guide_sign_2_title, R.string.guide_sign_2_body },
        { R.string.guide_sign_3_title, R.string.guide_sign_3_body },
        { R.string.guide_sign_4_title, R.string.guide_sign_4_body },
    };

    /**
     * What a lifeguard needs to hear from someone who got out of a rip. The
     * rip is still there for the next swimmer, a friend may still be in it,
     * and water breathed in can cause trouble hours later — each one is
     * something only the person who was caught can tell them.
     */
    private static final int[][] TELL = {
        { R.string.guide_tell_1_title, R.string.guide_tell_1_body },
        { R.string.guide_tell_2_title, R.string.guide_tell_2_body },
        { R.string.guide_tell_3_title, R.string.guide_tell_3_body },
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rip_guide);

        EdgeToEdge.applyTopInset(findViewById(R.id.guideHeader));
        EdgeToEdge.applyBottomInset(findViewById(R.id.guideScrollView));

        findViewById(R.id.guideBackButton).setOnClickListener(
            v -> getOnBackPressedDispatcher().onBackPressed());

        fill(findViewById(R.id.guideSignList), SIGNS);
        fill(findViewById(R.id.guideTellList), TELL);
    }

    /** Numbered title-and-body rows, one per pair. */
    private void fill(ViewGroup list, int[][] rows) {
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < rows.length; i++) {
            View row = inflater.inflate(R.layout.item_guide_sign, list, false);
            ((TextView) row.findViewById(R.id.guideNumber)).setText(String.valueOf(i + 1));
            ((TextView) row.findViewById(R.id.guideSignTitle)).setText(rows[i][0]);
            ((TextView) row.findViewById(R.id.guideSignBody)).setText(rows[i][1]);
            list.addView(row);
        }
    }
}
