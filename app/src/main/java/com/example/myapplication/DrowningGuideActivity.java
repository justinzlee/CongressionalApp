package com.example.myapplication;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.util.EdgeToEdge;

/**
 * How to tell that someone in the water is drowning, and what to do about it.
 * Static safety guidance, like the rip-current guide it sits beside on the
 * Learn tab.
 *
 * <p>The signs are the instinctive drowning response. A drowning person is
 * usually silent and still-looking — no splashing, no shouting, no waving —
 * and has well under a minute on the surface, which is why the splashing,
 * shouting picture most people carry gets them overlooked.
 */
public class DrowningGuideActivity extends AppCompatActivity {

    /** Paired title/body string resources, in the order they are shown. */
    private static final int[][] SIGNS = {
        { R.string.drowning_sign_1_title, R.string.drowning_sign_1_body },
        { R.string.drowning_sign_2_title, R.string.drowning_sign_2_body },
        { R.string.drowning_sign_3_title, R.string.drowning_sign_3_body },
        { R.string.drowning_sign_4_title, R.string.drowning_sign_4_body },
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drowning_guide);

        EdgeToEdge.applyTopInset(findViewById(R.id.drowningGuideHeader));
        EdgeToEdge.applyBottomInset(findViewById(R.id.drowningGuideScrollView));

        findViewById(R.id.drowningGuideBackButton).setOnClickListener(
            v -> getOnBackPressedDispatcher().onBackPressed());

        ViewGroup list = findViewById(R.id.drowningSignList);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < SIGNS.length; i++) {
            View row = inflater.inflate(R.layout.item_guide_sign, list, false);
            ((TextView) row.findViewById(R.id.guideNumber)).setText(String.valueOf(i + 1));
            ((TextView) row.findViewById(R.id.guideSignTitle)).setText(SIGNS[i][0]);
            ((TextView) row.findViewById(R.id.guideSignBody)).setText(SIGNS[i][1]);
            list.addView(row);
        }
    }
}
