package com.example.lovanta;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.util.Locale;
import android.view.View;
import android.view.LayoutInflater;
import android.widget.LinearLayout;

public class TaskActivity extends AppCompatActivity {
    private static final long SUMMARY_CACHE_MAX_AGE_MS = 5 * 60 * 1000;
    private ApiClient api;
    private View ongoingTab;
    private View endedTab;
    private View ongoingContent;
    private View endedContent;
    private JSONObject cachedSummary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task);
        ScreenInsets.applyToMainRoot(this);
        BottomNavigation.bind(this, R.id.navTask);
        api = ApiClient.get(this);
        ongoingTab = findViewById(R.id.taskOngoingTab);
        endedTab = findViewById(R.id.taskEndedTab);
        ongoingContent = findViewById(R.id.taskOngoingContent);
        endedContent = findViewById(R.id.taskEndedContent);
        ongoingTab.setOnClickListener(v -> showOngoingTasks());
        endedTab.setOnClickListener(v -> showEndedTasks());
        showOngoingTasks();
        setupMilestones();
        setupTaskCards();
        cachedSummary = api.getCachedChatterSummary();
        if (cachedSummary != null) renderSummary(cachedSummary);
        loadSummary();
    }

    private void loadSummary() {
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
                    startActivity(new Intent(TaskActivity.this, LoginActivity.class));
                    finish();
                    overridePendingTransition(0, 0);
                } else if (cachedSummary == null) {
                    Toast.makeText(TaskActivity.this, message, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void renderSummary(JSONObject result) {
        api.saveStaffNumber(result.optString("staffNumber", ""));
        double pending = result.optDouble("pendingCommission", 0);
        double paid = result.optDouble("paidCommission", 0);
        ((TextView) findViewById(R.id.tvPendingCommission)).setText(money(pending));
        ((TextView) findViewById(R.id.tvPaidCommission)).setText(money(paid));
        ((TextView) findViewById(R.id.tvTaskPaidPpv)).setText(
                result.optString("paidPpvPurchases", "0") + " total paid PPVs");
        ((TextView) findViewById(R.id.tvTaskSales)).setText(
                money(result.optDouble("salesAmount", 0)));
        ((TextView) findViewById(R.id.tvTaskSalesCount)).setText(
                result.optString("successfulSales", "0") + " successful sales");
    }

    private void setupMilestones() {
        int[] rewardsInCents = {50, 150, 400, 700, 1500, 2500};
        int[] targets = {5, 15, 35, 70, 150, 300};
        LinearLayout milestones = findViewById(R.id.taskMilestones);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < targets.length; i++) {
            View milestone = inflater.inflate(R.layout.item_task_milestone, milestones, false);
            String reward = String.format(Locale.getDefault(), "$%.2f", rewardsInCents[i] / 100.0);
            ((TextView) milestone.findViewById(R.id.tvMilestoneReward)).setText(reward);
            ((TextView) milestone.findViewById(R.id.tvMilestoneTarget))
                    .setText(targets[i] + " PPVs");
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(64),
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(dp(4));
            milestones.addView(milestone, params);
        }
    }

    private void setupTaskCards() {
        configureTaskCard(R.id.ogBonusTask, "OG Bonus", "Chat OG");
        configureTaskCard(R.id.recallTask, "Recall Your Old Match", "Chat 10 old conversations");
        configureTaskCard(R.id.oldMatchPpvTask, "At least 1 PPV in Old Match",
                "Unlock 1 PPV in an old conversation");
    }

    private void configureTaskCard(int cardId, String title, String description) {
        View card = findViewById(cardId);
        ((TextView) card.findViewById(R.id.tvProgressTitle)).setText(title);
        ((TextView) card.findViewById(R.id.tvProgressDescription)).setText(description);
        ((TextView) card.findViewById(R.id.tvProgressStatus))
                .setText("Task-specific progress is not included in the available account data.");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showOngoingTasks() {
        ongoingContent.setVisibility(View.VISIBLE);
        endedContent.setVisibility(View.GONE);
        setTabStyle(ongoingTab, true);
        setTabStyle(endedTab, false);
    }

    private void showEndedTasks() {
        ongoingContent.setVisibility(View.GONE);
        endedContent.setVisibility(View.VISIBLE);
        setTabStyle(ongoingTab, false);
        setTabStyle(endedTab, true);
    }

    private void setTabStyle(View tab, boolean selected) {
        tab.setBackgroundResource(selected ? R.drawable.bg_pill_active : R.drawable.bg_pill_inactive);
        ((TextView) tab).setTextColor(getResources().getColor(
                selected ? R.color.white : R.color.lovanta_dark));
    }

    private String money(double value) {
        return String.format(Locale.getDefault(), "$%.2f", value);
    }
}
