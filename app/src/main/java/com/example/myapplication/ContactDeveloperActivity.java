package com.example.myapplication;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.myapplication.util.EdgeToEdge;
import com.google.android.material.textfield.TextInputEditText;

/**
 * Screen allowing users to contact the developers of the app.
 * Pre-fills topic and email details, launching the device's mail client.
 */
public class ContactDeveloperActivity extends AppCompatActivity {

    private TextInputEditText subjectInput;
    private TextInputEditText messageInput;

    private TextView topicGeneralView;
    private TextView topicBugView;
    private TextView topicFeatureView;
    private TextView topicDataView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_developer);

        EdgeToEdge.applyTopInset(findViewById(R.id.contactHeader));
        EdgeToEdge.applyBottomInset(findViewById(R.id.contactScrollView));

        findViewById(R.id.contactBackButton).setOnClickListener(
            v -> getOnBackPressedDispatcher().onBackPressed());

        subjectInput = findViewById(R.id.contactSubject);
        messageInput = findViewById(R.id.contactMessage);

        topicGeneralView = findViewById(R.id.topicGeneral);
        topicBugView = findViewById(R.id.topicBug);
        topicFeatureView = findViewById(R.id.topicFeature);
        topicDataView = findViewById(R.id.topicData);

        topicGeneralView.setOnClickListener(v -> selectTopic(0, getString(R.string.contact_topic_general)));
        topicBugView.setOnClickListener(v -> selectTopic(1, getString(R.string.contact_topic_bug)));
        topicFeatureView.setOnClickListener(v -> selectTopic(2, getString(R.string.contact_topic_feature)));
        topicDataView.setOnClickListener(v -> selectTopic(3, getString(R.string.contact_topic_data)));

        selectTopic(0, getString(R.string.contact_topic_general));

        findViewById(R.id.contactSendButton).setOnClickListener(v -> sendEmail());
    }

    private void selectTopic(int index, String topicTitle) {
        paintTopicCard(topicGeneralView, index == 0);
        paintTopicCard(topicBugView, index == 1);
        paintTopicCard(topicFeatureView, index == 2);
        paintTopicCard(topicDataView, index == 3);

        // The topic fills the subject in as a starting point; the field stays
        // open to type over, and a subject somebody has written themselves is
        // left alone rather than replaced by a topic name.
        if (subjectIsTopicOrEmpty()) {
            subjectInput.setText(topicTitle);
        }
    }

    private boolean subjectIsTopicOrEmpty() {
        String subject = subjectInput.getText() != null ? subjectInput.getText().toString().trim() : "";
        return subject.isEmpty()
            || subject.equals(getString(R.string.contact_topic_general))
            || subject.equals(getString(R.string.contact_topic_bug))
            || subject.equals(getString(R.string.contact_topic_feature))
            || subject.equals(getString(R.string.contact_topic_data));
    }

    private void paintTopicCard(TextView card, boolean selected) {
        Drawable background = card.getBackground();
        if (background instanceof GradientDrawable) {
            GradientDrawable shape = (GradientDrawable) background.mutate();
            shape.setColor(ContextCompat.getColor(this, selected ? R.color.accent_tint : R.color.surface));
            shape.setStroke(Math.round(getResources().getDisplayMetrics().density),
                ContextCompat.getColor(this, selected ? R.color.primary : R.color.divider));
        }
        card.setTextColor(ContextCompat.getColor(this, selected ? R.color.primary : R.color.text_primary));
    }

    private void sendEmail() {
        String recipient = getString(R.string.contact_email_address);

        String messageText = messageInput.getText() != null ? messageInput.getText().toString().trim() : "";
        if (messageText.isEmpty()) {
            Toast.makeText(this, R.string.contact_empty_message_error, Toast.LENGTH_SHORT).show();
            messageInput.requestFocus();
            return;
        }

        String rawSubject = subjectInput.getText() != null ? subjectInput.getText().toString().trim() : "";
        String finalSubject = rawSubject.isEmpty() ? getString(R.string.contact_developers_title) : rawSubject;

        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
        emailIntent.setData(Uri.parse("mailto:" + recipient));
        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{recipient});
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, finalSubject);
        emailIntent.putExtra(Intent.EXTRA_TEXT, messageText);

        // The chooser itself always resolves, so it would open empty ("No apps
        // can perform this action") rather than throw. Ask first whether
        // anything handles mailto: — which the manifest's <queries> entry lets
        // us see — and give the address instead when nothing does.
        if (emailIntent.resolveActivity(getPackageManager()) == null) {
            showNoEmailApp();
            return;
        }
        try {
            startActivity(Intent.createChooser(emailIntent, getString(R.string.contact_chooser_title)));
        } catch (ActivityNotFoundException e) {
            showNoEmailApp();
        }
    }

    private void showNoEmailApp() {
        Toast.makeText(this, R.string.contact_no_email_app, Toast.LENGTH_LONG).show();
    }
}
