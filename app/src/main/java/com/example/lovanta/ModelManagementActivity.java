package com.example.lovanta;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.canhub.cropper.CropImageContract;
import com.canhub.cropper.CropImageContractOptions;
import com.canhub.cropper.CropImageOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ModelManagementActivity extends AppCompatActivity {
    public static final String EXTRA_MODEL_ID = "model_id";
    private static final String TAG = "ModelManagement";
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final int REQUEST_PROFILE_IMAGE = 1;
    private static final int REQUEST_GALLERY_IMAGE = 2;

    private final ExecutorService imageExecutor = Executors.newSingleThreadExecutor();
    private ApiClient api;
    private String modelId;
    private LinearLayout content;
    private ProgressBar progress;
    private TextView title;
    private JSONObject model;
    private JSONArray assignments = new JSONArray();
    private JSONArray mediaAssets = new JSONArray();
    private ActivityResultLauncher<String> imagePicker;
    private ActivityResultLauncher<CropImageContractOptions> imageCropper;
    private int pendingImagePurpose = REQUEST_PROFILE_IMAGE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_model_management);
        imagePicker = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                this::onImageSelected);
        imageCropper = registerForActivityResult(
                new CropImageContract(),
                result -> {
                    if (result.isSuccessful()) {
                        Uri croppedUri = result.getUriContent();
                        if (croppedUri != null) uploadCroppedImage(croppedUri);
                        else Toast.makeText(this, "The cropped image could not be opened.", Toast.LENGTH_LONG).show();
                    } else if (result.getError() != null) {
                        Toast.makeText(this, "Could not crop image: "
                                + result.getError().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
        ScreenInsets.applyToRoot(this, R.id.modelManagementRoot);
        api = ApiClient.get(this);
        modelId = getIntent().getStringExtra(EXTRA_MODEL_ID);
        content = findViewById(R.id.modelManagementContent);
        progress = findViewById(R.id.modelManagementProgress);
        title = findViewById(R.id.tvModelManagementTitle);
        ((ImageButton) findViewById(R.id.btnModelManagementBack)).setOnClickListener(v -> finish());
        if (modelId == null || !modelId.matches("[a-fA-F0-9]{24}")) {
            Toast.makeText(this, "Model could not be opened.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        loadModel();
    }

    private void loadModel() {
        content.setVisibility(View.INVISIBLE);
        progress.setVisibility(View.VISIBLE);
        api.get("/api/v1/admin/models/" + modelId, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject result) {
                model = result.optJSONObject("model");
                assignments = result.optJSONArray("assignments");
                if (assignments == null) assignments = new JSONArray();
                if (model == null) {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(ModelManagementActivity.this,
                            "Unable to load model. Please try again.", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                api.get("/api/v1/admin/media/models/" + modelId, new ApiClient.Callback() {
                    @Override
                    public void onSuccess(JSONObject mediaResult) {
                        progress.setVisibility(View.GONE);
                        mediaAssets = mediaResult.optJSONArray("assets");
                        if (mediaAssets == null) mediaAssets = new JSONArray();
                        renderModel();
                    }

                    @Override
                    public void onError(String message) {
                        progress.setVisibility(View.GONE);
                        mediaAssets = new JSONArray();
                        renderModel();
                    }
                });
            }

            @Override
            public void onError(String message) {
                progress.setVisibility(View.GONE);
                Toast.makeText(ModelManagementActivity.this, message, Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void renderModel() {
        content.removeAllViews();
        content.setVisibility(View.VISIBLE);
        String displayName = model.optString("displayName", "Model");
        title.setText(displayName);
        addSectionTitle("MODEL PROFILE");

        LinearLayout profile = card();
        ImageView avatar = new ImageView(this);
        avatar.setImageResource(R.drawable.ic_person);
        avatar.setBackgroundResource(R.drawable.bg_model_avatar);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(96), dp(96));
        avatarParams.gravity = Gravity.CENTER_HORIZONTAL;
        profile.addView(avatar, avatarParams);
        String avatarUrl = model.optString("avatarUrl", "");
        if (!avatarUrl.isEmpty()) {
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            avatar.setOnClickListener(v -> showImagePreview(avatarUrl));
            loadAvatar(avatar, avatarUrl);
        } else {
            avatar.setPadding(dp(18), dp(18), dp(18), dp(18));
            avatar.setColorFilter(getResources().getColor(R.color.lovanta_pink));
        }

        TextView name = text(displayName, 20, R.color.lovanta_dark, true);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nameParams = wrapParams();
        nameParams.topMargin = dp(10);
        name.setLayoutParams(nameParams);
        profile.addView(name);
        String permanentId = model.optString("modelId", model.optString("_id", modelId));
        profile.addView(text("Model ID: " + permanentId, 12, R.color.lovanta_gray, false));
        String status = model.optString("status", model.optBoolean("active", true) ? "active" : "inactive");
        profile.addView(text("Status: " + status, 13, R.color.lovanta_pink, true));
        profile.addView(text("Created: " + model.optString("createdAt", "—"),
                12, R.color.lovanta_gray, false));
        profile.addView(text("Availability: " + model.optString("availability", "offline"),
                        12, R.color.lovanta_gray, false));
        profile.addView(text("Verified: " + model.optBoolean("verified", false)
                        + " · Age verified: " + model.optBoolean("ageVerified", false),
                12, R.color.lovanta_gray, false));
        String description = model.optString("description", "").trim();
        profile.addView(text(description.isEmpty() ? "No bio has been added." : description,
                14, R.color.lovanta_gray, false));
        if (canManage()) {
            LinearLayout profileActions = actionRow();
            Button edit = actionButton("Edit profile", R.color.lovanta_pink);
            edit.setOnClickListener(v -> editProfile());
            Button uploadImage = actionButton("Upload profile image", R.color.lovanta_pink);
            uploadImage.setOnClickListener(v -> {
                pendingImagePurpose = REQUEST_PROFILE_IMAGE;
                imagePicker.launch("image/*");
            });
            Button toggleStatus = actionButton(
                    model.optBoolean("active", true) ? "Deactivate" : "Activate",
                    R.color.lovanta_dark);
            toggleStatus.setOnClickListener(v -> confirmStatusChange());
            profileActions.addView(edit, weightedButton());
            profileActions.addView(uploadImage, weightedButton());
            profileActions.addView(toggleStatus, weightedButton());
            profile.addView(profileActions);
        }
        content.addView(profile);

        addSectionTitle("PUBLIC GALLERY");
        addPublicGallery(canManage());
        addSectionTitle("PRIVATE GALLERY");
        addText("Private uploads and protected delivery are unavailable until a media storage provider is configured.");

        addSectionTitle("MODEL CHATTERS");
        if (canManage() && model.optBoolean("active", true)) {
            Button addChatter = actionButton("Assign chatter", R.color.lovanta_pink);
            addChatter.setOnClickListener(v -> chooseChatter());
            content.addView(addChatter);
        }
        addAssignedChatters(canManage());
    }

    private void addPublicGallery(boolean canManage) {
        LinearLayout gallery = card();
        if (canManage) {
            Button addImage = actionButton("Upload gallery image", R.color.lovanta_pink);
            addImage.setOnClickListener(v -> {
                pendingImagePurpose = REQUEST_GALLERY_IMAGE;
                imagePicker.launch("image/*");
            });
            gallery.addView(addImage);
        }
        JSONArray galleryUrls = model.optJSONArray("galleryUrls");
        if (galleryUrls == null || galleryUrls.length() == 0) {
            gallery.addView(text("No public gallery items found.", 14, R.color.lovanta_gray, false));
        } else {
            for (int i = 0; i < galleryUrls.length(); i++) {
                final int index = i;
                String mediaUrl = galleryUrls.optString(i, "");
                if (mediaUrl.isEmpty()) continue;
                LinearLayout item = new LinearLayout(this);
                item.setGravity(Gravity.CENTER_VERTICAL);
                item.setOrientation(LinearLayout.HORIZONTAL);
                ImageView thumbnail = new ImageView(this);
                thumbnail.setImageResource(R.drawable.ic_person);
                thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
                thumbnail.setBackgroundResource(R.drawable.bg_model_avatar);
                LinearLayout.LayoutParams thumbnailParams =
                        new LinearLayout.LayoutParams(dp(88), dp(88));
                thumbnailParams.setMargins(0, dp(6), dp(12), dp(6));
                item.addView(thumbnail, thumbnailParams);
                loadAvatar(thumbnail, mediaUrl);
                thumbnail.setOnClickListener(v -> showImagePreview(mediaUrl));
                TextView label = text("Gallery image " + (index + 1), 14, R.color.lovanta_dark, true);
                item.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
                Button preview = actionButton("Preview", R.color.lovanta_dark);
                preview.setOnClickListener(v -> showImagePreview(mediaUrl));
                item.addView(preview, new LinearLayout.LayoutParams(dp(84), dp(40)));
                if (canManage) {
                    Button remove = actionButton("Remove", R.color.lovanta_dark);
                    remove.setOnClickListener(v -> confirmRemoveGalleryUrl(index));
                    item.addView(remove, new LinearLayout.LayoutParams(dp(84), dp(40)));
                }
                gallery.addView(item);
            }
        }
        gallery.addView(text(
                "Public model gallery images are stored in Cloudflare R2.",
                12, R.color.lovanta_gray, false));
        content.addView(gallery);
    }

    private void addAssignedChatters(boolean canManage) {
        if (assignments.length() == 0) {
            addText("No chatters are assigned to this model.");
            return;
        }
        for (int i = 0; i < assignments.length(); i++) {
            JSONObject assignment = assignments.optJSONObject(i);
            if (assignment == null) continue;
            JSONObject worker = assignment.optJSONObject("workerId");
            if (worker == null) continue;
            LinearLayout assigned = card();
            assigned.addView(text(worker.optString("displayName", "Chatter"),
                    16, R.color.lovanta_dark, true));
            assigned.addView(text(worker.optString("email", ""),
                    12, R.color.lovanta_gray, false));
            assigned.addView(text("Assigned: " + assignment.optString("assignedAt", "—"),
                    12, R.color.lovanta_gray, false));
            if (canManage) {
                Button remove = actionButton("Remove assignment", R.color.lovanta_dark);
                String assignmentId = assignment.optString("_id", "");
                remove.setOnClickListener(v -> confirmRemoveAssignment(assignmentId));
                assigned.addView(remove);
            }
            content.addView(assigned);
        }
    }

    private void editProfile() {
        LinearLayout form = formLayout();
        EditText name = field("Full name");
        name.setText(model.optString("displayName", ""));
        EditText username = field("Username");
        username.setText(model.optString("username", ""));
        EditText avatar = field("Profile picture URL");
        avatar.setText(model.optString("avatarUrl", ""));
        EditText bio = field("Bio");
        bio.setSingleLine(false);
        bio.setMinLines(2);
        bio.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        bio.setText(model.optString("description", ""));
        form.addView(name);
        form.addView(username);
        form.addView(avatar);
        form.addView(bio);
        new AlertDialog.Builder(this).setTitle("Edit model profile").setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    String updatedName = name.getText().toString().trim();
                    if (updatedName.isEmpty()) {
                        Toast.makeText(this, "Model name is required.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    JSONObject body = new JSONObject();
                    try {
                        body.put("displayName", updatedName);
                        body.put("avatarUrl", avatar.getText().toString().trim());
                        body.put("description", bio.getText().toString().trim());
                        if (!username.getText().toString().trim().isEmpty()) {
                            body.put("username", username.getText().toString().trim());
                        }
                    } catch (JSONException error) {
                        Toast.makeText(this, "Could not save model profile.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    api.patch("/api/v1/admin/models/" + modelId, body, callback(result -> loadModel()));
                }).show();
    }

    private void confirmStatusChange() {
        boolean active = model.optBoolean("active", true);
        String action = active ? "deactivate" : "activate";
        new AlertDialog.Builder(this)
                .setTitle((active ? "Deactivate" : "Activate") + " model?")
                .setMessage("This changes the model's availability to assigned chatters.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(active ? "Deactivate" : "Activate", (dialog, which) -> {
                    JSONObject body = new JSONObject();
                    try {
                        body.put("status", active ? "inactive" : "active");
                    } catch (JSONException error) {
                        Toast.makeText(this, "Could not update model status.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    api.patch("/api/v1/admin/models/" + modelId, body, callback(result -> loadModel()));
                }).show();
    }

    private void confirmRemoveGalleryUrl(int index) {
        new AlertDialog.Builder(this)
                .setTitle("Remove public media?")
                .setMessage("This removes this URL from the model's public gallery.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (dialog, which) -> {
                    JSONArray oldUrls = model.optJSONArray("galleryUrls");
                    String removeUrl = oldUrls == null ? "" : oldUrls.optString(index, "");
                    String assetId = findMediaAssetId(removeUrl, "gallery");
                    if (!assetId.isEmpty()) {
                        api.delete("/api/v1/admin/media/" + assetId,
                                callback(result -> loadModel()));
                        return;
                    }
                    JSONArray updatedUrls = new JSONArray();
                    if (oldUrls != null) {
                        for (int i = 0; i < oldUrls.length(); i++) {
                            if (i != index) updatedUrls.put(oldUrls.opt(i));
                        }
                    }
                    JSONObject body = new JSONObject();
                    try {
                        body.put("galleryUrls", updatedUrls);
                    } catch (JSONException error) {
                        Toast.makeText(this, "Could not update public gallery.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    api.patch("/api/v1/admin/models/" + modelId, body, callback(result -> loadModel()));
                }).show();
    }

    private String findMediaAssetId(String publicUrl, String purpose) {
        for (int i = 0; i < mediaAssets.length(); i++) {
            JSONObject asset = mediaAssets.optJSONObject(i);
            if (asset == null) continue;
            if (purpose.equals(asset.optString("purpose"))
                    && publicUrl.equals(asset.optString("publicUrl"))) {
                return asset.optString("id", "");
            }
        }
        return "";
    }

    private void onImageSelected(Uri uri) {
        if (uri == null) return;
        String contentType = getContentResolver().getType(uri);
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            Toast.makeText(this, "Choose a valid image file.", Toast.LENGTH_LONG).show();
            return;
        }

        CropImageOptions options = new CropImageOptions();
        options.imageSourceIncludeCamera = false;
        options.fixAspectRatio = pendingImagePurpose == REQUEST_PROFILE_IMAGE;
        options.aspectRatioX = 1;
        options.aspectRatioY = 1;
        options.outputCompressFormat = Bitmap.CompressFormat.JPEG;
        options.outputCompressQuality = 90;
        options.activityTitle = pendingImagePurpose == REQUEST_PROFILE_IMAGE
                ? "Crop profile image"
                : "Crop gallery image";
        imageCropper.launch(new CropImageContractOptions(uri, options));
    }

    private void uploadCroppedImage(Uri uri) {
        progress.setVisibility(View.VISIBLE);
        imageExecutor.execute(() -> {
            try {
                byte[] bytes;
                try (InputStream input = getContentResolver().openInputStream(uri);
                     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    if (input == null) throw new IOException("Selected image could not be opened.");
                    byte[] buffer = new byte[8192];
                    int total = 0;
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        total += read;
                        if (total > MAX_IMAGE_BYTES) {
                            throw new IOException("The image must be 10 MB or smaller.");
                        }
                        output.write(buffer, 0, read);
                    }
                    bytes = output.toByteArray();
                }
                if (bytes.length == 0) throw new IOException("The selected image is empty.");

                JSONObject body = new JSONObject();
                String purpose = pendingImagePurpose == REQUEST_PROFILE_IMAGE ? "avatar" : "gallery";
                body.put("modelId", modelId);
                body.put("purpose", purpose);
                body.put("contentType", "image/jpeg");
                body.put("sizeBytes", bytes.length);

                runOnUiThread(() -> api.post(
                        "/api/v1/admin/media/upload-intent",
                        body,
                        new ApiClient.Callback() {
                            @Override
                            public void onSuccess(JSONObject intent) {
                                String uploadUrl = intent.optString("uploadUrl", "");
                                String assetId = intent.optString("assetId", "");
                                if (uploadUrl.isEmpty() || assetId.isEmpty()) {
                                    progress.setVisibility(View.GONE);
                                    Toast.makeText(ModelManagementActivity.this,
                                            "The upload could not be prepared.", Toast.LENGTH_LONG).show();
                                    return;
                                }
                                uploadToR2(uploadUrl, assetId, "image/jpeg", bytes);
                            }

                            @Override
                            public void onError(String message) {
                                progress.setVisibility(View.GONE);
                                Toast.makeText(ModelManagementActivity.this,
                                        message, Toast.LENGTH_LONG).show();
                            }
                        }));
            } catch (IOException | JSONException error) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(ModelManagementActivity.this,
                            error.getMessage() == null
                                    ? "The selected image could not be uploaded."
                                    : error.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void uploadToR2(String uploadUrl, String assetId, String contentType, byte[] bytes) {
        imageExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(uploadUrl).openConnection();
                connection.setRequestMethod("PUT");
                connection.setDoOutput(true);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(60000);
                connection.setFixedLengthStreamingMode(bytes.length);
                connection.setRequestProperty("Content-Type", contentType);
                try (java.io.OutputStream output = connection.getOutputStream()) {
                    output.write(bytes);
                }
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) {
                    throw new IOException("R2 upload returned HTTP " + status);
                }

                JSONObject completeBody = new JSONObject();
                completeBody.put("assetId", assetId);
                runOnUiThread(() -> api.post(
                        "/api/v1/admin/media/" + assetId + "/complete",
                        completeBody,
                        callback(result -> {
                            Toast.makeText(this, "Image uploaded.", Toast.LENGTH_LONG).show();
                            loadModel();
                        })));
            } catch (IOException | JSONException error) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(this,
                            "Image upload failed: " + error.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void showImagePreview(String imageUrl) {
        ImageView preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setMinimumHeight(dp(240));
        preview.setBackgroundColor(getResources().getColor(R.color.white));
        preview.setImageResource(R.drawable.ic_person);

        new AlertDialog.Builder(this)
                .setTitle("Image preview")
                .setView(preview)
                .setPositiveButton("Done", null)
                .show();
        loadAvatar(preview, imageUrl);
    }

    private void chooseChatter() {
        api.get("/api/v1/admin/chatters?page=1&limit=100", callback(result -> {
            JSONArray chatters = result.optJSONArray("items");
            if (chatters == null) chatters = new JSONArray();
            Set<String> assignedIds = new HashSet<>();
            for (int i = 0; i < assignments.length(); i++) {
                JSONObject assignment = assignments.optJSONObject(i);
                JSONObject worker = assignment == null ? null : assignment.optJSONObject("workerId");
                if (worker != null) assignedIds.add(worker.optString("_id", ""));
            }
            List<JSONObject> eligible = new ArrayList<>();
            for (int i = 0; i < chatters.length(); i++) {
                JSONObject worker = chatters.optJSONObject(i);
                if (worker == null || !worker.optBoolean("active", false)) continue;
                String role = worker.optString("role", "");
                String workerId = worker.optString("_id", "");
                if (("CHATTER".equals(role) || "worker".equals(role))
                        && !workerId.isEmpty() && !assignedIds.contains(workerId)) {
                    eligible.add(worker);
                }
            }
            if (eligible.isEmpty()) {
                Toast.makeText(this, "No unassigned active chatters are available.", Toast.LENGTH_SHORT).show();
                return;
            }
            String[] labels = new String[eligible.size()];
            for (int i = 0; i < eligible.size(); i++) {
                JSONObject worker = eligible.get(i);
                labels[i] = worker.optString("displayName", "Chatter")
                        + " · " + worker.optString("email", "");
            }
            new AlertDialog.Builder(this).setTitle("Assign chatter")
                    .setItems(labels, (dialog, position) -> assignChatter(eligible.get(position)))
                    .setNegativeButton("Cancel", null)
                    .show();
        }));
    }

    private void assignChatter(JSONObject worker) {
        JSONObject body = new JSONObject();
        try {
            body.put("modelAccountId", modelId);
            body.put("workerIds", new JSONArray().put(worker.optString("_id")));
        } catch (JSONException error) {
            Toast.makeText(this, "Could not prepare assignment.", Toast.LENGTH_SHORT).show();
            return;
        }
        api.post("/api/v1/admin/assignments", body, callback(result -> {
            Toast.makeText(this, "Chatter assigned.", Toast.LENGTH_SHORT).show();
            loadModel();
        }));
    }

    private void confirmRemoveAssignment(String assignmentId) {
        if (assignmentId.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("Remove chatter assignment?")
                .setMessage("The chatter will no longer be able to access this model's workspace.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (dialog, which) ->
                        api.delete("/api/v1/admin/assignments/" + assignmentId,
                                callback(result -> loadModel())))
                .show();
    }

    private void loadAvatar(ImageView image, String imageUrl) {
        Uri uri = Uri.parse(imageUrl);
        String scheme = uri.getScheme();
        if (uri.getHost() == null
                || !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) {
            Log.w(TAG, "Ignoring invalid model profile image URL");
            return;
        }
        image.setTag(imageUrl);
        imageExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(imageUrl).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(10000);
                connection.setInstanceFollowRedirects(true);
                int statusCode = connection.getResponseCode();
                String contentType = connection.getContentType();
                if (statusCode < 200 || statusCode >= 300
                        || contentType == null || !contentType.toLowerCase().startsWith("image/")) {
                    throw new IOException("Profile image response is not a successful image.");
                }
                byte[] bytes;
                try (InputStream input = connection.getInputStream();
                     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int total = 0;
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        total += read;
                        if (total > MAX_IMAGE_BYTES) throw new IOException("Profile image is too large.");
                        output.write(buffer, 0, read);
                    }
                    bytes = output.toByteArray();
                }
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                int sampleSize = 1;
                while (bounds.outWidth / sampleSize > 1024 || bounds.outHeight / sampleSize > 1024) {
                    sampleSize *= 2;
                }
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sampleSize;
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                if (bitmap == null) throw new IOException("Profile image could not be decoded.");
                runOnUiThread(() -> {
                    if (!isFinishing() && imageUrl.equals(image.getTag())) image.setImageBitmap(bitmap);
                });
            } catch (IOException | RuntimeException error) {
                Log.w(TAG, "Unable to load model profile image", error);
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private ApiClient.Callback callback(java.util.function.Consumer<JSONObject> onSuccess) {
        progress.setVisibility(View.VISIBLE);
        return new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject result) {
                progress.setVisibility(View.GONE);
                onSuccess.accept(result);
            }

            @Override
            public void onError(String message) {
                progress.setVisibility(View.GONE);
                if (!api.hasSession()) {
                    startActivity(new Intent(ModelManagementActivity.this, LoginActivity.class));
                    finish();
                } else {
                    Toast.makeText(ModelManagementActivity.this, message, Toast.LENGTH_LONG).show();
                }
            }
        };
    }

    private boolean canManage() {
        return "ADMIN".equals(api.getRole());
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_skeumorphic_card);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(10);
        card.setLayoutParams(params);
        return card;
    }

    private LinearLayout actionRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private Button actionButton(String label, int tintColor) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setBackgroundTintList(getColorStateList(tintColor));
        button.setTextColor(getResources().getColor(R.color.white));
        return button;
    }

    private LinearLayout.LayoutParams weightedButton() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1);
        params.setMargins(dp(4), dp(8), dp(4), 0);
        return params;
    }

    private TextView text(String value, int sizeSp, int colorResource, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sizeSp);
        text.setTextColor(getResources().getColor(colorResource));
        if (bold) text.setTypeface(null, android.graphics.Typeface.BOLD);
        return text;
    }

    private void addSectionTitle(String value) {
        TextView heading = text(value, 12, R.color.lovanta_gray, true);
        heading.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams params = wrapParams();
        params.topMargin = dp(18);
        params.bottomMargin = dp(8);
        heading.setLayoutParams(params);
        content.addView(heading);
    }

    private void addText(String value) {
        TextView message = text(value, 14, R.color.lovanta_gray, false);
        message.setPadding(dp(4), dp(8), dp(4), dp(8));
        content.addView(message);
    }

    private LinearLayout formLayout() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), 0);
        return form;
    }

    private EditText field(String hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setSingleLine(true);
        return field;
    }

    private LinearLayout.LayoutParams wrapParams() {
        return new LinearLayout.LayoutParams(-2, -2);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        imageExecutor.shutdownNow();
        super.onDestroy();
    }
}
