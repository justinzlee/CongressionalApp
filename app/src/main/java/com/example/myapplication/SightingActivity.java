package com.example.myapplication;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.widget.ImageViewCompat;

import com.bumptech.glide.Glide;
import com.example.myapplication.data.BeachRepository;
import com.example.myapplication.data.PhotoImporter;
import com.example.myapplication.data.SightingStore;
import com.example.myapplication.model.Beach;
import com.example.myapplication.model.Sighting;
import com.example.myapplication.model.SightingSpecies;
import com.example.myapplication.util.EdgeToEdge;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

/**
 * Reporting an animal seen at a beach: what it was, whether it was in the water
 * or washed up on the sand, optionally a photograph and a note.
 *
 * <p>The "washed up" choice is what makes this more than a diary. It files the
 * report as a high-severity hazard for two days — see {@link
 * com.example.myapplication.model.ReportHazards} — so the beach screen, the
 * Alerts tab and the tab badge all carry it, and it hands the user straight to
 * the share sheet on save so the warning can reach people who are not holding
 * this phone.
 */
public class SightingActivity extends AppCompatActivity {

    public static final String EXTRA_BEACH_ID = "extra_beach_id";

    private static final String STATE_PENDING_CAPTURE = "pending_capture";
    private static final String STATE_SPECIES = "species";
    private static final String STATE_STRANDED = "stranded";
    private static final String STATE_PENDING_PHOTO = "pending_photo";

    private Beach beach;
    private SightingStore store;

    private SightingSpecies species = SightingSpecies.DOLPHIN;
    private boolean stranded;

    private View dolphinChoice, sealChoice, seaLionChoice;
    private View inWaterChoice, strandedChoice, strandedNote;
    private ImageView photo;
    private View photoEmpty;
    private TextInputEditText note;

    /**
     * A photograph already imported into the sighting directory but not yet
     * attached to a saved report. Deleted if the user leaves without saving —
     * otherwise it would sit in private storage with nothing pointing at it and
     * nothing able to clean it up.
     */
    @Nullable private File pendingPhoto;

    private ActivityResultLauncher<PickVisualMediaRequest> photoPicker;
    private ActivityResultLauncher<Uri> photoCamera;
    private File pendingCapture;

    /** Set once the report is stored, so {@link #onDestroy} keeps its photo. */
    private boolean saved;

    public static Intent intentFor(Context context, String beachId) {
        Intent intent = new Intent(context, SightingActivity.class);
        intent.putExtra(EXTRA_BEACH_ID, beachId);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sighting);

        String beachId = getIntent().getStringExtra(EXTRA_BEACH_ID);
        beach = BeachRepository.getInstance().getBeaches().stream()
            .filter(b -> Objects.equals(b.getId(), beachId))
            .findFirst()
            .orElse(null);
        if (beach == null) {
            finish();
            return;
        }

        EdgeToEdge.applyTopInset(findViewById(R.id.sightingHeader));
        EdgeToEdge.applyBottomInset(findViewById(R.id.sightingScrollView));
        findViewById(R.id.sightingBackButton).setOnClickListener(
            v -> getOnBackPressedDispatcher().onBackPressed());

        store = new SightingStore(this);

        ((TextView) findViewById(R.id.sightingTitle))
            .setText(getString(R.string.sighting_title_for, beach.getName()));

        dolphinChoice = findViewById(R.id.sightingDolphin);
        sealChoice = findViewById(R.id.sightingSeal);
        seaLionChoice = findViewById(R.id.sightingSeaLion);
        inWaterChoice = findViewById(R.id.sightingInWater);
        strandedChoice = findViewById(R.id.sightingStranded);
        strandedNote = findViewById(R.id.sightingStrandedNote);
        photo = findViewById(R.id.sightingPhoto);
        photoEmpty = findViewById(R.id.sightingPhotoEmpty);
        note = findViewById(R.id.sightingNote);

