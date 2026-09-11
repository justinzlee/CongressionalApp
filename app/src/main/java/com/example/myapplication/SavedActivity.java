package com.example.myapplication;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.example.myapplication.data.BeachRepository;
import com.example.myapplication.data.SavedBeachStore;
import com.example.myapplication.model.Beach;
import com.example.myapplication.model.SafetyLevel;
import com.example.myapplication.util.EdgeToEdge;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The beaches the user has saved, each showing its current swim verdict so
 * the list answers "where can I go today?" at a glance.
 */
public class       SavedActivity extends AppCompatActivity {

    private BeachRepository repository;
    private SavedBeachStore store;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved);

        EdgeToEdge.applyTopInset(findViewById(R.id.savedHeader));
        EdgeToEdge.applyBottomInset(findViewById(R.id.tabBar));

        repository = BeachRepository.getInstance();
        store = new SavedBeachStore(this);

        TabBar.setup(this, TabBar.Tab.SAVED);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // A beach may have been saved or unsaved on the detail screen.
        bindSaved();
        // Alerts may have been read since this screen was last in front.
        TabBar.refreshAlertBadge(this);

        // Tabs are brought back to the front rather than rebuilt, so asking
        // only on the way in left these verdicts on whatever the water was
        // doing when the tab first opened. Within the repository's freshness
        // window this costs nothing. Conditions arrive asynchronously; redraw
        // once they land so the verdicts are not stuck on "Checking conditions".
        repository.refreshAllData(() -> runOnUiThread(() -> {
            bindSaved();
            // Alerts derived from these readings only exist once they land.
            TabBar.refreshAlertBadge(this);
        }));
    }

    private void bindSaved() {
        Set<String> savedIds = store.getSavedIds();
        List<Beach> saved = new ArrayList<>();
        for (Beach beach : repository.getBeaches()) {
            if (savedIds.contains(beach.getId())) {
                saved.add(beach);
            }
        }

        View empty = findViewById(R.id.savedEmpty);
        TextView heading = findViewById(R.id.savedHeading);
        ViewGroup list = findViewById(R.id.savedList);
        list.removeAllViews();

        if (saved.isEmpty()) {
            empty.setVisibility(View.VISIBLE);
            heading.setVisibility(View.GONE);
            return;
        }

        empty.setVisibility(View.GONE);
        heading.setVisibility(View.VISIBLE);
        heading.setText(getResources().getQuantityString(
            R.plurals.saved_count, saved.size(), saved.size()));

        LayoutInflater inflater = LayoutInflater.from(this);
        for (Beach beach : saved) {
            list.addView(buildRow(inflater, list, beach));
        }
    }

    private View buildRow(LayoutInflater inflater, ViewGroup parent, Beach beach) {
        View row = inflater.inflate(R.layout.item_saved_beach, parent, false);

        ((TextView) row.findViewById(R.id.savedName)).setText(beach.getName());

        SafetyLevel level = SafetyLevel.evaluate(beach);
        int color = ContextCompat.getColor(this,
            level != null ? level.getColorRes() : R.color.text_secondary);
        int tint = ContextCompat.getColor(this,
            level != null ? level.getTintRes() : R.color.surface_alt);

        ImageView icon = row.findViewById(R.id.savedLevelIcon);
        icon.setImageResource(level != null ? level.getIconRes() : R.drawable.ic_warning_circle);
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(color));

        Drawable chip = row.findViewById(R.id.savedLevelChip).getBackground();
        if (chip instanceof GradientDrawable) {
            ((GradientDrawable) chip.mutate()).setColor(tint);
        }

        TextView verdict = row.findViewById(R.id.savedVerdict);
        verdict.setText(level != null ? level.getShortVerdictRes() : R.string.verdict_unknown);
        verdict.setTextColor(color);

        row.setOnClickListener(v -> {
            Intent intent = new Intent(this, BeachDetailActivity.class);
            intent.putExtra(BeachDetailActivity.EXTRA_BEACH_ID, beach.getId());
            startActivity(intent);
        });
        return row;
    }
}
