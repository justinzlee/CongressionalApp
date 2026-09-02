package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.util.EdgeToEdge;

/**
 * The Learn tab: the app's lessons in one place, so knowing what a rip current
 * looks like is a tab away rather than buried in Settings.
 */
public class LearnActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_learn);

        EdgeToEdge.applyTopInset(findViewById(R.id.learnHeader));
        EdgeToEdge.applyBottomInset(findViewById(R.id.tabBar));

        TabBar.setup(this, TabBar.Tab.LEARN);

        findViewById(R.id.learnRipRow).setOnClickListener(
            v -> startActivity(new Intent(this, RipGuideActivity.class)));

        findViewById(R.id.learnFlagRow).setOnClickListener(
            v -> startActivity(new Intent(this, FlagGuideActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Alerts may have been read since this screen was last in front.
        TabBar.refreshAlertBadge(this);
    }
}