        if (savedInstanceState != null) {
            species = SightingSpecies.parse(savedInstanceState.getString(STATE_SPECIES));
            stranded = savedInstanceState.getBoolean(STATE_STRANDED);
            String pendingCapturePath = savedInstanceState.getString(STATE_PENDING_CAPTURE);
            if (pendingCapturePath != null) pendingCapture = new File(pendingCapturePath);
            String pendingPhotoPath = savedInstanceState.getString(STATE_PENDING_PHOTO);
            if (pendingPhotoPath != null) pendingPhoto = new File(pendingPhotoPath);
        }
        registerPhotoLaunchers();

        dolphinChoice.setOnClickListener(v -> setSpecies(SightingSpecies.DOLPHIN));
        sealChoice.setOnClickListener(v -> setSpecies(SightingSpecies.SEAL));
        seaLionChoice.setOnClickListener(v -> setSpecies(SightingSpecies.SEA_LION));
        inWaterChoice.setOnClickListener(v -> setStranded(false));
        strandedChoice.setOnClickListener(v -> setStranded(true));

        View.OnClickListener choosePhoto = v -> openPhotoSheet();
        photo.setOnClickListener(choosePhoto);
        photoEmpty.setOnClickListener(choosePhoto);

        findViewById(R.id.sightingSave).setOnClickListener(v -> save());

