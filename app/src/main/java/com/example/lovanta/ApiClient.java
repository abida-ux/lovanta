package com.example.lovanta;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLException;

public final class ApiClient {
    public interface Callback {
        void onSuccess(JSONObject result);
        void onError(String message);
    }

    public interface WorkspaceCallback {
        void onSuccess(JSONArray models, JSONArray conversations);
        void onError(String message);
    }

    private static final String PREFS = "lovanta_session";
    private static final String TAG = "LovantaApi";
    private static final String TOKEN = "token";
    private static final String WORKER_ID = "worker_id";
    private static final String STAFF_NUMBER = "staff_number";
    private static final String ROLE = "role";
    private static final String MUST_CHANGE_PASSWORD = "must_change_password";
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static ApiClient instance;

    private static final class ApiResponseException extends IOException {
        private final int statusCode;
        private final String endpoint;
        private final String serverMessage;

        private ApiResponseException(int statusCode, String endpoint, String serverMessage) {
            super("HTTP " + statusCode);
            this.statusCode = statusCode;
            this.endpoint = endpoint;
            this.serverMessage = serverMessage;
        }
    }

    private final Context appContext;
    private final String baseUrl;

    private ApiClient(Context context) {
        appContext = context.getApplicationContext();
        baseUrl = appContext.getString(R.string.api_base_url).replaceAll("/+$", "");
    }

    public static synchronized ApiClient get(Context context) {
        if (instance == null) instance = new ApiClient(context);
        return instance;
    }

    public void saveSession(
            String token, String workerId, String staffNumber, String role, boolean mustChangePassword) {
        preferences().edit()
                .putString(TOKEN, token)
                .putString(WORKER_ID, workerId)
                .putString(STAFF_NUMBER, formatStaffNumber(staffNumber))
                .putString(ROLE, role)
                .putBoolean(MUST_CHANGE_PASSWORD, mustChangePassword)
                .apply();
    }

    public boolean mustChangePassword() {
        return preferences().getBoolean(MUST_CHANGE_PASSWORD, false);
    }

    public void markPasswordChanged() {
        preferences().edit().putBoolean(MUST_CHANGE_PASSWORD, false).apply();
    }

    public void replaceToken(String token) {
        preferences().edit().putString(TOKEN, token).apply();
    }

    public void clearSession() {
        preferences().edit().clear().apply();
    }

    public boolean hasSession() {
        return getToken() != null;
    }

    public String getRole() {
        return preferences().getString(ROLE, "");
    }

    public String getWorkerId() {
        return preferences().getString(WORKER_ID, "");
    }

    public String getStaffNumber() {
        return formatStaffNumber(preferences().getString(STAFF_NUMBER, ""));
    }

    public void saveStaffNumber(String staffNumber) {
        if (staffNumber == null || staffNumber.isEmpty()) return;
        preferences().edit().putString(STAFF_NUMBER, formatStaffNumber(staffNumber)).apply();
    }

