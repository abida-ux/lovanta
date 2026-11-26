package com.example.lovanta;

import android.content.Intent;
import android.graphics.Outline;
import android.os.Bundle;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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

        btnBack.setOnClickListener(v -> finish());
        tvName.setText(getIntent().getStringExtra(EXTRA_NAME));
        String bio = getIntent().getStringExtra(EXTRA_BIO);
        tvBio.setText(bio == null || bio.trim().isEmpty()
                ? "No description provided."
                : bio);

        ivAvatar.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        ivAvatar.setClipToOutline(true);
        ModelImageLoader.load(
                ivAvatar,
                getIntent().getStringExtra(EXTRA_AVATAR_URL),
                getIntent().getIntExtra(EXTRA_AVATAR, R.drawable.ic_person));
        tvName.setContentDescription("Model " + tvName.getText());
        ivAvatar.setContentDescription("Profile for " + tvName.getText());

        String modelId = getIntent().getStringExtra(EXTRA_MODEL_ID);
        if (modelId == null || !modelId.matches("[a-fA-F0-9]{24}")) {
            mediaSection.setVisibility(View.GONE);
        } else {
            loadModelMedia(modelId, mediaContent);
        }
    }

    private void loadModelMedia(String modelId, LinearLayout mediaContent) {
        mediaContent.setOrientation(LinearLayout.VERTICAL);
        mediaContent.addView(mediaText("Loading model galleries…", false));
        ApiClient.get(this).get("/api/v1/models/" + modelId + "/media", new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject result) {
                mediaContent.removeAllViews();
                mediaContent.setOrientation(LinearLayout.HORIZONTAL);
                JSONArray assets = result.optJSONArray("assets");
                if (assets == null) assets = new JSONArray();
                String modelName = getIntent().getStringExtra(EXTRA_NAME);
                addMediaCard(mediaContent, assets, modelId, modelName, "public");
                addMediaCard(mediaContent, assets, modelId, modelName, "private");
            }

            @Override
            public void onError(String message) {
                mediaContent.removeAllViews();
                mediaContent.setOrientation(LinearLayout.VERTICAL);
                mediaContent.addView(mediaText("Model galleries could not be loaded.", false));
                Toast.makeText(ModelProfileActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void addMediaCard(
            LinearLayout container,
            JSONArray assets,
            String modelId,
            String modelName,
            String visibility) {
        int count = 0;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset != null
                    && visibility.equals(asset.optString("visibility"))
                    && !asset.optString("previewUrl", "").isEmpty()) {
                count++;
            }
        }

        boolean isPublic = "public".equals(visibility);
        String title = isPublic ? "Public gallery" : "Private PPV";
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_model_media_access);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setElevation(dp(2));
        card.setClickable(true);
        card.setFocusable(true);

        TextView badge = mediaText(isPublic ? "FREE ACCESS" : "PRIVATE", true);
        badge.setBackgroundResource(R.drawable.bg_model_profile_badge);
        badge.setTextColor(getResources().getColor(R.color.lovanta_pink));
        badge.setPadding(dp(8), dp(4), dp(8), dp(4));
        card.addView(badge);

        TextView heading = mediaText(title, true);
        heading.setTextSize(15);
        heading.setTextColor(getResources().getColor(R.color.lovanta_dark));
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, -2);
        headingParams.topMargin = dp(8);
        card.addView(heading, headingParams);

        TextView photoCount = mediaText(
                count == 0 ? "No photos yet" : count + (count == 1 ? " photo" : " photos"),
                false);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(-1, -2);
        countParams.topMargin = dp(3);
        card.addView(photoCount, countParams);

        TextView browse = mediaText("Browse photos  ›", true);
        browse.setTextColor(getResources().getColor(R.color.lovanta_pink));
        LinearLayout.LayoutParams browseParams = new LinearLayout.LayoutParams(-1, -2);
        browseParams.topMargin = dp(14);
        card.addView(browse, browseParams);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(0, dp(170), 1);
        cardParams.setMargins(dp(4), 0, dp(4), dp(8));
        container.addView(card, cardParams);
        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, ModelMediaGalleryActivity.class);
            intent.putExtra(ModelManagementActivity.EXTRA_MODEL_ID, modelId);
            intent.putExtra(ModelManagementActivity.EXTRA_MODEL_NAME, modelName);
            intent.putExtra(ModelManagementActivity.EXTRA_VISIBILITY, visibility);
            intent.putExtra(ModelManagementActivity.EXTRA_USE_WORKSPACE_ENDPOINT, true);
            startActivity(intent);
        });
    }

    private TextView mediaText(String value, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(12);
        text.setTextColor(getResources().getColor(
                bold ? R.color.lovanta_pink : R.color.lovanta_gray));
        if (bold) text.setTypeface(null, android.graphics.Typeface.BOLD);
        return text;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
