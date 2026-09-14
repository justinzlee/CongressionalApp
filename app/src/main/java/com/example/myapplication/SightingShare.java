package com.example.myapplication;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.example.myapplication.model.Beach;
import com.example.myapplication.model.Sighting;
import com.example.myapplication.model.SightingSpecies;

/**
 * Passes a sighting to other people through whatever they already use to talk
 * to each other.
 *
 * <p>This is the honest answer to "warn others" in an app with no server behind
 * it. Nothing this app stores reaches another phone by itself — the beach list
 * is compiled in and the only network calls are read-only forecast APIs — so a
 * warning gets out the way a warning actually gets out on a beach: somebody
 * texts the group chat. The system share sheet reaches every messaging app on
 * the phone, and it reaches the one person who is walking towards the animal
 * faster than any push notification would.
 *
 * <p>Each message is written to stand on its own in a text thread, with the
 * beach named and the instructions spelled out — for a stranding, distance,
 * dogs and do not move it — because the person receiving it does not have
 * this app open and may not have it at all.
 */
public final class SightingShare {

    private SightingShare() {
    }

    /** Opens the share sheet with a message about this sighting. */
    public static void share(Context context, Beach beach, Sighting sighting) {
        String noun = context.getString(sighting.getSpecies().getNounRes());
        String beachName = beach.getName();

        String message;
        String subject;
        if (sighting.isStranded()) {
            message = context.getString(R.string.sighting_share_stranded, noun, beachName);
            subject = context.getString(R.string.sighting_share_subject, noun, beachName);
        } else if (sighting.getSpecies() == SightingSpecies.SHARK) {
            // Named in the subject line rather than left to the body. This one
            // is read over a shoulder or in a notification preview, and "shark"
            // is the whole message.
            message = context.getString(R.string.sighting_share_shark, beachName);
            subject = context.getString(R.string.sighting_share_subject_shark, beachName);
        } else {
            message = context.getString(R.string.sighting_share_water, noun, beachName);
            subject = context.getString(R.string.sighting_share_subject_seen, beachName);
        }

        String note = sighting.getNote();
        if (note != null) {
            message = message + "\n\n" + note;
        }

        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, message);
        // Filled in by mail apps, ignored by messaging ones.
        send.putExtra(Intent.EXTRA_SUBJECT, subject);

        try {
            context.startActivity(Intent.createChooser(
                send, context.getString(R.string.sighting_share_cta)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, R.string.sighting_share_none, Toast.LENGTH_LONG).show();
        }
    }
}