        showSpecies();
        showWhere();
        bindPhoto();
    }

    private void setSpecies(SightingSpecies chosen) {
        species = chosen;
        showSpecies();
    }

    private void setStranded(boolean value) {
        stranded = value;
        showWhere();
    }

    private void showSpecies() {
        paintChoice(dolphinChoice, R.id.sightingDolphinIcon, R.id.sightingDolphinLabel,
            species == SightingSpecies.DOLPHIN);
        paintChoice(sealChoice, R.id.sightingSealIcon, R.id.sightingSealLabel,
            species == SightingSpecies.SEAL);
        paintChoice(seaLionChoice, R.id.sightingSeaLionIcon, R.id.sightingSeaLionLabel,
            species == SightingSpecies.SEA_LION);
    }

    /**
     * The "washed up" row turns the whole card red rather than brand teal, and
     * brings up the note explaining what it is about to do. Selecting it is the
     * one choice on this screen with consequences beyond the phone, so it does
     * not look like the other one.
     */
    private void showWhere() {
        paintWhere(inWaterChoice, R.id.sightingInWaterMark, !stranded, false);
        paintWhere(strandedChoice, R.id.sightingStrandedMark, stranded, true);
        strandedNote.setVisibility(stranded ? View.VISIBLE : View.GONE);
    }

    /** One of the three species cards: filled and tinted when it is the choice. */
    private void paintChoice(View card, int iconId, int labelId, boolean selected) {
        paintCard(card, selected, R.color.accent_tint, R.color.primary);

        int accent = ContextCompat.getColor(this,
            selected ? R.color.primary : R.color.text_secondary);
        ImageViewCompat.setImageTintList(card.findViewById(iconId), ColorStateList.valueOf(accent));
        ((TextView) card.findViewById(labelId)).setTextColor(accent);
    }

    private void paintWhere(View card, int markId, boolean selected, boolean danger) {
        int tintRes = danger ? R.color.danger_tint : R.color.accent_tint;
        int edgeRes = danger ? R.color.danger : R.color.primary;
        paintCard(card, selected, tintRes, edgeRes);

        int color = ContextCompat.getColor(this, selected ? edgeRes : R.color.text_secondary);
        ImageViewCompat.setImageTintList(card.findViewById(markId), ColorStateList.valueOf(color));
    }

    /** Fill and hairline for a choice card, mutated in place. */
    private void paintCard(View card, boolean selected, int tintRes, int edgeRes) {
        Drawable background = card.getBackground();
        if (!(background instanceof GradientDrawable)) return;
        GradientDrawable shape = (GradientDrawable) background.mutate();
        shape.setColor(ContextCompat.getColor(this, selected ? tintRes : R.color.surface));
        shape.setStroke(Math.round(getResources().getDisplayMetrics().density),
            ContextCompat.getColor(this, selected ? edgeRes : R.color.divider));
    }

    /**
     * Stores the report, then — for a stranding — goes straight to the share
     * sheet. Warning people is the point of filing that one, and asking them to
     * find a share button afterwards is how it does not get done.
     */
    private void save() {
        Sighting sighting = store.add(beach.getId(), species, stranded,
            note.getText() == null ? null : note.getText().toString(), pendingPhoto);
        saved = true;

        Toast.makeText(this, R.string.sighting_saved, Toast.LENGTH_SHORT).show();
        if (stranded) {
            SightingShare.share(this, beach, sighting);
        }
        finish();
    }

    // ── The photograph ────────────────────────────────────────────────────

    private void bindPhoto() {
        if (pendingPhoto == null || !pendingPhoto.exists()) {
            photo.setVisibility(View.GONE);
            photoEmpty.setVisibility(View.VISIBLE);
            return;
        }
        photo.setVisibility(View.VISIBLE);
        photoEmpty.setVisibility(View.GONE);
        Glide.with(this).load(pendingPhoto).centerCrop().into(photo);
    }

    private void openPhotoSheet() {
        BeachPhotoSheet.show(this, beach.getName(), pendingPhoto != null,
            new BeachPhotoSheet.Choice() {
                @Override
                public void onTakePhoto() {
                    takePhoto();
                }

                @Override
                public void onChoosePhoto() {
                    photoPicker.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build());
                }

                @Override
                public void onRemovePhoto() {
                    discardPendingPhoto();
                    bindPhoto();
                }
            });
    }

    private void registerPhotoLaunchers() {
        photoPicker = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(),
            uri -> {
                if (uri != null) importPhoto(uri, null);
            });

        photoCamera = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            taken -> {
                File capture = pendingCapture;
                pendingCapture = null;
                if (capture == null) return;
                if (Boolean.TRUE.equals(taken)) {
                    importPhoto(Uri.fromFile(capture), capture);
                } else {
                    capture.delete();
                }
            });
    }

    private void takePhoto() {
        File capture = null;
        try {
            File dir = new File(getCacheDir(), "camera");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                throw new IOException("Could not create " + dir);
            }
            capture = File.createTempFile("capture-", ".jpg", dir);
            Uri target = FileProvider.getUriForFile(
                this, getPackageName() + ".fileprovider", capture);
            pendingCapture = capture;
            photoCamera.launch(target);
        } catch (ActivityNotFoundException e) {
            pendingCapture = null;
            if (capture != null) capture.delete();
            Toast.makeText(this, R.string.photo_no_camera, Toast.LENGTH_SHORT).show();
        } catch (IOException | IllegalArgumentException e) {
            pendingCapture = null;
            if (capture != null) capture.delete();
            Toast.makeText(this, R.string.photo_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void importPhoto(Uri source, @Nullable File cleanup) {
        new PhotoImporter(this).importInto(store.photoDir(), beach.getId(), source,
            new PhotoImporter.Callback() {
                @Override
                public void onSaved(File imported) {
                    if (cleanup != null) cleanup.delete();
                    if (isFinishing() || isDestroyed()) {
                        // Nothing will ever point at this file now.
                        imported.delete();
                        return;
                    }
                    discardPendingPhoto();
                    pendingPhoto = imported;
                    bindPhoto();
                }

                @Override
                public void onFailed(Exception e) {
                    if (cleanup != null) cleanup.delete();
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(SightingActivity.this,
                        R.string.photo_failed, Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void discardPendingPhoto() {
        if (pendingPhoto != null) {
            pendingPhoto.delete();
            pendingPhoto = null;
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_SPECIES, species.name());
        outState.putBoolean(STATE_STRANDED, stranded);
        if (pendingCapture != null) {
            outState.putString(STATE_PENDING_CAPTURE, pendingCapture.getAbsolutePath());
        }
        if (pendingPhoto != null) {
            outState.putString(STATE_PENDING_PHOTO, pendingPhoto.getAbsolutePath());
        }
    }

    /**
     * Cleans up a photograph that was imported but never attached to a report.
     *
     * <p>Guarded on {@code isFinishing()} rather than running unconditionally:
     * this also fires on a rotation, and deleting the picture there would take
     * it away from a user who has done nothing but turn their phone.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing() && !saved) {
            discardPendingPhoto();
        }
    }
}
