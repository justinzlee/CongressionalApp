package com.example.myapplication;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

import com.bumptech.glide.Glide;
import com.example.myapplication.data.BeachRepository;
import com.example.myapplication.data.PhotoImporter;
import com.example.myapplication.data.RockRatingStore;
import com.example.myapplication.model.Beach;
import com.example.myapplication.model.RockRating;
import com.example.myapplication.util.EdgeToEdge;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;

import java.io.File;
import java.io.IOException;

/**
 * Rating how rocky a beach is underfoot, from 0 to 10.
 *
 * <p>The screen is built around one problem: a number out of ten is meaningless
 * between two people who have not agreed what the numbers mean. So the three
 * drawn references come first, the one matching the current value is
 * highlighted as the slider moves, and tapping a reference jumps the slider to
 * it. By the time a rating is saved the user has looked at the scale, not
 * guessed at it.
 *
 * <p>See {@link RockRating} for the scale itself, and {@link
 * com.example.myapplication.model.ReportHazards} for what a high rating does
 * once it is saved.
 */
public class RockRatingActivity extends AppCompatActivity {

    public static final String EXTRA_BEACH_ID = "extra_beach_id";

    /** Where a camera capture is being written — see {@link BeachDetailActivity}. */
    private static final String STATE_PENDING_CAPTURE = "pending_capture";

    /** The slider position, so a rotation does not throw away an unsaved rating. */
    private static final String STATE_RATING = "rating";

    /**
     * Where the slider starts on a beach nobody has rated. The middle of the
     * scale, which is also the middle reference picture, so the first thing the
     * user sees is a value sitting next to the image explaining it — rather than
     * a 0 that reads as an answer already given.
     */
    private static final int DEFAULT_RATING = 5;

    private static final int[] REFERENCES = {
        RockRating.REFERENCE_LOW, RockRating.REFERENCE_MID, RockRating.REFERENCE_HIGH };

    private Beach beach;
    private RockRatingStore store;

    private Slider slider;
    private TextView value, band, advice;
    private ImageView photo;
    private View photoEmpty;
    private MaterialButton clear;

    /** One per reference picture, in {@link #REFERENCES} order, for the highlight. */
    private final View[] referenceCards = new View[REFERENCES.length];

    private ActivityResultLauncher<PickVisualMediaRequest> photoPicker;
    private ActivityResultLauncher<Uri> photoCamera;
    private File pendingCapture;

    public static Intent intentFor(Context context, String beachId) {
        Intent intent = new Intent(context, RockRatingActivity.class);
        intent.putExtra(EXTRA_BEACH_ID, beachId);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rock_rating);

        String beachId = getIntent().getStringExtra(EXTRA_BEACH_ID);
        beach = BeachRepository.getInstance().findById(beachId);
        if (beach == null) {
            finish();
            return;
        }

        EdgeToEdge.applyTopInset(findViewById(R.id.rocksHeader));
        EdgeToEdge.applyBottomInset(findViewById(R.id.rocksScrollView));
        findViewById(R.id.rocksBackButton).setOnClickListener(
            v -> getOnBackPressedDispatcher().onBackPressed());

        store = new RockRatingStore(this);

        ((TextView) findViewById(R.id.rocksTitle))
            .setText(getString(R.string.rocks_title_for, beach.getName()));

        slider = findViewById(R.id.rocksSlider);
        value = findViewById(R.id.rocksValue);
        band = findViewById(R.id.rocksBand);
        advice = findViewById(R.id.rocksAdvice);
        photo = findViewById(R.id.rocksPhoto);
        photoEmpty = findViewById(R.id.rocksPhotoEmpty);
        clear = findViewById(R.id.rocksClear);

        buildReferences();

        if (savedInstanceState != null) {
            String pending = savedInstanceState.getString(STATE_PENDING_CAPTURE);
            if (pending != null) pendingCapture = new File(pending);
        }
        registerPhotoLaunchers();

        int start = savedInstanceState != null
            ? savedInstanceState.getInt(STATE_RATING, DEFAULT_RATING)
            : startingRating();
        slider.setValue(start);
        slider.addOnChangeListener((s, v, fromUser) -> showRating(Math.round(v)));
        showRating(start);

        bindPhoto();
        View.OnClickListener choosePhoto = v -> openPhotoSheet();
        photo.setOnClickListener(choosePhoto);
        photoEmpty.setOnClickListener(choosePhoto);

        findViewById(R.id.rocksSave).setOnClickListener(v -> save());

