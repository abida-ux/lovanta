package com.example.lovanta;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DataActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data);
        ScreenInsets.applyToMainRoot(this);
        BottomNavigation.bind(this, R.id.navData);

        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", Locale.getDefault());
        Date now = new Date();
        ((TextView) findViewById(R.id.tvDataDate)).setText(dateFormat.format(now));
        ((TextView) findViewById(R.id.tvDataDay)).setText(dayFormat.format(now));
        ((TextView) findViewById(R.id.tvWorkerId)).setText("Staff ID: " + ApiClient.get(this).getWorkerId());
        ((TextView) findViewById(R.id.tvNewMatchesCount)).setText("—");
        ((TextView) findViewById(R.id.tvOldMatchesCount)).setText("—");
        ((TextView) findViewById(R.id.tvPpvCount)).setText("—");

        ApiClient.get(this).get("/api/v1/chatter/summary", new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject result) {
                ((TextView) findViewById(R.id.tvNewMatchesCount)).setText(result.optString("assignedModels", "0"));
                ((TextView) findViewById(R.id.tvOldMatchesCount)).setText(result.optString("activeConversations", "0"));
                ((TextView) findViewById(R.id.tvPpvCount)).setText(result.optString("paidPpvPurchases", "0"));
                LinearLayout sections = findViewById(R.id.dataSections);
                sections.removeAllViews();
                addMetricSection(sections, "Messages & sales", R.color.lovanta_pink, new String[][]{
                        {"Messages sent", result.optString("messagesSent", "0")},
                        {"Successful sales", result.optString("successfulSales", "0")},
                        {"Sales amount", money(result.optDouble("salesAmount", 0))}
                });
                addMetricSection(sections, "Commission", R.color.lovanta_yellow, new String[][]{
                        {"Pending", money(result.optDouble("pendingCommission", 0))},
                        {"Paid", money(result.optDouble("paidCommission", 0))}
                });
            }

            @Override
            public void onError(String message) {
                Toast.makeText(DataActivity.this, message, Toast.LENGTH_LONG).show();
                if (!ApiClient.get(DataActivity.this).hasSession()) {
                    startActivity(new android.content.Intent(DataActivity.this, LoginActivity.class));
                    finish();
                    return;
                }
                ((TextView) findViewById(R.id.tvNewMatchesCount)).setText("—");
                ((TextView) findViewById(R.id.tvOldMatchesCount)).setText("—");
                ((TextView) findViewById(R.id.tvPpvCount)).setText("—");
            }
        });
    }

    private void addMetricSection(LinearLayout parent, String title, int titleColor, String[][] metrics) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_skeumorphic_card);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(14);
        parent.addView(card, cardParams);

        TextView heading = new TextView(this);
        heading.setText(title);
        int accentColor = titleColor == R.color.lovanta_yellow
                ? R.color.lovanta_yellow_dark : titleColor;
        heading.setTextColor(getResources().getColor(accentColor));
        heading.setTextSize(18);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        headingParams.bottomMargin = dp(10);
        card.addView(heading, headingParams);

        for (int i = 0; i < metrics.length; i++) {
            String[] metric = metrics[i];
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(titleColor == R.color.lovanta_yellow
                    ? R.drawable.bg_data_row_yellow : R.drawable.bg_data_row);
            row.setPadding(dp(14), dp(14), dp(14), dp(14));

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(58));
            rowParams.bottomMargin = i == metrics.length - 1 ? 0 : dp(8);
            card.addView(row, rowParams);

            TextView label = new TextView(this);
            label.setText(metric[0]);
            label.setTextColor(getResources().getColor(R.color.lovanta_dark));
            label.setTextSize(15);
            label.setTypeface(null, android.graphics.Typeface.BOLD);
            row.addView(label, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            TextView value = new TextView(this);
            value.setText(metric[1]);
            value.setTextColor(getResources().getColor(accentColor));
            value.setTextSize(16);
            value.setTypeface(null, android.graphics.Typeface.BOLD);
            row.addView(value);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String money(double value) {
        return String.format(Locale.getDefault(), "$%.2f", value);
    }
}
