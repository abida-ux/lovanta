package com.example.lovanta;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class MeActivity extends AppCompatActivity {
    private static final long SUMMARY_CACHE_MAX_AGE_MS = 5 * 60 * 1000;
    private ApiClient api;
    private JSONObject cachedSummary;

    private static final int[] OPTION_IDS = {
            R.id.optionAdmin, R.id.optionReceiving, R.id.optionEarning, R.id.optionPayroll,
            R.id.optionSalary, R.id.optionData, R.id.optionDeduction, R.id.optionVersion,
            R.id.optionSkin, R.id.optionLogout
    };
    private static final String[] OPTION_LABELS = {
            "Online Admin", "Receiving Account", "Earning Details", "History Payroll",
            "Salary Structure", "Data Center", "Deduction", "App Version", "Skin Setting", "Logout"
    };
    private static final int[] OPTION_ICONS = {
            R.drawable.ic_phone, R.drawable.ic_attach, R.drawable.ic_tab_data, R.drawable.ic_bell,
            R.drawable.ic_star, R.drawable.ic_tab_data, R.drawable.ic_tab_task,
            R.drawable.ic_person, R.drawable.ic_tab_me, R.drawable.ic_arrow_back
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_me);
        ScreenInsets.applyToMainRoot(this);
        BottomNavigation.bind(this, R.id.navMe);
        api = ApiClient.get(this);

        setupOptions();
        findViewById(R.id.tvStaffIdentifier).setContentDescription(
                "Staff account " + api.getWorkerId());
        findViewById(R.id.tvStaffIdentifier).setTag("Staff ID: " + api.getWorkerId());
        ((TextView) findViewById(R.id.tvStaffIdentifier)).setText(
                "Staff ID: " + api.getWorkerId());
        ((TextView) findViewById(R.id.tvStaffBadge)).setText("1");
        ((TextView) findViewById(R.id.tvLevelProgress)).setText("Start your PPV activity");
        ((TextView) findViewById(R.id.tvLevelNextUpdate)).setText("Next level update: today");
        ((TextView) findViewById(R.id.tvBalance)).setText("$0.00");
        loadModels();
        loadSummary();
    }

    private void loadModels() {
        RecyclerView rvModels = findViewById(R.id.rvMyModels);
        List<StoryItem> models = new ArrayList<>();
        StoryAdapter adapter = new StoryAdapter(models, model -> {
            Intent intent = new Intent(this, ModelProfileActivity.class);
            intent.putExtra(ModelProfileActivity.EXTRA_NAME, model.getName());
            intent.putExtra(ModelProfileActivity.EXTRA_AVATAR, model.getAvatarResId());
            intent.putExtra(ModelProfileActivity.EXTRA_BIO, model.getBio());
            startActivity(intent);
        });
        rvModels.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvModels.setAdapter(adapter);
        ApiClient.get(this).get("/api/v1/models", new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject result) {
                JSONArray records = result.optJSONArray("models");
                if (records != null) {
                    for (int i = 0; i < records.length(); i++) {
                        JSONObject model = records.optJSONObject(i);
                        if (model == null) continue;
                        models.add(new StoryItem(model.optString("displayName", "Model"),
                                R.drawable.ic_person, model.optString("description", ""), 0, 0));
                    }
                }
                adapter.notifyDataSetChanged();
                ((TextView) findViewById(R.id.tvModelCount)).setText("Assigned models: " + models.size());
            }

            @Override
            public void onError(String message) {
                Toast.makeText(MeActivity.this, message, Toast.LENGTH_LONG).show();
                if (!ApiClient.get(MeActivity.this).hasSession()) {
                    startActivity(new Intent(MeActivity.this, LoginActivity.class));
                    finish();
                    return;
                }
                ((TextView) findViewById(R.id.tvModelCount)).setText("Assigned models: unavailable");
            }
        });
    }

    private void loadSummary() {
        cachedSummary = api.getCachedChatterSummary();
        if (cachedSummary != null) renderSummary(cachedSummary);
        if (api.isChatterSummaryCacheFresh(SUMMARY_CACHE_MAX_AGE_MS)) return;

        String summaryWorkerId = api.getWorkerId();
        api.get("/api/v1/chatter/summary", new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject result) {
                api.cacheChatterSummary(summaryWorkerId, result);
                cachedSummary = result;
                renderSummary(result);
            }

            @Override
            public void onError(String message) {
                if (!api.hasSession()) {
                    startActivity(new Intent(MeActivity.this, LoginActivity.class));
                    finish();
                    overridePendingTransition(0, 0);
                } else if (cachedSummary == null) {
                    Toast.makeText(MeActivity.this, message, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void renderSummary(JSONObject result) {
        double paidCommission = result.optDouble("paidCommission", 0);
        int paidPpv = result.optInt("paidPpvPurchases", 0);
        ((TextView) findViewById(R.id.tvBalance)).setText(
                String.format(java.util.Locale.getDefault(), "$%.2f", paidCommission));
        int level = paidPpv >= 15 ? 2 : 1;
        ((TextView) findViewById(R.id.tvStaffBadge)).setText(String.valueOf(level));
        if (level == 1) {
            int remaining = Math.max(0, 15 - paidPpv);
            ((TextView) findViewById(R.id.tvLevelProgress)).setText(
                    "Unlock " + remaining + " more PPV to reach LV 2");
        } else {
            ((TextView) findViewById(R.id.tvLevelProgress)).setText("LV 2 unlocked");
        }
    }

    private void setupOptions() {
        for (int i = 0; i < OPTION_IDS.length; i++) {
            int optionId = OPTION_IDS[i];
            View option = findViewById(OPTION_IDS[i]);
            ImageView icon = option.findViewById(R.id.ivOptionIcon);
            TextView title = option.findViewById(R.id.tvOptionTitle);
            TextView trailing = option.findViewById(R.id.tvOptionTrailing);

            title.setText(OPTION_LABELS[i]);
            icon.setImageResource(OPTION_ICONS[i]);
            int iconColor = i == OPTION_IDS.length - 1
                    ? getResources().getColor(R.color.lovanta_pink)
                    : getResources().getColor(R.color.lovanta_dark);
            icon.setColorFilter(iconColor);
            if (OPTION_IDS[i] == R.id.optionVersion) {
                trailing.setText("v1.0");
                trailing.setTextSize(12);
            }

            option.setClickable(true);
            option.setFocusable(true);
            option.setBackgroundResource(android.R.drawable.list_selector_background);
            final int index = i;
            option.setOnClickListener(v -> handleOption(index));
        }
    }

    private void handleOption(int index) {
        int optionId = OPTION_IDS[index];
        if (optionId == R.id.optionData) {
            startActivity(new Intent(this, DataActivity.class));
            finish();
        } else if (optionId == R.id.optionSalary || optionId == R.id.optionEarning) {
            startActivity(new Intent(this, TaskActivity.class));
            finish();
        } else if (optionId == R.id.optionLogout) {
            ApiClient.get(this).clearSession();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        } else if (optionId != R.id.optionVersion) {
            Toast.makeText(this, OPTION_LABELS[index], Toast.LENGTH_SHORT).show();
        }
    }
}
