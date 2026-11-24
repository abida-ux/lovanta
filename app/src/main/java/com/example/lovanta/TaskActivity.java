package com.example.lovanta;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.util.Locale;

public class TaskActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task);
        ScreenInsets.applyToMainRoot(this);
        BottomNavigation.bind(this, R.id.navTask);
        loadSummary();
    }

    private void loadSummary() {
        ApiClient.get(this).get("/api/v1/chatter/summary", new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject result) {
                double pending = result.optDouble("pendingCommission", 0);
                double paid = result.optDouble("paidCommission", 0);
                ((TextView) findViewById(R.id.tvPendingCommission)).setText(money(pending));
                ((TextView) findViewById(R.id.tvPaidCommission)).setText(money(paid));
                ((TextView) findViewById(R.id.tvCommissionTotal)).setText(money(pending + paid));
            }

            @Override
            public void onError(String message) {
                Toast.makeText(TaskActivity.this, message, Toast.LENGTH_LONG).show();
                if (!ApiClient.get(TaskActivity.this).hasSession()) {
                    startActivity(new Intent(TaskActivity.this, LoginActivity.class));
                    finish();
                }
            }
        });
    }

    private String money(double value) {
        return String.format(Locale.getDefault(), "$%.2f", value);
    }
}