    private String formatStaffNumber(String staffNumber) {
        String value = staffNumber == null ? "" : staffNumber.trim();
        if (value.isEmpty()) return "";
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < '0' || value.charAt(i) > '9') return staffNumber;
        }
        return value.length() >= 4 ? value : "0000".substring(0, 4 - value.length()) + value;
    }

    public JSONObject getCachedChatterSummary() {
        String key = summaryCacheKey(getWorkerId());
        String cached = preferences().getString(key, null);
        if (cached == null) return null;
        try {
            return new JSONObject(cached);
        } catch (JSONException error) {
            Log.w(TAG, "Ignoring invalid cached chatter summary", error);
            preferences().edit()
                    .remove(key)
                    .remove(key + "_updated")
                    .apply();
            return null;
        }
    }

    public boolean isChatterSummaryCacheFresh(long maxAgeMillis) {
        long updatedAt = preferences().getLong(summaryCacheKey(getWorkerId()) + "_updated", 0);
        long age = System.currentTimeMillis() - updatedAt;
        return updatedAt > 0 && age >= 0 && age < maxAgeMillis;
    }

    public void cacheChatterSummary(String workerId, JSONObject summary) {
        String key = summaryCacheKey(workerId);
        preferences().edit()
                .putString(key, summary.toString())
                .putLong(key + "_updated", System.currentTimeMillis())
                .apply();
    }

    public void login(String email, String password, Callback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("email", email);
            body.put("password", password);
        } catch (JSONException error) {
            callback.onError("Unable to prepare login request");
            return;
        }
        post("/api/staff/auth/login", body, callback);
    }

    public void get(String path, Callback callback) {
        enqueue("GET", path, null, callback);
    }

    public void post(String path, JSONObject body, Callback callback) {
        enqueue("POST", path, body, callback);
    }

    public void patch(String path, JSONObject body, Callback callback) {
        enqueue("PATCH", path, body, callback);
    }

    public void delete(String path, Callback callback) {
        enqueue("DELETE", path, null, callback);
    }

    public void loadWorkspace(WorkspaceCallback callback) {
        EXECUTOR.execute(() -> {
            try {
                JSONObject modelResult = request("GET", "/api/v1/models", null);
                JSONArray models = modelResult.optJSONArray("models");
                if (models == null) models = new JSONArray();
                JSONArray conversations = new JSONArray();
                for (int i = 0; i < models.length(); i++) {
                    JSONObject model = models.getJSONObject(i);
                    JSONObject result = request(
                            "GET",
                            "/api/v1/models/" + model.getString("id") + "/conversations?limit=100",
                            null
                    );
                    JSONArray modelConversations = result.optJSONArray("conversations");
                    if (modelConversations == null) continue;
                    for (int j = 0; j < modelConversations.length(); j++) {
                        JSONObject conversation = modelConversations.getJSONObject(j);
                        conversation.put("modelAccountId", model.getString("id"));
                        conversation.put("modelName", model.optString("displayName", ""));
                        conversations.put(conversation);
                    }
                }
                JSONArray finalModels = models;
                JSONArray finalConversations = conversations;
                MAIN.post(() -> callback.onSuccess(finalModels, finalConversations));
            } catch (Exception error) {
                postError(callback, messageFor(error));
            }
        });
    }

    public void getMessages(String conversationId, Callback callback) {
        get("/api/v1/conversations/" + conversationId + "/messages?limit=100", callback);
    }

    public void sendMessage(String conversationId, String text, Callback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("text", text);
        } catch (JSONException error) {
            callback.onError("Unable to prepare message");
            return;
        }
        post("/api/v1/conversations/" + conversationId + "/messages", body, callback);
    }

    private void enqueue(String method, String path, JSONObject body, Callback callback) {
        EXECUTOR.execute(() -> {
            try {
                JSONObject response = request(method, path, body);
                MAIN.post(() -> callback.onSuccess(response));
            } catch (Exception error) {
                postError(callback, messageFor(error, method, path));
            }
        });
    }

    private void postError(Callback callback, String message) {
        MAIN.post(() -> callback.onError(message));
    }

    private void postError(WorkspaceCallback callback, String message) {
        MAIN.post(() -> callback.onError(message));
    }

    private JSONObject request(String method, String path, JSONObject body) throws IOException, JSONException {
        String endpoint = baseUrl + path;
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Accept", "application/json");
            String token = getToken();
            if (token != null) connection.setRequestProperty("Authorization", "Bearer " + token);
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(data);
                }
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String responseText = readAll(stream);
            if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
                clearSession();
            }
            if (status < 200 || status >= 300) {
                String detail = errorFrom(responseText);
                throw new ApiResponseException(status, endpoint, detail);
            }
            try {
                return responseText.isEmpty() ? new JSONObject() : new JSONObject(responseText);
            } catch (JSONException error) {
                throw new IOException(
                        "Invalid JSON response from " + method + " " + endpoint
                                + " (HTTP " + status + ")",
                        error
                );
            }
        } finally {
            connection.disconnect();
        }
    }

    private String errorFrom(String responseText) {
        if (responseText.isEmpty()) return "";
        try {
            return new JSONObject(responseText).optString("error", "");
        } catch (JSONException ignored) {
            return "";
        }
    }

    private String readAll(InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) text.append(line);
        }
        return text.toString();
    }

    private SharedPreferences preferences() {
        return appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private String getToken() {
        return preferences().getString(TOKEN, null);
    }

    private String summaryCacheKey(String workerId) {
        return "chatter_summary_" + workerId;
    }

    private String messageFor(Exception error) {
        Log.e(TAG, "API request failed", error);
        return friendlyMessage(error, null, null);
    }

    private String messageFor(Exception error, String method, String path) {
        Log.e(TAG, "API request failed: " + method + " " + path, error);
        return friendlyMessage(error, method, path);
    }

    private String friendlyMessage(Exception error, String method, String path) {
        if (error instanceof ApiResponseException) {
            ApiResponseException response = (ApiResponseException) error;
            Log.w(TAG, "Server response " + response.statusCode + " from " + response.endpoint
                    + (response.serverMessage.isEmpty() ? "" : ": " + response.serverMessage));
            if (response.statusCode == 401) {
                boolean loginRequest = "POST".equals(method)
                        && path != null && path.startsWith("/api/staff/auth/login");
                return loginRequest
                        ? "Invalid email or password."
                        : "Your session has expired. Please sign in again.";
            }
            if (response.statusCode == 403) return "You don't have permission to access this page.";
            if (response.statusCode == 404) return "The requested information could not be found.";
            if (response.statusCode == 423) {
                boolean loginRequest = "POST".equals(method)
                        && path != null && path.startsWith("/api/staff/auth/login");
                if (loginRequest && isAccountStatusMessage(response.serverMessage)) {
                    return response.serverMessage;
                }
                return "This account is temporarily locked. Try again later.";
            }
            if (response.statusCode == 429) return "Too many attempts. Please wait and try again.";
            if (response.statusCode >= 500) return "The service is temporarily unavailable. Please try again.";
            if (response.statusCode == 400 || response.statusCode == 409) {
                return "Please check the information and try again.";
            }
            return "Unable to complete the request. Please try again.";
        }
        if (error instanceof SocketTimeoutException) {
            return "The request timed out. Check your connection and try again.";
        }
        if (error instanceof UnknownHostException) {
            return "Unable to connect. Check your internet connection and try again.";
        }
        if (error instanceof SSLException) {
            return "A secure connection could not be established. Please try again.";
        }
        if (error instanceof ConnectException) {
            return "Unable to reach the service. Check your connection and try again.";
        }
        return "Unable to complete the request. Please try again.";
    }

    private boolean isAccountStatusMessage(String message) {
        return "Your chatter account has been banned. Your case is being reviewed. Please contact an administrator."
                .equals(message)
                || "Your chatter account is disabled. Your case is being reviewed. Please contact an administrator."
                .equals(message)
                || "Your chatter account is archived. Please contact an administrator.".equals(message);
    }
}