        boolean rated = store.isRated(beach.getId());
        clear.setVisibility(rated ? View.VISIBLE : View.GONE);
        clear.setOnClickListener(v -> clearRating());
    }

    /** The rating already on this beach, or the neutral default. */
    private int startingRating() {
        int stored = store.ratingFor(beach.getId());
        return RockRating.isRated(stored) ? stored : DEFAULT_RATING;
    }

    /** The three drawn references, each tappable to jump the slider to it. */
    private void buildReferences() {
        ViewGroup row = findViewById(R.id.rocksReferenceRow);
        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < REFERENCES.length; i++) {
            int reference = REFERENCES[i];
            View card = inflater.inflate(R.layout.item_rock_reference, row, false);

            ((ImageView) card.findViewById(R.id.rockReferenceImage))
                .setImageResource(RockRating.referenceImageRes(reference));
            ((TextView) card.findViewById(R.id.rockReferenceCaption))
                .setText(RockRating.referenceCaptionRes(reference));

            card.setOnClickListener(v -> slider.setValue(reference));

            referenceCards[i] = card;
            row.addView(card);
        }
    }

    /** Paints every part of the screen that depends on the current number. */
    private void showRating(int rating) {
        value.setText(getString(R.string.rocks_value, rating));
        band.setText(RockRating.labelRes(rating));
        advice.setText(RockRating.adviceRes(rating));

        int nearest = RockRating.nearestReference(rating);
        for (int i = 0; i < REFERENCES.length; i++) {
            paintReference(referenceCards[i], REFERENCES[i] == nearest);
        }
    }

    /**
     * The reference the current rating sits nearest gets the brand-coloured
     * ring. Only the stroke colour changes — the stroke is always there — so
     * nothing shifts by a pixel as the selection moves along the row.
     */
    private void paintReference(View card, boolean selected) {
        Drawable background = card.findViewById(R.id.rockReferenceFrame).getBackground();
        if (background instanceof GradientDrawable) {
            int width = Math.round(2 * getResources().getDisplayMetrics().density);
            ((GradientDrawable) background.mutate()).setStroke(width,
                ContextCompat.getColor(this, selected ? R.color.primary : R.color.divider));
        }
    }

    private void save() {
        store.save(beach.getId(), Math.round(slider.getValue()));
        Toast.makeText(this, R.string.rocks_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void clearRating() {
        store.clear(beach.getId());
        Toast.makeText(this, R.string.rocks_cleared, Toast.LENGTH_SHORT).show();
        finish();
    }

    // ── The photograph ────────────────────────────────────────────────────
    //
    // Saved the moment it is picked rather than waiting for the Save button,
    // which is how the beach hero photo already behaves. Picking a photo is
    // deliberate enough to stand on its own, and it means the picture survives
    // someone backing out of the screen without committing to a number.

    private void bindPhoto() {
        File existing = store.photoFor(beach.getId());
        if (existing == null) {
            photo.setVisibility(View.GONE);
            photoEmpty.setVisibility(View.VISIBLE);
            return;
        }
        photo.setVisibility(View.VISIBLE);
        photoEmpty.setVisibility(View.GONE);
        Glide.with(this).load(existing).centerCrop().into(photo);
    }

    private void openPhotoSheet() {
        BeachPhotoSheet.show(this, beach.getName(), store.photoFor(beach.getId()) != null,
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
                    store.removePhoto(beach.getId());
                    bindPhoto();
                }
            });
    }

    private void registerPhotoLaunchers() {
        photoPicker = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(),
            uri -> {
                if (uri != null) submitPhoto(uri, null);
            });

        photoCamera = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            taken -> {
                File capture = pendingCapture;
                pendingCapture = null;
                if (capture == null) return;
                if (Boolean.TRUE.equals(taken)) {
                    submitPhoto(Uri.fromFile(capture), capture);
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

    private void submitPhoto(Uri source, @Nullable File cleanup) {
        store.submitPhoto(beach.getId(), source, new PhotoImporter.Callback() {
            @Override
            public void onSaved(File saved) {
                if (cleanup != null) cleanup.delete();
                if (isFinishing() || isDestroyed()) return;
                bindPhoto();
            }

            @Override
            public void onFailed(Exception e) {
                if (cleanup != null) cleanup.delete();
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(RockRatingActivity.this,
                    R.string.photo_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (pendingCapture != null) {
            outState.putString(STATE_PENDING_CAPTURE, pendingCapture.getAbsolutePath());
        }
        if (slider != null) {
            outState.putInt(STATE_RATING, Math.round(slider.getValue()));
        }
    }
}
