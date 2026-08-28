package com.example.myapplication;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.util.EdgeToEdge;

/**
 * How to spot a rip current, and what to do if you are caught in one. Static
 * safety guidance — the one screen here that needs no live data at all.
 */
public class RipGuideActivity extends AppCompatActivity {

    /** Paired title/body string resources, in the order they are shown. */
    private static final int[][] SIGNS = {
        { R.string.guide_sign_1_title, R.string.guide_sign_1_body },
        { R.string.guide_sign_2_title, R.string.guide_sign_2_body },
        { R.string.guide_sign_3_title, R.string.guide_sign_3_body },
        { R.string.guide_sign_4_title, R.string.guide_sign_4_body },
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rip_guide);

        EdgeToEdge.applyTopInset(findViewById(R.id.guideHeader));
        EdgeToEdge.applyBottomInset(findViewById(R.id.guideScrollView));

        findViewById(R.id.guideBackButton).setOnClickListener(
            v -> getOnBackPressedDispatcher().onBackPressed());

        ViewGroup list = findViewById(R.id.guideSignList);
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
