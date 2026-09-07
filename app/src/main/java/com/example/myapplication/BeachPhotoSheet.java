package com.example.myapplication;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

/**
 * The sheet behind the camera button on a beach's photo.
 *
 * <p>It only collects the choice. Taking and picking both go out to another
 * app and come back through an activity result, and those launchers have to be
 * registered before the activity is started, so the work itself stays in
 * {@link BeachDetailActivity} and this hands the decision back to it.
 */
public final class BeachPhotoSheet {

    public interface Choice {
        void onTakePhoto();
        void onChoosePhoto();
        void onRemovePhoto();
    }

    private BeachPhotoSheet() {
    }

    /**
     * @param beachName titles the sheet, so it is obvious which beach the photo
     *                  is about to become — the sheet covers the picture it
     *                  would replace.
     * @param hasPhoto  whether this beach already has a submitted photo; the
     *                  remove row appears only then, since there is nothing to
     *                  take back off a beach showing its default.
     */
    public static void show(Context context, String beachName, boolean hasPhoto, Choice choice) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        View sheet = LayoutInflater.from(context).inflate(R.layout.sheet_beach_photo, null);
        dialog.setContentView(sheet);

        ((TextView) sheet.findViewById(R.id.photoSheetTitle)).setText(beachName);

        sheet.findViewById(R.id.photoTake).setOnClickListener(v -> {
            dialog.dismiss();
            choice.onTakePhoto();
        });

        sheet.findViewById(R.id.photoChoose).setOnClickListener(v -> {
            dialog.dismiss();
            choice.onChoosePhoto();
        });

        View remove = sheet.findViewById(R.id.photoRemove);
        remove.setVisibility(hasPhoto ? View.VISIBLE : View.GONE);
        remove.setOnClickListener(v -> {
            dialog.dismiss();
            choice.onRemovePhoto();
        });

        dialog.show();
    }
}
