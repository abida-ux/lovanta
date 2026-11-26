package com.example.lovanta;

import android.app.Dialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.PopupMenu;
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
        ArrayList<GalleryImage> images = new ArrayList<>();
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null
                    || (!useWorkspaceEndpoint && !"gallery".equals(asset.optString("purpose")))
                    || !visibility.equals(asset.optString("visibility"))) continue;
            String imageUrl = asset.optString("previewUrl", "");
            if (imageUrl.isEmpty()) continue;
            if ("public".equals(visibility)) shownPublicUrls.add(asset.optString("publicUrl", ""));
            images.add(new GalleryImage(imageUrl, asset.optString("id", "")));
        }

        if ("public".equals(visibility)) {
            for (String legacyUrl : legacyPublicUrls) {
                if (legacyUrl.isEmpty() || shownPublicUrls.contains(legacyUrl)) continue;
                images.add(new GalleryImage(legacyUrl, ""));
            }
        }

        TextView count = new TextView(this);
        count.setText(images.size() + (images.size() == 1 ? " photo" : " photos")
                + "  ·  Tap a photo to view it");
        count.setTextSize(13);
        count.setTextColor(getResources().getColor(R.color.lovanta_gray));
        count.setPadding(dp(4), dp(2), dp(4), dp(10));
        content.addView(count);

        if (images.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Your " + visibility + " gallery is empty. Uploaded photos will appear here.");
            empty.setTextSize(15);
            empty.setTextColor(getResources().getColor(R.color.lovanta_gray));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(36), dp(20), dp(36));
            content.addView(empty);
            return;
        }

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setUseDefaultMargins(false);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int tileSize = (screenWidth - dp(20)) / 3;
        for (int i = 0; i < images.size(); i++) {
            addImageTile(grid, images.get(i), tileSize);
        }
        content.addView(grid, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addImageTile(GridLayout grid, GalleryImage galleryImage, int tileSize) {
        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(createTileBackground());
        tile.setClipToOutline(true);
        tile.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(12));
            }
        });

        ImageView image = new ImageView(this);
        image.setImageResource(R.drawable.ic_person);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ModelImageLoader.load(image, galleryImage.url, R.drawable.ic_person);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setContentDescription("Open gallery photo full screen");
        tile.addView(image, new FrameLayout.LayoutParams(-1, -1));
        tile.setOnClickListener(v -> showFullScreenImage(galleryImage.url));

        if (canManageMedia && (!galleryImage.assetId.isEmpty() || "public".equals(visibility))) {
            ImageButton more = new ImageButton(this);
            more.setImageResource(R.drawable.ic_more_vert);
            more.setImageTintList(ColorStateList.valueOf(getResources().getColor(R.color.lovanta_dark)));
            more.setBackground(createActionBackground());
            more.setContentDescription("Photo options");
            FrameLayout.LayoutParams moreParams =
                    new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.TOP | Gravity.END);
            moreParams.setMargins(0, dp(6), dp(6), 0);
            tile.addView(more, moreParams);
            more.setOnClickListener(v -> showPhotoMenu(more, galleryImage));
        }

        GridLayout.LayoutParams tileParams = new GridLayout.LayoutParams();
        tileParams.width = tileSize;
        tileParams.height = tileSize;
        tileParams.setMargins(dp(2), dp(2), dp(2), dp(2));
        grid.addView(tile, tileParams);
    }

    private void showPhotoMenu(View anchor, GalleryImage image) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Delete photo");
        menu.setOnMenuItemClickListener(item -> {
            if (!image.assetId.isEmpty()) confirmRemoveAsset(image.assetId);
            else confirmRemoveLegacyUrl(image.url);
            return true;
        });
        menu.show();
    }

    private GradientDrawable createTileBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(getResources().getColor(R.color.lovanta_pink_light));
        background.setCornerRadius(dp(12));
        return background;
    }

    private GradientDrawable createActionBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setShape(GradientDrawable.OVAL);
        return background;
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
        image.setOnClickListener(v -> dialog.dismiss());
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

    private static final class GalleryImage {
        private final String url;
        private final String assetId;

        private GalleryImage(String url, String assetId) {
            this.url = url;
            this.assetId = assetId;
        }
    }
}
