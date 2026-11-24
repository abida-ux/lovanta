package com.example.lovanta;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private static final String TOKEN = "token";
    private static final String WORKER_ID = "worker_id";
    private static final String ROLE = "role";
    private static final String MUST_CHANGE_PASSWORD = "must_change_password";
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static ApiClient instance;

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

    public void saveSession(String token, String workerId, String role, boolean mustChangePassword) {
        preferences().edit()
                .putString(TOKEN, token)
                .putString(WORKER_ID, workerId)
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
                postError(callback, messageFor(error));
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
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
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
        connection.disconnect();
        JSONObject result = responseText.isEmpty() ? new JSONObject() : new JSONObject(responseText);
        if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
            clearSession();
            throw new IOException("Session expired. Please sign in again.");
        }
        if (status < 200 || status >= 300) {
            throw new IOException(result.optString("error", "Request failed (" + status + ")"));
        }
        return result;
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

    private String messageFor(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? "Unable to reach the server" : message;
    }
}
