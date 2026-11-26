package com.example.lovanta;

import android.app.Dialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
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
    private final ArrayList<GalleryImage> galleryImages = new ArrayList<>();

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
        galleryImages.clear();
        Set<String> shownPublicUrls = new HashSet<>();
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null
                    || (!useWorkspaceEndpoint && !"gallery".equals(asset.optString("purpose")))
                    || !visibility.equals(asset.optString("visibility"))) continue;
            String imageUrl = asset.optString("previewUrl", "");
            if (imageUrl.isEmpty()) continue;
            if ("public".equals(visibility)) shownPublicUrls.add(asset.optString("publicUrl", ""));
            galleryImages.add(new GalleryImage(imageUrl, asset.optString("id", "")));
        }

        if ("public".equals(visibility)) {
            for (String legacyUrl : legacyPublicUrls) {
                if (legacyUrl.isEmpty() || shownPublicUrls.contains(legacyUrl)) continue;
                galleryImages.add(new GalleryImage(legacyUrl, ""));
            }
        }

        TextView count = new TextView(this);
        count.setText(galleryImages.size() + (galleryImages.size() == 1 ? " photo" : " photos")
                + "  ·  Tap a photo to view it");
        count.setTextSize(13);
        count.setTextColor(getResources().getColor(R.color.lovanta_gray));
        count.setPadding(dp(4), dp(2), dp(4), dp(10));
        content.addView(count);

        if (galleryImages.isEmpty()) {
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
        for (int i = 0; i < galleryImages.size(); i++) {
            addImageTile(grid, galleryImages.get(i), i, tileSize);
        }
        content.addView(grid, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addImageTile(GridLayout grid, GalleryImage galleryImage, int index, int tileSize) {
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
        tile.setOnClickListener(v -> showFullScreenImage(index));

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

    private GradientDrawable createViewerPillBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(0x99000000);
        background.setCornerRadius(dp(20));
        return background;
    }

    private void showFullScreenImage(int initialIndex) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout viewer = new FrameLayout(this);
        viewer.setBackgroundColor(Color.BLACK);
        ImageView firstImage = new ImageView(this);
        ImageView secondImage = new ImageView(this);
        firstImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        secondImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        viewer.addView(firstImage, new FrameLayout.LayoutParams(-1, -1));
        viewer.addView(secondImage, new FrameLayout.LayoutParams(-1, -1));
        secondImage.setVisibility(View.GONE);

        View touchLayer = new View(this);
        touchLayer.setBackgroundColor(Color.TRANSPARENT);
        touchLayer.setClickable(true);
        viewer.addView(touchLayer, new FrameLayout.LayoutParams(-1, -1));

        TextView position = new TextView(this);
        position.setTextColor(Color.WHITE);
        position.setTextSize(14);
        position.setGravity(Gravity.CENTER);
        position.setBackground(createViewerPillBackground());
        FrameLayout.LayoutParams positionParams =
                new FrameLayout.LayoutParams(dp(64), dp(36), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        positionParams.topMargin = dp(20);
        viewer.addView(position, positionParams);

        TextView swipeHint = new TextView(this);
        swipeHint.setText("Swipe to browse  ·  Tap to close");
        swipeHint.setTextColor(Color.WHITE);
        swipeHint.setTextSize(13);
        swipeHint.setGravity(Gravity.CENTER);
        swipeHint.setBackground(createViewerPillBackground());
        FrameLayout.LayoutParams hintParams =
                new FrameLayout.LayoutParams(-2, dp(40), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hintParams.bottomMargin = dp(28);
        viewer.addView(swipeHint, hintParams);
        dialog.setContentView(viewer);
        dialog.setCancelable(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
        }
        int[] currentIndex = {Math.max(0, Math.min(initialIndex, galleryImages.size() - 1))};
        ImageView[] activeImage = {firstImage};
        ImageView[] incomingImage = {secondImage};
        boolean[] transitioning = {false};
        showViewerImage(activeImage[0], position, currentIndex[0]);
        float[] touchStart = new float[2];
        touchLayer.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                touchStart[0] = event.getX();
                touchStart[1] = event.getY();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float deltaX = event.getX() - touchStart[0];
                float deltaY = event.getY() - touchStart[1];
                if (Math.abs(deltaX) > dp(48) && Math.abs(deltaX) > Math.abs(deltaY)) {
                    if (transitioning[0] || galleryImages.size() < 2) return true;
                    int next = currentIndex[0] + (deltaX < 0 ? 1 : -1);
                    if (next < 0) next = galleryImages.size() - 1;
                    if (next >= galleryImages.size()) next = 0;
                    int slideDirection = deltaX < 0 ? 1 : -1;
                    animateViewerImage(
                            viewer,
                            activeImage,
                            incomingImage,
                            transitioning,
                            position,
                            currentIndex,
                            next,
                            slideDirection);
                } else if (Math.abs(deltaX) < dp(16) && Math.abs(deltaY) < dp(16)) {
                    dialog.dismiss();
                }
                return true;
            }
            return event.getAction() == MotionEvent.ACTION_MOVE
                    || event.getAction() == MotionEvent.ACTION_CANCEL;
        });
    }

    private void animateViewerImage(
            FrameLayout viewer,
            ImageView[] activeImage,
            ImageView[] incomingImage,
            boolean[] transitioning,
            TextView position,
            int[] currentIndex,
            int nextIndex,
            int direction) {
        transitioning[0] = true;
        ImageView outgoing = activeImage[0];
        ImageView incoming = incomingImage[0];
        incoming.setVisibility(View.VISIBLE);
        incoming.setTranslationX(direction * Math.max(viewer.getWidth(), getResources()
                .getDisplayMetrics().widthPixels));
        incoming.setAlpha(0f);

        GalleryImage selected = galleryImages.get(nextIndex);
        ModelImageLoader.load(incoming, selected.url, R.drawable.ic_person, loaded -> {
            incoming.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int slideDistance = Math.max(viewer.getWidth(), getResources()
                    .getDisplayMetrics().widthPixels);
            long duration = 240;
            incoming.animate()
                    .translationX(0)
                    .alpha(1f)
                    .setDuration(duration)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .withEndAction(() -> {
                        outgoing.setVisibility(View.GONE);
                        outgoing.setTranslationX(0);
                        outgoing.setAlpha(1f);
                        activeImage[0] = incoming;
                        incomingImage[0] = outgoing;
                        currentIndex[0] = nextIndex;
                        position.setText((nextIndex + 1) + " / " + galleryImages.size());
                        transitioning[0] = false;
                    })
                    .start();
            outgoing.animate()
                    .translationX(-direction * slideDistance)
                    .alpha(0f)
                    .setDuration(duration)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        });
    }

    private void showViewerImage(ImageView image, TextView position, int index) {
        GalleryImage selected = galleryImages.get(index);
        image.setContentDescription("Photo " + (index + 1) + " of " + galleryImages.size()
                + ". Swipe left or right to browse.");
        position.setText((index + 1) + " / " + galleryImages.size());
        ModelImageLoader.load(image, selected.url, R.drawable.ic_person);
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

    private static final class GalleryImage {
        private final String url;
        private final String assetId;

        private GalleryImage(String url, String assetId) {
            this.url = url;
            this.assetId = assetId;
        }
    }
}
