package com.example.lovanta;

import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import java.text.NumberFormat;

public class ModelProfileActivity extends AppCompatActivity {

    public static final String EXTRA_NAME = "model_name";
    public static final String EXTRA_AVATAR = "model_avatar";
    public static final String EXTRA_BIO = "model_bio";
    public static final String EXTRA_SUBSCRIBERS = "model_subscribers";
    public static final String EXTRA_PPV_UNLOCKS = "model_ppv_unlocks";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_model_profile);
        ScreenInsets.applyToMainRoot(this);

        ImageButton btnBack = findViewById(R.id.btnBack);
        ImageView ivAvatar = findViewById(R.id.ivModelAvatar);
        TextView tvName = findViewById(R.id.tvModelName);
        TextView tvBio = findViewById(R.id.tvModelBio);
        TextView tvSubscribers = findViewById(R.id.tvModelSubscribers);
        TextView tvUnlocks = findViewById(R.id.tvModelUnlocks);

        btnBack.setOnClickListener(v -> finish());
        tvName.setText(getIntent().getStringExtra(EXTRA_NAME));
        String bio = getIntent().getStringExtra(EXTRA_BIO);
        tvBio.setText(bio == null || bio.trim().isEmpty() ? "No description provided." : bio);
        ivAvatar.setImageResource(getIntent().getIntExtra(EXTRA_AVATAR, R.drawable.ic_person));
        tvName.setContentDescription("Model " + tvName.getText());
        ivAvatar.setContentDescription("Profile for " + tvName.getText());
        findViewById(R.id.layoutModelStats).setVisibility(View.GONE);
    }
}
