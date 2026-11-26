package com.example.lovanta;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ModelImageLoader {
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ModelImageLoader() {}

    public static void load(ImageView image, String imageUrl, int fallbackResource) {
        image.setImageResource(fallbackResource);
        image.setTag(imageUrl);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setPadding(0, 0, 0, 0);

        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            image.setColorFilter(image.getResources().getColor(R.color.lovanta_pink));
            return;
        }

        Uri uri = Uri.parse(imageUrl);
        String scheme = uri.getScheme();
        if (uri.getHost() == null
                || !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) {
            image.setColorFilter(image.getResources().getColor(R.color.lovanta_pink));
            return;
        }

        image.setColorFilter(image.getResources().getColor(R.color.lovanta_pink));
        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(imageUrl).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(10000);
                connection.setInstanceFollowRedirects(true);
                int statusCode = connection.getResponseCode();
                String contentType = connection.getContentType();
                if (statusCode < 200 || statusCode >= 300
                        || contentType == null
                        || !contentType.toLowerCase().startsWith("image/")) {
                    throw new IOException("Model avatar response is not a successful image.");
                }

                byte[] bytes;
                try (InputStream input = connection.getInputStream();
                     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int total = 0;
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        total += read;
                        if (total > MAX_IMAGE_BYTES) {
                            throw new IOException("Model avatar is too large.");
                        }
                        output.write(buffer, 0, read);
                    }
                    bytes = output.toByteArray();
                }

                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                int sampleSize = 1;
                while (bounds.outWidth / sampleSize > 1280
                        || bounds.outHeight / sampleSize > 1280) {
                    sampleSize *= 2;
                }
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sampleSize;
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                if (bitmap == null) throw new IOException("Model avatar could not be decoded.");

                MAIN.post(() -> {
                    if (imageUrl.equals(image.getTag())) {
                        image.setImageTintList(null);
                        image.clearColorFilter();
                        image.setImageBitmap(bitmap);
                    }
                });
            } catch (IOException | RuntimeException error) {
                android.util.Log.w("ModelImageLoader", "Unable to load model avatar", error);
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }
}
