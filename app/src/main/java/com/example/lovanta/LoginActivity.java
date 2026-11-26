package com.example.lovanta;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {
    private static final String STATUS_NOTIFICATION_CHANNEL = "account_status";
    private static final int STATUS_NOTIFICATION_ID = 1001;
    private EditText emailInput;
    private EditText passwordInput;
    private Button signInButton;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private String pendingNotificationTitle;
    private String pendingNotificationMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        emailInput = findViewById(R.id.etEmail);
        passwordInput = findViewById(R.id.etPassword);
        signInButton = findViewById(R.id.btnSignIn);
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted && pendingNotificationTitle != null
                            && pendingNotificationMessage != null) {
                        postStatusNotification(pendingNotificationTitle, pendingNotificationMessage);
                    }
                    pendingNotificationTitle = null;
                    pendingNotificationMessage = null;
                });

        ApiClient api = ApiClient.get(this);
        if (api.hasSession()) {
            openRoleHome(api.getRole());
            finish();
            return;
        }
        signInButton.setOnClickListener(v -> signIn());
    }

    private void signIn() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Enter your email or username and password.", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        ApiClient.get(this).login(email, password, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject result) {
                JSONObject worker = result.optJSONObject("worker");
                if (worker == null || result.optString("token").isEmpty()) {
                    setLoading(false);
                    showError("Unable to sign in. Please try again.");
                    return;
                }
                String role = worker.optString("role", "");
                ApiClient.get(LoginActivity.this).saveSession(
                        result.optString("token"),
                        worker.optString("id"),
                        worker.optString("staffNumber"),
                        role,
                        false
                );
                openRoleHome(role);
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                showError(message);
            }
        });
    }

    private void openRoleHome(String role) {
        Class<?> target = "ADMIN".equals(role) || "SUPERVISOR".equals(role)
                ? AdminActivity.class : MainActivity.class;
        startActivity(new Intent(this, target));
        finish();
    }

    private void setLoading(boolean loading) {
        signInButton.setEnabled(!loading);
        signInButton.setText(loading ? "Logging in..." : "Log in");
    }

    private void showError(String message) {
        if (message.startsWith("Your chatter account is disabled.")
                || message.startsWith("Your chatter account has been banned.")) {
            boolean banned = message.startsWith("Your chatter account has been banned.");
            String title = banned ? "Account banned" : "Account disabled";
            String instructions = message.replace(
                    "Please contact an administrator.",
                    "Please contact your Lovanta administrator to ask about the restriction and next steps.");
            MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(
                    this, R.style.ThemeOverlay_Lovanta_StatusDialog)
                    .setTitle(title)
                    .setMessage(instructions)
                    .setPositiveButton("OK", null)
                    .setOnDismissListener(ignored -> notifyStatus(title, instructions));
            dialog.show();
            return;
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void notifyStatus(String title, String message) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            pendingNotificationTitle = title;
            pendingNotificationMessage = message;
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        postStatusNotification(title, message);
    }

    private void postStatusNotification(String title, String message) {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    STATUS_NOTIFICATION_CHANNEL,
                    "Account status",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Account access status messages");
            notificationManager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                STATUS_NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, STATUS_NOTIFICATION_CHANNEL)
                : new Notification.Builder(this).setPriority(Notification.PRIORITY_DEFAULT);
        Notification notification = builder
                .setSmallIcon(R.mipmap.ic_launcher)
                .setColor(getResources().getColor(R.color.lovanta_pink))
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_STATUS)
                .build();
        notificationManager.notify(STATUS_NOTIFICATION_ID, notification);
    }
}
