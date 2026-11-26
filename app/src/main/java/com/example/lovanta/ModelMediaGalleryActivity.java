package com.example.lovanta;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class ModelMediaGalleryActivity extends AppCompatActivity {
    private ApiClient api;
    private String modelId;
    private String modelName;
    private String visibility;
    private boolean useWorkspaceEndpoint;
    private boolean canManageMedia;
    private LinearLayout content;
    private ProgressBar progress;
    private JSONArray assets = new JSONArray();
    private final ArrayList<String> legacyPublicUrls = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_model_media_gallery);
        ScreenInsets.applyToRoot(this, R.id.modelMediaGalleryRoot);

        modelId = getIntent().getStringExtra(ModelManagementActivity.EXTRA_MODEL_ID);
        modelName = getIntent().getStringExtra(ModelManagementActivity.EXTRA_MODEL_NAME);
        visibility = getIntent().getStringExtra(ModelManagementActivity.EXTRA_VISIBILITY);
        useWorkspaceEndpoint = getIntent().getBooleanExtra(
                ModelManagementActivity.EXTRA_USE_WORKSPACE_ENDPOINT, false);
        canManageMedia = getIntent().getBooleanExtra(
                ModelManagementActivity.EXTRA_CAN_MANAGE_MEDIA, false);
        ArrayList<String> legacyUrls = getIntent().getStringArrayListExtra(
                ModelManagementActivity.EXTRA_LEGACY_PUBLIC_URLS);
        if (legacyUrls != null) legacyPublicUrls.addAll(legacyUrls);

        content = findViewById(R.id.modelMediaGalleryContent);
        progress = findViewById(R.id.modelMediaGalleryProgress);
        TextView title = findViewById(R.id.tvModelMediaGalleryTitle);
        title.setText(("private".equals(visibility) ? "Private · PPV" : "Public · Free")
                + " · " + (modelName == null ? "Model" : modelName));
        ((ImageButton) findViewById(R.id.btnModelMediaGalleryBack)).setOnClickListener(v -> finish());

        if (modelId == null || !modelId.matches("[a-fA-F0-9]{24}")
                || !("public".equals(visibility) || "private".equals(visibility))) {
            Toast.makeText(this, "Gallery could not be opened.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        api = ApiClient.get(this);
        loadAssets();
    }

    private void loadAssets() {
        progress.setVisibility(View.VISIBLE);
        String path = useWorkspaceEndpoint
                ? "/api/v1/models/" + modelId + "/media"
                : "/api/v1/admin/media/models/" + modelId;
        api.get(path, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject result) {
                progress.setVisibility(View.GONE);
                assets = result.optJSONArray("assets");
                if (assets == null) assets = new JSONArray();
                renderGallery();
            }

            @Override
            public void onError(String message) {
                progress.setVisibility(View.GONE);
                if (!api.hasSession()) {
                    startActivity(new Intent(ModelMediaGalleryActivity.this, LoginActivity.class));
                    finish();
                    return;
                }
                Toast.makeText(ModelMediaGalleryActivity.this,
                        "Gallery could not be loaded: " + message,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void renderGallery() {
        content.removeAllViews();
        Set<String> shownPublicUrls = new HashSet<>();
        int imageCount = 0;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null
                    || (!useWorkspaceEndpoint && !"gallery".equals(asset.optString("purpose")))
                    || !visibility.equals(asset.optString("visibility"))) continue;
            String imageUrl = asset.optString("previewUrl", "");
            if (imageUrl.isEmpty()) continue;
            if ("public".equals(visibility)) shownPublicUrls.add(asset.optString("publicUrl", ""));
            addImageCard(imageUrl, asset.optString("id", ""), ++imageCount);
        }

        if ("public".equals(visibility)) {
            for (String legacyUrl : legacyPublicUrls) {
                if (legacyUrl.isEmpty() || shownPublicUrls.contains(legacyUrl)) continue;
                addImageCard(legacyUrl, "", ++imageCount);
            }
        }

        if (imageCount == 0) {
            TextView empty = new TextView(this);
            empty.setText("No " + visibility + " gallery images yet.");
            empty.setTextSize(15);
            empty.setTextColor(getResources().getColor(R.color.lovanta_gray));
            empty.setPadding(dp(16), dp(20), dp(16), dp(20));
            content.addView(empty);
            return;
        }

        TextView count = new TextView(this);
        count.setText(imageCount + (imageCount == 1 ? " image" : " images"));
        count.setTextSize(13);
        count.setTextColor(getResources().getColor(R.color.lovanta_gray));
        count.setPadding(dp(4), 0, dp(4), dp(8));
        content.addView(count, 0);
    }

    private void addImageCard(String imageUrl, String assetId, int index) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_skeumorphic_card);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = dp(12);
        card.setLayoutParams(cardParams);

        ImageView image = new ImageView(this);
        image.setImageResource(R.drawable.ic_person);
        image.setAdjustViewBounds(true);
        image.setMinimumHeight(dp(240));
        image.setMaxHeight(dp(420));
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackgroundColor(Color.BLACK);
        image.setContentDescription("Gallery image " + index + ". Tap to view full screen.");
        ModelImageLoader.load(image, imageUrl, R.drawable.ic_person);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setOnClickListener(v -> showFullScreenImage(imageUrl));
        card.addView(image, new LinearLayout.LayoutParams(-1, dp(280)));

        TextView label = new TextView(this);
        label.setText(("private".equals(visibility) ? "PPV image " : "Free image ") + index);
        label.setTextSize(14);
        label.setTextColor(getResources().getColor(R.color.lovanta_dark));
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setPadding(dp(4), dp(8), dp(4), dp(4));
        card.addView(label);

        if (canManageMedia && (!assetId.isEmpty() || "public".equals(visibility))) {
            Button remove = new Button(this);
            remove.setText("Remove image");
            remove.setAllCaps(false);
            remove.setOnClickListener(v -> {
                if (!assetId.isEmpty()) confirmRemoveAsset(assetId);
                else confirmRemoveLegacyUrl(imageUrl);
            });
            card.addView(remove, new LinearLayout.LayoutParams(-1, dp(44)));
        }
        content.addView(card);
    }

    private void showFullScreenImage(String imageUrl) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        ImageView image = new ImageView(this);
        image.setBackgroundColor(Color.BLACK);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription("Full-screen image. Press Back to close.");
        dialog.setContentView(image);
        dialog.setCancelable(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
        }
        ModelImageLoader.load(image, imageUrl, R.drawable.ic_person);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
    }

    private void confirmRemoveAsset(String assetId) {
        new AlertDialog.Builder(this)
                .setTitle("Delete image?")
                .setMessage("This permanently deletes the image from its R2 bucket.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) ->
                        api.delete("/api/v1/admin/media/" + assetId, new ApiClient.Callback() {
                            @Override
                            public void onSuccess(JSONObject result) {
                                Toast.makeText(ModelMediaGalleryActivity.this,
                                        "Image deleted.", Toast.LENGTH_SHORT).show();
                                loadAssets();
                            }

                            @Override
                            public void onError(String message) {
                                Toast.makeText(ModelMediaGalleryActivity.this,
                                        "Image could not be deleted: " + message,
                                        Toast.LENGTH_LONG).show();
                            }
                        }))
                .show();
    }

    private void confirmRemoveLegacyUrl(String url) {
        new AlertDialog.Builder(this)
                .setTitle("Remove image?")
                .setMessage("This removes the image URL from the model's public gallery.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (dialog, which) -> removeLegacyUrl(url))
                .show();
    }

    private void removeLegacyUrl(String url) {
        ArrayList<String> updatedUrls = new ArrayList<>(legacyPublicUrls);
        updatedUrls.remove(url);
        JSONArray jsonUrls = new JSONArray();
        for (String legacyUrl : updatedUrls) jsonUrls.put(legacyUrl);

        JSONObject body = new JSONObject();
        try {
            body.put("galleryUrls", jsonUrls);
        } catch (JSONException error) {
            Toast.makeText(this, "Could not prepare gallery update.", Toast.LENGTH_SHORT).show();
            return;
        }
        api.patch("/api/v1/admin/models/" + modelId, body, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject result) {
                legacyPublicUrls.clear();
                legacyPublicUrls.addAll(updatedUrls);
                renderGallery();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ModelMediaGalleryActivity.this,
                        "Image could not be removed: " + message,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
