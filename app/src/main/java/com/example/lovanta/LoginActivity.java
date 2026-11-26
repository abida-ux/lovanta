package com.example.lovanta;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {
    private EditText emailInput;
    private EditText passwordInput;
    private Button signInButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        emailInput = findViewById(R.id.etEmail);
        passwordInput = findViewById(R.id.etPassword);
        signInButton = findViewById(R.id.btnSignIn);

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
            new AlertDialog.Builder(this)
                    .setTitle(banned ? "Account banned" : "Account disabled")
                    .setMessage(message + "\n\nContact your Lovanta administrator to ask about the restriction and the next steps.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
