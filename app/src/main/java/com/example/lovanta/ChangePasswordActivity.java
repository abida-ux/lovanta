package com.example.lovanta;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

public class ChangePasswordActivity extends AppCompatActivity {
    private EditText currentPassword;
    private EditText newPassword;
    private EditText confirmPassword;
    private TextView errorText;
    private Button saveButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);
        currentPassword = findViewById(R.id.etCurrentPassword);
        newPassword = findViewById(R.id.etNewPassword);
        confirmPassword = findViewById(R.id.etConfirmPassword);
        errorText = findViewById(R.id.tvPasswordError);
        saveButton = findViewById(R.id.btnSavePassword);
        saveButton.setOnClickListener(v -> save());
    }

    private void save() {
        String oldValue = currentPassword.getText().toString();
        String newValue = newPassword.getText().toString();
        if (newValue.length() < 12) {
            showError("Choose a password with at least 12 characters.");
            return;
        }
        if (!newValue.equals(confirmPassword.getText().toString())) {
            showError("The new passwords do not match.");
            return;
        }
        JSONObject body = new JSONObject();
        try {
            body.put("currentPassword", oldValue);
            body.put("newPassword", newValue);
        } catch (JSONException error) {
            showError("Unable to prepare the password update.");
            return;
        }
        saveButton.setEnabled(false);
        ApiClient.get(this).post("/api/staff/auth/password/change", body, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject result) {
                ApiClient.get(ChangePasswordActivity.this).replaceToken(result.optString("token"));
                ApiClient.get(ChangePasswordActivity.this).markPasswordChanged();
                openHome();
            }

            @Override
            public void onError(String message) {
                saveButton.setEnabled(true);
                showError(message);
            }
        });
    }

    private void openHome() {
        String role = ApiClient.get(this).getRole();
        Class<?> target = "ADMIN".equals(role) || "SUPERVISOR".equals(role)
                ? AdminActivity.class : MainActivity.class;
        startActivity(new Intent(this, target));
        finish();
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }
}
