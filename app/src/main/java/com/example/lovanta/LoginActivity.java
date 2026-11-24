package com.example.lovanta;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {
    private EditText emailInput;
    private EditText passwordInput;
    private Button signInButton;
    private ProgressBar progress;
    private TextView errorText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        emailInput = findViewById(R.id.etEmail);
        passwordInput = findViewById(R.id.etPassword);
        signInButton = findViewById(R.id.btnSignIn);
        progress = findViewById(R.id.loginProgress);
        errorText = findViewById(R.id.tvLoginError);

        ApiClient api = ApiClient.get(this);
        if (api.hasSession()) {
            if (api.mustChangePassword()) {
                startActivity(new Intent(this, ChangePasswordActivity.class));
            } else {
                openRoleHome(api.getRole());
            }
            finish();
            return;
        }
        signInButton.setOnClickListener(v -> signIn());
    }

    private void signIn() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        if (email.isEmpty() || password.isEmpty()) {
            errorText.setText("Enter your staff email and password.");
            errorText.setVisibility(View.VISIBLE);
            return;
        }

        setLoading(true);
        ApiClient.get(this).login(email, password, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject result) {
                JSONObject worker = result.optJSONObject("worker");
                if (worker == null || result.optString("token").isEmpty()) {
                    setLoading(false);
                    showError("The server returned an invalid sign-in response.");
                    return;
                }
                String role = worker.optString("role", "");
                ApiClient.get(LoginActivity.this).saveSession(
                        result.optString("token"),
                        worker.optString("id"),
                        role,
                        result.optBoolean("mustChangePassword")
                );
                if (result.optBoolean("mustChangePassword")) {
                    startActivity(new Intent(LoginActivity.this, ChangePasswordActivity.class));
                    finish();
                    return;
                }
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
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        signInButton.setEnabled(!loading);
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }
}
