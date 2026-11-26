package com.example.lovanta;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

public class ModelProfileActivity extends AppCompatActivity {

    public static final String EXTRA_NAME = "model_name";
    public static final String EXTRA_AVATAR = "model_avatar";
    public static final String EXTRA_AVATAR_URL = "model_avatar_url";
    public static final String EXTRA_BIO = "model_bio";
    public static final String EXTRA_MODEL_ID = "model_id";
    public static final String EXTRA_SUBSCRIBERS = "model_subscribers";
    public static final String EXTRA_PPV_UNLOCKS = "model_ppv_unlocks";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_model_profile);
        ScreenInsets.applyToMainRoot(this);

        ImageButton btnBack = findViewById(R.id.btnBack);
        ImageView ivAvatar = findViewById(R.id.ivModelAvatar);
        LinearLayout mediaSection = findViewById(R.id.layoutModelMediaSection);
        LinearLayout mediaContent = findViewById(R.id.layoutModelMediaContent);
        TextView tvName = findViewById(R.id.tvModelName);
        TextView tvBio = findViewById(R.id.tvModelBio);
        TextView tvSubscribers = findViewById(R.id.tvModelSubscribers);
        TextView tvUnlocks = findViewById(R.id.tvModelUnlocks);

        btnBack.setOnClickListener(v -> finish());
        tvName.setText(getIntent().getStringExtra(EXTRA_NAME));
        String bio = getIntent().getStringExtra(EXTRA_BIO);
        tvBio.setText(bio == null || bio.trim().isEmpty() ? "No description provided." : bio);
        ModelImageLoader.load(
                ivAvatar,
                getIntent().getStringExtra(EXTRA_AVATAR_URL),
                getIntent().getIntExtra(EXTRA_AVATAR, R.drawable.ic_person));
        tvName.setContentDescription("Model " + tvName.getText());
        ivAvatar.setContentDescription("Profile for " + tvName.getText());
        findViewById(R.id.layoutModelStats).setVisibility(View.GONE);

        String modelId = getIntent().getStringExtra(EXTRA_MODEL_ID);
        if (modelId == null || !modelId.matches("[a-fA-F0-9]{24}")) {
            mediaSection.setVisibility(View.GONE);
        } else {
            loadModelMedia(modelId, mediaContent);
        }
    }

    private void loadModelMedia(String modelId, LinearLayout mediaContent) {
        mediaContent.addView(mediaText("Loading model images…", false));
        ApiClient.get(this).get("/api/v1/models/" + modelId + "/media", new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject result) {
                mediaContent.removeAllViews();
                JSONArray assets = result.optJSONArray("assets");
                if (assets == null) assets = new JSONArray();
                String modelName = getIntent().getStringExtra(EXTRA_NAME);
                addMediaGroup(mediaContent, assets, modelId, modelName, "public", "PUBLIC · FREE");
                addMediaGroup(mediaContent, assets, modelId, modelName, "private", "PRIVATE · PPV");
            }

            @Override
            public void onError(String message) {
                mediaContent.removeAllViews();
                mediaContent.addView(mediaText("Model images could not be loaded.", false));
                Toast.makeText(ModelProfileActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void addMediaGroup(
            LinearLayout container,
            JSONArray assets,
            String modelId,
            String modelName,
            String visibility,
            String title) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        int count = 0;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null || !visibility.equals(asset.optString("visibility"))) continue;
            if (!asset.optString("previewUrl", "").isEmpty()) count++;
        }

        section.addView(mediaText(title, true));
        section.addView(mediaText(
                count + (count == 1 ? " image" : " images")
                        + ("public".equals(visibility) ? " · Free to view" : " · Private PPV media"),
                false));
        Button openGallery = new Button(this);
        openGallery.setText("Open " + ("public".equals(visibility) ? "public" : "private") + " gallery");
        openGallery.setAllCaps(false);
        openGallery.setOnClickListener(v -> {
            Intent intent = new Intent(this, ModelMediaGalleryActivity.class);
            intent.putExtra(ModelManagementActivity.EXTRA_MODEL_ID, modelId);
            intent.putExtra(ModelManagementActivity.EXTRA_MODEL_NAME, modelName);
            intent.putExtra(ModelManagementActivity.EXTRA_VISIBILITY, visibility);
            intent.putExtra(ModelManagementActivity.EXTRA_USE_WORKSPACE_ENDPOINT, true);
            startActivity(intent);
        });
        section.addView(openGallery, new LinearLayout.LayoutParams(-1, dp(46)));
        container.addView(section);
    }

    private TextView mediaText(String value, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(12);
        text.setTextColor(getResources().getColor(
                bold ? R.color.lovanta_pink : R.color.lovanta_gray));
        if (bold) text.setTypeface(null, android.graphics.Typeface.BOLD);
        text.setPadding(dp(4), dp(6), dp(4), dp(6));
        return text;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
