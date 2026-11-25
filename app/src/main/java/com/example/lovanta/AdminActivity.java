package com.example.lovanta;

import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.text.SimpleDateFormat;
import java.util.Date;

public class AdminActivity extends AppCompatActivity {
    private static final String[] PAGES = {
            "Overview", "Models", "Chatters", "Model Assignments",
            "Account Management", "Reports & Analytics", "Settings"
    };

    private ApiClient api;
    private LinearLayout navigation;
    private LinearLayout content;
    private ProgressBar progress;
    private TextView title;
    private View modelFilters;
    private EditText modelSearch;
    private Spinner modelStatusFilter;
    private View chatterFilters;
    private EditText chatterSearch;
    private Spinner chatterStatusFilter;
    private String currentPage = "Overview";
    private JSONArray cachedModels = new JSONArray();
    private JSONArray cachedChatters = new JSONArray();
    private int activeRequests;
    private int pageRequestVersion;
    private final Handler chatterSearchHandler = new Handler(Looper.getMainLooper());
    private final Handler modelSearchHandler = new Handler(Looper.getMainLooper());
    private final Runnable modelSearchReload = () -> loadPage("Models");
    private final Runnable chatterSearchReload = () -> loadPage("Chatters");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);
        ScreenInsets.applyToRoot(this, R.id.adminRoot);
        api = ApiClient.get(this);
        navigation = findViewById(R.id.adminNavigation);
        content = findViewById(R.id.adminContent);
        progress = findViewById(R.id.adminProgress);
        title = findViewById(R.id.tvAdminTitle);
        modelFilters = findViewById(R.id.modelFilters);
        modelSearch = findViewById(R.id.adminModelSearch);
        modelStatusFilter = findViewById(R.id.adminModelStatus);
        chatterFilters = findViewById(R.id.chatterFilters);
        chatterSearch = findViewById(R.id.adminChatterSearch);
        chatterStatusFilter = findViewById(R.id.adminChatterStatus);

        String role = api.getRole();
        ((TextView) findViewById(R.id.tvAdminRole)).setText(role);
        boolean isAdmin = "ADMIN".equals(role);
        findViewById(R.id.adminActions).setVisibility(isAdmin ? View.VISIBLE : View.GONE);
        setupModelFilters();
        setupChatterFilters();
        setupNavigation();
        updateNavigation();
        findViewById(R.id.btnAdminLogout).setOnClickListener(v -> signOut());
        findViewById(R.id.btnCreateModel).setOnClickListener(v -> createModel());
        findViewById(R.id.btnCreateChatter).setOnClickListener(v -> createChatter());
        findViewById(R.id.btnAssignModel).setOnClickListener(v -> beginAssignment());
        loadPage(currentPage);
        preloadDirectoryData();
    }

    private void setupNavigation() {
        for (String page : PAGES) {
            if (!pageAllowed(page)) continue;
            Button button = new Button(this);
            button.setText(page);
            button.setTag(page);
            button.setAllCaps(false);
            button.setTextSize(12);
            button.setMinWidth(0);
            button.setPadding(dp(12), 0, dp(12), 0);
            button.setBackgroundResource("Overview".equals(page)
                    ? R.drawable.bg_pill_active : R.drawable.bg_pill_inactive);
            button.setTextColor(getResources().getColor("Overview".equals(page)
                    ? R.color.white : R.color.lovanta_dark));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(36));
            params.setMargins(dp(4), dp(8), dp(4), 0);
            navigation.addView(button, params);
            button.setOnClickListener(v -> {
                currentPage = page;
                updateNavigation();
                loadPage(page);
            });
        }
    }

    private boolean pageAllowed(String page) {
        if ("ADMIN".equals(api.getRole())) return true;
        return Arrays.asList("Overview", "Models",
                "Reports & Analytics").contains(page);
    }

    private void updateNavigation() {
        title.setText(currentPage);
        modelFilters.setVisibility("Models".equals(currentPage) ? View.VISIBLE : View.GONE);
        chatterFilters.setVisibility("Chatters".equals(currentPage) ? View.VISIBLE : View.GONE);
        boolean admin = "ADMIN".equals(api.getRole());
        boolean modelActions = admin && "Models".equals(currentPage);
        boolean chatterActions = admin && "Chatters".equals(currentPage);
        boolean assignmentActions = admin && "Model Assignments".equals(currentPage);
        findViewById(R.id.adminActions).setVisibility(
                modelActions || chatterActions || assignmentActions ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnCreateModel).setVisibility(modelActions ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnCreateChatter).setVisibility(chatterActions ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnAssignModel).setVisibility(assignmentActions ? View.VISIBLE : View.GONE);
        for (int i = 0; i < navigation.getChildCount(); i++) {
            Button button = (Button) navigation.getChildAt(i);
            boolean selected = currentPage.equals(button.getTag());
            button.setBackgroundResource(selected ? R.drawable.bg_pill_active : R.drawable.bg_pill_inactive);
            button.setTextColor(getResources().getColor(selected ? R.color.white : R.color.lovanta_dark));
        }
    }

    private void loadPage(String page) {
        int requestVersion = ++pageRequestVersion;
        content.removeAllViews();
        content.setVisibility(View.INVISIBLE);
        if ("Settings".equals(page)) {
            renderSettings();
            return;
        }
        if ("Reports & Analytics".equals(page)) {
            api.get("/api/v1/admin/dashboard", callback(result -> {
                if (requestVersion == pageRequestVersion) renderReports(result);
            }));
            return;
        }
        if ("Overview".equals(page)) {
            api.get("/api/v1/admin/dashboard", callback(result -> {
                if (requestVersion == pageRequestVersion) renderDashboard(result);
            }));
            return;
        }
        String path = "/api/v1/admin/" + routeFor(page) + "?page=1&limit=100";
        if ("Models".equals(page)) {
            String search = modelSearch.getText().toString().trim();
            if (!search.isEmpty()) path += "&search=" + Uri.encode(search);
            int statusPosition = modelStatusFilter.getSelectedItemPosition();
            if (statusPosition > 0) {
                String[] statuses = {"", "active", "inactive", "suspended", "archived"};
                path += "&status=" + statuses[statusPosition];
            }
        }
        if ("Chatters".equals(page)) {
            String search = chatterSearch.getText().toString().trim();
            if (!search.isEmpty()) path += "&search=" + Uri.encode(search);
            int statusPosition = chatterStatusFilter.getSelectedItemPosition();
            if (statusPosition > 0) {
                String[] statuses = {"", "ACTIVE", "DISABLED", "BANNED", "ARCHIVED"};
                path += "&status=" + statuses[statusPosition];
            }
        }
        api.get(path, callback(result -> {
            if (requestVersion != pageRequestVersion) return;
            JSONArray records = "Model Assignments".equals(page)
                    ? result.optJSONArray("assignments") : result.optJSONArray("items");
            renderRecords(page, records);
        }));
    }

    private void setupChatterFilters() {
        String[] labels = {"All accounts", "Active", "Disabled", "Banned", "Archived"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        chatterStatusFilter.setAdapter(adapter);
        chatterSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable value) {
                if (!"Chatters".equals(currentPage)) return;
                chatterSearchHandler.removeCallbacks(chatterSearchReload);
                chatterSearchHandler.postDelayed(chatterSearchReload, 300);
            }
        });
        chatterStatusFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!"Chatters".equals(currentPage)) return;
                chatterSearchHandler.removeCallbacks(chatterSearchReload);
                chatterSearchHandler.postDelayed(chatterSearchReload, 100);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupModelFilters() {
        String[] labels = {"All statuses", "Active", "Inactive", "Suspended", "Archived"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modelStatusFilter.setAdapter(adapter);
        modelSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable value) {
                scheduleModelReload();
            }
        });
        modelStatusFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(
                    AdapterView<?> parent, View view, int position, long id) {
                scheduleModelReload();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void scheduleModelReload() {
        if (!"Models".equals(currentPage)) return;
        modelSearchHandler.removeCallbacks(modelSearchReload);
        modelSearchHandler.postDelayed(modelSearchReload, 300);
    }

    @Override
    protected void onDestroy() {
        modelSearchHandler.removeCallbacks(modelSearchReload);
        chatterSearchHandler.removeCallbacks(chatterSearchReload);
        super.onDestroy();
    }

    private String routeFor(String page) {
        switch (page) {
            case "Chatters": return "chatters";
            case "Model Assignments": return "assignments";
            case "Account Management": return "subscribers";
            default: return "models";
        }
    }

    private void renderDashboard(JSONObject data) {
        content.removeAllViews();
        content.setVisibility(View.VISIBLE);
        addSectionTitle("Management overview");
        addMetricRow(
                new String[]{"Total models", "Active models", "Disabled accounts"},
                new String[]{
                        data.optString("totalModels", data.optString("models", "0")),
                        data.optString("activeModels", "0"),
                        data.optString("disabledAccounts", "0"),
                });
        addMetricRow(
                new String[]{"Total chatters", "Active chatters"},
                new String[]{
                        data.optString("totalChatters", data.optString("chatters", "0")),
                        data.optString("activeChatters", "0"),
                });

        addSectionTitle("Recent recruitment");
        JSONArray recruited = data.optJSONArray("recentRecruitment");
        if (recruited == null || recruited.length() == 0) {
            addText("No chatter accounts have been created yet.");
        } else {
            for (int i = 0; i < recruited.length(); i++) {
                JSONObject chatter = recruited.optJSONObject(i);
                if (chatter == null) continue;
                addActivityLine(
                        chatter.optString("displayName", "Chatter")
                                + " · " + chatter.optString("email", ""),
                        formatDateTime(chatter.optString("createdAt", "")));
            }
        }

        addSectionTitle("Recent administrative actions");
        JSONArray actions = data.optJSONArray("recentActions");
        if (actions == null || actions.length() == 0) {
            addText("No administrative actions have been recorded yet.");
        } else {
            for (int i = 0; i < actions.length(); i++) {
                JSONObject action = actions.optJSONObject(i);
                if (action == null) continue;
                String reason = action.optString("reason", "");
                addActivityLine(
                        formatAdminAction(action.optString("action", "Action"))
                                + " · " + action.optString("affectedType", "Record"),
                        formatDateTime(action.optString("createdAt", ""))
                                + (reason.isEmpty() ? "" : " · " + reason));
            }
        }
    }

    private void renderReports(JSONObject data) {
        content.removeAllViews();
        content.setVisibility(View.VISIBLE);
        addSectionTitle("Business reporting");
        addMetricRow(
                new String[]{"Total revenue", "Successful transactions"},
                new String[]{
                        money(data.optDouble("totalRevenue", 0)),
                        data.optString("successfulTransactions", "0"),
                });
        addMetricRow(
                new String[]{"Revenue this month", "Revenue this week"},
                new String[]{
                        money(data.optDouble("revenueMonth", 0)),
                        money(data.optDouble("revenueWeek", 0)),
                });
        addMetricRow(
                new String[]{"Paid PPV purchases", "Paid commissions"},
                new String[]{
                        data.optString("paidPpvPurchases", "0"),
                        money(data.optDouble("paidCommissions", 0)),
                });
        addMetricRow(
                new String[]{"Pending transactions", "Pending commissions"},
                new String[]{
                        data.optString("pendingTransactions", "0"),
                        money(data.optDouble("pendingCommissions", 0)),
                });
        addText("Chatter conversation and sales activity stays in the chatter workspace.");
    }

    private void renderSettings() {
        content.removeAllViews();
        content.setVisibility(View.VISIBLE);
        addSectionTitle("Settings");
        addText("There are no in-app system settings configured. Backend environment settings are managed in Render.");
    }

    private void renderRecords(String page, JSONArray records) {
        content.removeAllViews();
        if ("Models".equals(page)) {
            cachedModels = records == null ? new JSONArray() : records;
        }
        renderItems(page, records == null ? new JSONArray() : records);
    }

    private void renderItems(String page, JSONArray records) {
        content.removeAllViews();
        content.setVisibility(View.VISIBLE);
        if ("Chatters".equals(page)) {
            renderChatterTable(records);
            return;
        }
        addSectionTitle(page + " · " + records.length());
        if (records.length() == 0) {
            addText("No " + page.toLowerCase() + " records found.");
            return;
        }

        for (int i = 0; i < records.length(); i++) {
            JSONObject row = records.optJSONObject(i);
            if (row == null) continue;
            LinearLayout card = card();
            if ("Models".equals(page)) {
                card.setOnClickListener(v -> openModelManagement(row));
            }
            TextView primary = new TextView(this);
            primary.setText(primaryValue(page, row));
            primary.setTextColor(getResources().getColor(R.color.lovanta_dark));
            primary.setTextSize(16);
            primary.setTypeface(null, android.graphics.Typeface.BOLD);
            card.addView(primary);

            TextView details = new TextView(this);
            details.setText(detailsValue(page, row));
            details.setTextColor(getResources().getColor(R.color.lovanta_gray));
            details.setTextSize(12);
            details.setPadding(0, dp(5), 0, 0);
            card.addView(details);

            if ("Models".equals(page) && "ADMIN".equals(api.getRole())) {
                addModelActions(card, row);
            } else if ("Account Management".equals(page) && "ADMIN".equals(api.getRole())) {
                addSubscriberActions(card, row);
            } else if ("Model Assignments".equals(page) && "ADMIN".equals(api.getRole())
                    && row.optBoolean("active", false)) {
                addAssignmentRemoveAction(card, row);
            }
            content.addView(card);
        }
    }

    private String primaryValue(String page, JSONObject row) {
        switch (page) {
            case "Models": return row.optString("displayName", "Model");
            case "Chatters":
                return row.optString("displayName", "Staff") + " · " + row.optString("role", "");
            case "Model Assignments":
                return nestedName(row.opt("modelAccountId")) + " ↔ " + nestedName(row.opt("workerId"));
            case "Account Management": return row.optString("username", "Subscriber");
            default: return row.optString("action", "Audit event");
        }
    }

    private String detailsValue(String page, JSONObject row) {
        switch (page) {
            case "Models":
                return "Model ID: " + row.optString("modelId", row.optString("_id", "—"))
                        + "\n@" + row.optString("username", "—") + " · " + row.optString("status", "active")
                        + "\n" + row.optString("description", "");
            case "Chatters":
                return "Status: " + row.optString("status", "—")
                        + "\nLast login: " + jsonDisplayValue(row, "lastLoginAt", "Never");
            case "Model Assignments":
                return "Status: " + row.optString("status", row.optBoolean("active") ? "active" : "removed")
                        + " · Assigned: " + jsonDisplayValue(row, "assignedAt", "—");
            case "Account Management":
                return "Status: " + row.optString("status", "—")
                        + " · Registered: " + jsonDisplayValue(row, "createdAt", "—");
            default:
                return row.optString("actorRole", "") + " · " + row.optString("affectedType", "")
                        + "\n" + jsonDisplayValue(row, "createdAt", "—");
        }
    }

    private void renderChatterTable(JSONArray chatters) {
        addSectionTitle("Chatter accounts · " + chatters.length());
        if (chatters.length() == 0) {
            addText("No chatter accounts match this search.");
            return;
        }

        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setFillViewport(false);
        horizontal.setHorizontalScrollBarEnabled(true);
        LinearLayout table = new LinearLayout(this);
        table.setOrientation(LinearLayout.VERTICAL);
        int[] widths = {82, 190, 170, 100, 150, 150, 125};
        String[] headers = {"Staff ID", "Login identifier", "Assigned models",
                "Status", "Created", "Last login", "Actions"};
        table.addView(chatterTableRow(headers, widths, true));

        for (int i = 0; i < chatters.length(); i++) {
            JSONObject chatter = chatters.optJSONObject(i);
            if (chatter == null) continue;
            String id = chatter.optString("_id", "");
            JSONArray assigned = chatter.optJSONArray("assignedModels");
            String[] values = {
                    chatter.optString("staffNumber", "—"),
                    chatter.optString("email", ""),
                    assignedModelNames(assigned),
                    chatter.optString("status", "ACTIVE"),
                    jsonDisplayValue(chatter, "createdAt", "—"),
                    jsonDisplayValue(chatter, "lastLoginAt", "Never"),
                    "View / manage",
            };
            LinearLayout row = chatterTableRow(values, widths, false);
            row.setOnClickListener(v -> showChatterProfile(id));
            table.addView(row);
            View divider = new View(this);
            divider.setBackgroundColor(getResources().getColor(R.color.lovanta_border));
            table.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        }
        horizontal.addView(table);
        content.addView(horizontal, new LinearLayout.LayoutParams(-1, -2));
    }

    private LinearLayout chatterTableRow(String[] values, int[] widths, boolean header) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(header ? 42 : 52));
        if (header) {
            row.setBackgroundColor(getResources().getColor(R.color.lovanta_pink_light));
        } else {
            row.setBackgroundResource(android.R.drawable.list_selector_background);
        }
        for (int i = 0; i < widths.length; i++) {
            TextView cell = new TextView(this);
            cell.setText(values[i]);
            cell.setTextSize(header ? 12 : 11);
            cell.setTextColor(getResources().getColor(R.color.lovanta_dark));
            cell.setPadding(dp(8), dp(10), dp(8), dp(10));
            cell.setMaxLines(2);
            cell.setEllipsize(android.text.TextUtils.TruncateAt.END);
            if (header || (!header && (i == 0 || i == 3 || i == 6))) {
                cell.setTypeface(null, android.graphics.Typeface.BOLD);
            }
            if (!header && i == 3) {
                int statusColor = "ACTIVE".equals(values[i])
                        ? R.color.lovanta_pink
                        : "BANNED".equals(values[i]) ? R.color.lovanta_yellow_dark : R.color.lovanta_gray;
                cell.setTextColor(getResources().getColor(statusColor));
            } else if (!header && i == 6) {
                cell.setTextColor(getResources().getColor(R.color.lovanta_pink));
            }
            row.addView(cell, new LinearLayout.LayoutParams(dp(widths[i]), -2));
        }
        return row;
    }

    private String assignedModelNames(JSONArray assignments) {
        if (assignments == null || assignments.length() == 0) return "None";
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < assignments.length(); i++) {
            JSONObject assignment = assignments.optJSONObject(i);
            if (assignment == null) continue;
            if (names.length() > 0) names.append(", ");
            names.append(assignment.optString("modelName", "Model"));
        }
        return names.length() == 0 ? "None" : names.toString();
    }

    private void showChatterProfile(String id) {
        api.get("/api/v1/admin/chatters/" + id, callback(result -> {
            JSONObject chatter = result.optJSONObject("chatter");
            if (chatter == null) {
                toast("Unable to load chatter profile.");
                return;
            }
            showChatterProfileDialog(chatter, result.optJSONArray("assignedModels"),
                    result.optJSONArray("history"));
        }));
    }

    private void showChatterProfileDialog(
            JSONObject chatter, JSONArray assignedModels, JSONArray history) {
        String id = chatter.optString("id", "");
        String email = chatter.optString("email", "");
        String status = chatter.optString("status", "ACTIVE");
        ScrollView scroll = new ScrollView(this);
        LinearLayout panel = formLayout();
        AlertDialog[] profileDialog = new AlertDialog[1];
        panel.addView(profileLine("Staff ID", chatter.optString("staffNumber", "—")));
        panel.addView(profileLine("Login identifier", email));
        panel.addView(profileLine("Account status", status));
        panel.addView(profileLine("Role", chatter.optString("role", "CHATTER")));
        panel.addView(profileLine("Created", jsonDisplayValue(chatter, "createdAt", "—")));
        panel.addView(profileLine("Last login", jsonDisplayValue(chatter, "lastLoginAt", "Never")));
        if (!chatter.isNull("createdBy")) {
            panel.addView(profileLine("Created by", "Administrator"));
        }
        if ("DISABLED".equals(status) && !chatter.optString("disabledReason", "").isEmpty()) {
            panel.addView(profileLine("Disable reason", chatter.optString("disabledReason")));
        }
        if ("BANNED".equals(status)) {
            panel.addView(profileLine("Ban reason", chatter.optString("banReason", "—")));
        }

        addSectionTo(panel, "Assigned models");
        if (assignedModels == null || assignedModels.length() == 0) {
            addMessageTo(panel, "No models are assigned.");
        } else {
            for (int i = 0; i < assignedModels.length(); i++) {
                JSONObject assignment = assignedModels.optJSONObject(i);
                if (assignment == null) continue;
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                TextView modelName = new TextView(this);
                modelName.setText(assignment.optString("modelName", "Model"));
                modelName.setTextSize(13);
                modelName.setTextColor(getResources().getColor(R.color.lovanta_dark));
                row.addView(modelName, new LinearLayout.LayoutParams(0, -2, 1));
                Button unassign = smallButton("Unassign");
                String assignmentId = assignment.optString("assignmentId", "");
                unassign.setOnClickListener(v -> {
                    if (profileDialog[0] != null) profileDialog[0].dismiss();
                    confirmChatterUnassignment(id, assignmentId);
                });
                row.addView(unassign);
                panel.addView(row);
            }
        }

        addSectionTo(panel, "Administrative history");
        if (history == null || history.length() == 0) {
            addMessageTo(panel, "No account actions have been recorded.");
        } else {
            for (int i = 0; i < history.length(); i++) {
                JSONObject entry = history.optJSONObject(i);
                if (entry == null) continue;
                String reason = entry.optString("reason", "");
                addMessageTo(panel,
                        formatAdminAction(entry.optString("action", "Action"))
                                + "\n" + formatDateTime(entry.optString("createdAt", ""))
                                + (reason.isEmpty() ? "" : "\nReason: " + reason));
            }
        }

        addSectionTo(panel, "Account actions");
        AlertDialog dialog = dialogBuilder()
                .setTitle(email.isEmpty() ? "Chatter profile" : email)
                .setView(scroll)
                .setPositiveButton("Done", null)
                .create();
        profileDialog[0] = dialog;
        scroll.addView(panel);

        if ("ACTIVE".equals(status)) {
            addProfileAction(panel, "Disable account", () -> {
                dialog.dismiss();
                requestAccountStatus(id, "DISABLED");
            });
        } else if ("DISABLED".equals(status)) {
            addProfileAction(panel, "Reactivate account", () -> {
                dialog.dismiss();
                requestAccountStatus(id, "ACTIVE");
            });
        }
        if ("ACTIVE".equals(status) || "DISABLED".equals(status)) {
            addProfileAction(panel, "Ban account", () -> {
                dialog.dismiss();
                requestReason("Ban chatter", "Enter a reason for permanently blocking this account.",
                        true, reason -> changeChatterState(id, "ban", reason));
            });
        } else if ("BANNED".equals(status)) {
            addProfileAction(panel, "Unban account", () -> {
                dialog.dismiss();
                requestReason("Unban chatter", "Enter a reason for restoring this account.",
                        true, reason -> changeChatterState(id, "unban", reason));
            });
        }
        if (!"ARCHIVED".equals(status)) {
            addProfileAction(panel, "Reset password", () -> {
                dialog.dismiss();
                resetPassword(id, email);
            });
            addProfileAction(panel, "Archive account", () -> {
                dialog.dismiss();
                requestReason("Archive chatter", "Optionally enter a reason for archiving.",
                        false, reason -> changeChatterState(id, "archive", reason));
            });
        }
        if ("ACTIVE".equals(status)) {
            addProfileAction(panel, "Assign models", () -> {
                dialog.dismiss();
                chooseModelsForChatter(id, assignedModels);
            });
        }
        dialog.show();
    }

    private LinearLayout profileLine(String label, String value) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(9), 0, dp(9));
        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(12);
        name.setTextColor(getResources().getColor(R.color.lovanta_gray));
        row.addView(name, new LinearLayout.LayoutParams(dp(112), -2));
        TextView contentValue = new TextView(this);
        contentValue.setText(value.isEmpty() ? "—" : value);
        contentValue.setTextSize(13);
        contentValue.setTypeface(null, android.graphics.Typeface.BOLD);
        contentValue.setTextColor(getResources().getColor(R.color.lovanta_dark));
        row.addView(contentValue, new LinearLayout.LayoutParams(0, -2, 1));
        container.addView(row);
        View divider = new View(this);
        divider.setBackgroundColor(getResources().getColor(R.color.lovanta_border));
        container.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        return container;
    }

    private void addSectionTo(LinearLayout target, String label) {
        TextView heading = new TextView(this);
        heading.setText(label);
        heading.setTextSize(14);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setTextColor(getResources().getColor(R.color.lovanta_pink));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(target.getChildCount() == 0 ? 2 : 14);
        target.addView(heading, params);
    }

    private void addMessageTo(LinearLayout target, String message) {
        TextView text = new TextView(this);
        text.setText(message);
        text.setTextSize(12);
        text.setTextColor(getResources().getColor(R.color.lovanta_dark));
        text.setPadding(dp(12), dp(10), dp(12), dp(10));
        text.setBackgroundResource(R.drawable.bg_skeumorphic_card);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(6);
        target.addView(text, params);
    }

    private void addProfileAction(LinearLayout target, String label, Runnable action) {
        Button button = smallButton(label);
        if ("Ban account".equals(label) || "Archive account".equals(label)) {
            button.setBackgroundTintList(ColorStateList.valueOf(
                    getResources().getColor(R.color.lovanta_pink)));
            button.setTextColor(getResources().getColor(R.color.white));
        }
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(42));
        params.topMargin = dp(5);
        target.addView(button, params);
    }

    private void requestAccountStatus(String id, String status) {
        String action = "DISABLED".equals(status) ? "Disable" : "Reactivate";
        dialogBuilder()
                .setTitle(action + " chatter account?")
                .setMessage("This will invalidate the chatter's active sessions.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(action, (dialog, which) -> {
                    JSONObject body = new JSONObject();
                    try {
                        body.put("status", status);
                    } catch (JSONException error) {
                        toast("Unable to prepare account update.");
                        return;
                    }
                    api.patch("/api/v1/admin/chatters/" + id + "/status", body,
                            callback(result -> {
                                toast("Account " + status.toLowerCase() + ".");
                                loadPage("Chatters");
                            }));
                })
                .show();
    }

    private void requestReason(
            String title, String prompt, boolean required,
            java.util.function.Consumer<String> onConfirm) {
        LinearLayout form = formLayout();
        TextView message = new TextView(this);
        message.setText(prompt);
        form.addView(message);
        EditText reason = field(required ? "Reason (required)" : "Reason (optional)");
        reason.setSingleLine(false);
        reason.setMinLines(2);
        form.addView(reason);
        AlertDialog dialog = dialogBuilder()
                .setTitle(title)
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String value = reason.getText().toString().trim();
                    if (required && value.length() < 3) {
                        toast("Enter a reason of at least 3 characters.");
                        return;
                    }
                    dialog.dismiss();
                    onConfirm.accept(value);
                }));
        dialog.show();
    }

    private void changeChatterState(String id, String action, String reason) {
        JSONObject body = new JSONObject();
        try {
            if ("ban".equals(action) || "unban".equals(action)) {
                body.put("reason", reason);
            } else if (!reason.isEmpty()) {
                body.put("reason", reason);
            }
        } catch (JSONException error) {
            toast("Unable to prepare account action.");
            return;
        }
        api.post("/api/v1/admin/chatters/" + id + "/" + action, body, callback(result -> {
            toast("Chatter account updated.");
            loadPage("Chatters");
        }));
    }

    private void chooseModelsForChatter(String chatterId, JSONArray assignedModels) {
        Set<String> assignedIds = new HashSet<>();
        if (assignedModels != null) {
            for (int i = 0; i < assignedModels.length(); i++) {
                JSONObject assignment = assignedModels.optJSONObject(i);
                if (assignment != null) assignedIds.add(assignment.optString("modelId", ""));
            }
        }
        api.get("/api/v1/admin/models?page=1&limit=100", callback(result -> {
            JSONArray models = result.optJSONArray("items");
            if (models == null) models = new JSONArray();
            List<JSONObject> eligible = new ArrayList<>();
            for (int i = 0; i < models.length(); i++) {
                JSONObject model = models.optJSONObject(i);
                if (model == null || !isModelActive(model)
                        || assignedIds.contains(model.optString("_id", ""))) continue;
                eligible.add(model);
            }
            if (eligible.isEmpty()) {
                toast("No unassigned active models are available.");
                return;
            }
            String[] labels = new String[eligible.size()];
            boolean[] checked = new boolean[eligible.size()];
            for (int i = 0; i < eligible.size(); i++) {
                labels[i] = eligible.get(i).optString("displayName", "Model");
            }
            dialogBuilder()
                    .setTitle("Assign models")
                    .setMultiChoiceItems(labels, checked, (dialog, which, selected) ->
                            checked[which] = selected)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Assign", (dialog, which) -> {
                        JSONArray modelIds = new JSONArray();
                        for (int i = 0; i < eligible.size(); i++) {
                            if (checked[i]) modelIds.put(eligible.get(i).optString("_id"));
                        }
                        if (modelIds.length() == 0) {
                            toast("Select at least one model.");
                            return;
                        }
                        JSONObject body = new JSONObject();
                        try {
                            body.put("modelIds", modelIds);
                        } catch (JSONException error) {
                            toast("Unable to prepare model assignments.");
                            return;
                        }
                        api.post("/api/v1/admin/chatters/" + chatterId + "/assignments",
                                body, callback(saved -> {
                                    toast("Models assigned.");
                                    loadPage("Chatters");
                                    showChatterProfile(chatterId);
                                }));
                    })
                    .show();
        }));
    }

    private void confirmChatterUnassignment(String chatterId, String assignmentId) {
        dialogBuilder()
                .setTitle("Unassign model?")
                .setMessage("The chatter will lose access to this model's workspace.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Unassign", (dialog, which) ->
                        api.delete("/api/v1/admin/assignments/" + assignmentId,
                                callback(result -> {
                                    loadPage("Chatters");
                                    showChatterProfile(chatterId);
                                })))
                .show();
    }

    private void resetPassword(String id, String email) {
        api.post("/api/v1/admin/chatters/" + id + "/password-reset", new JSONObject(),
                callback(result -> showCredentials(
                        "Chatter password reset",
                        email,
                        result.optString("temporaryPassword", ""))));
    }

    private void showCredentials(String title, String login, String password) {
        showCredentials(title, login, password, "");
    }

    private void showCredentials(String title, String login, String password, String staffNumber) {
        LinearLayout panel = formLayout();
        addMessageTo(panel, "This is an app login ID only; it is not a Gmail inbox.");
        if (!staffNumber.isEmpty()) {
            addMessageTo(panel, "Staff ID: " + staffNumber);
        }
        addCredentialField(panel, "LOGIN ID", login, "Copy Login ID", login);
        addCredentialField(panel, "PASSWORD", password, "Copy Password", password);
        Button copyAll = smallButton("Copy All Credentials");
        copyAll.setBackgroundTintList(getColorStateList(R.color.lovanta_pink));
        copyAll.setTextColor(getResources().getColor(R.color.white));
        copyAll.setOnClickListener(v -> copyToClipboard(
                "Chatter credentials", "Login ID: " + login + "\nPassword: " + password));
        panel.addView(copyAll);
        dialogBuilder()
                .setTitle(title)
                .setView(panel)
                .setPositiveButton("Done", null)
                .show();
    }

    private void addCredentialField(
            LinearLayout panel, String label, String value, String buttonLabel, String copyValue) {
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(12);
        labelView.setTypeface(null, android.graphics.Typeface.BOLD);
        labelView.setTextColor(getResources().getColor(R.color.lovanta_gray));
        panel.addView(labelView);
        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextIsSelectable(true);
        valueView.setTextSize(16);
        valueView.setTextColor(getResources().getColor(R.color.lovanta_dark));
        valueView.setTypeface(null, android.graphics.Typeface.BOLD);
        valueView.setBackgroundResource(R.drawable.bg_admin_input);
        valueView.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(-1, -2);
        valueParams.bottomMargin = dp(6);
        panel.addView(valueView, valueParams);
        Button copy = smallButton(buttonLabel);
        copy.setOnClickListener(v -> copyToClipboard(label, copyValue));
        panel.addView(copy);
    }

    private void copyToClipboard(String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
        toast(label + " copied.");
    }

    private void openModelManagement(JSONObject model) {
        String modelId = model.optString("modelId", model.optString("_id", ""));
        if (modelId.isEmpty()) {
            toast("Could not open this model.");
            return;
        }
        Intent intent = new Intent(this, ModelManagementActivity.class);
        intent.putExtra(ModelManagementActivity.EXTRA_MODEL_ID, modelId);
        startActivity(intent);
    }

    private void addModelActions(LinearLayout card, JSONObject model) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button manage = smallButton("Manage");
        manage.setOnClickListener(v -> openModelManagement(model));
        Button edit = smallButton("Edit");
        edit.setOnClickListener(v -> editModel(model));
        row.addView(manage, weightedButton());
        row.addView(edit, weightedButton());
        if (!"archived".equals(model.optString("status"))) {
            Button archive = smallButton("Archive");
            archive.setOnClickListener(v -> {
                JSONObject body = new JSONObject();
                try { body.put("status", "archived"); } catch (JSONException ignored) { return; }
                api.patch("/api/v1/admin/models/" + model.optString("_id"), body, callback(result -> loadPage("Models")));
            });
            row.addView(archive, weightedButton());
        }
        card.addView(row);
    }

    private void editModel(JSONObject model) {
        LinearLayout form = formLayout();
        EditText name = field("Display name");
        name.setText(model.optString("displayName"));
        EditText description = field("Description");
        description.setText(model.optString("description"));
        form.addView(name);
        form.addView(description);
        dialogBuilder().setTitle("Edit model").setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    JSONObject body = new JSONObject();
                    try {
                        body.put("displayName", name.getText().toString().trim());
                        body.put("description", description.getText().toString().trim());
                    } catch (JSONException error) {
                        toast("Could not prepare model update");
                        return;
                    }
                    api.patch("/api/v1/admin/models/" + model.optString("_id"), body,
                            callback(result -> loadPage("Models")));
                }).show();
    }

    private void addSubscriberActions(LinearLayout card, JSONObject subscriber) {
        Button status = smallButton("active".equals(subscriber.optString("status")) ? "Suspend" : "Reactivate");
        status.setOnClickListener(v -> {
            JSONObject body = new JSONObject();
            try {
                body.put("status", "active".equals(subscriber.optString("status")) ? "suspended" : "active");
            } catch (JSONException ignored) {
                return;
            }
            api.patch("/api/v1/admin/subscribers/" + subscriber.optString("_id") + "/status",
                    body, callback(result -> loadPage("Account Management")));
        });
        card.addView(status);
    }

    private void addAssignmentRemoveAction(LinearLayout card, JSONObject assignment) {
        Button remove = smallButton("Remove assignment");
        remove.setOnClickListener(v -> api.delete(
                "/api/v1/admin/assignments/" + assignment.optString("_id"),
                callback(result -> loadPage("Model Assignments"))));
        card.addView(remove);
    }

    private void createModel() {
        LinearLayout form = formLayout();
        EditText name = field("Display name");
        EditText username = field("Username");
        EditText avatar = field("Profile image URL (optional)");
        EditText description = field("Description (optional)");
        form.addView(name);
        form.addView(username);
        form.addView(avatar);
        form.addView(description);
        dialogBuilder().setTitle("Create model profile").setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", (dialog, which) -> {
                    if (name.getText().toString().trim().isEmpty()) {
                        toast("Model name is required");
                        return;
                    }
                    JSONObject body = new JSONObject();
                    try {
                        body.put("displayName", name.getText().toString().trim());
                        if (!username.getText().toString().trim().isEmpty()) {
                            body.put("username", username.getText().toString().trim());
                        }
                        if (!avatar.getText().toString().trim().isEmpty()) body.put("avatarUrl", avatar.getText().toString().trim());
                        if (!description.getText().toString().trim().isEmpty()) body.put("description", description.getText().toString().trim());
                    } catch (JSONException error) {
                        toast("Could not prepare model");
                        return;
                    }
                    api.post("/api/v1/admin/models", body, callback(result -> {
                        toast("Model created");
                        loadPage(currentPage);
                    }));
                }).show();
    }

    private void createChatter() {
        api.post("/api/v1/admin/chatters", new JSONObject(), callback(result -> {
            JSONObject chatter = result.optJSONObject("chatter");
            String email = chatter == null ? "" : chatter.optString("email", "");
            String staffNumber = chatter == null ? "" : chatter.optString("staffNumber", "");
            String password = result.optString("temporaryPassword", "");
            if (email.isEmpty() || password.isEmpty()) {
                toast("Account may have been created. Use Reset Password to generate login details.");
                loadPage("Chatters");
                return;
            }
            showCredentials("Chatter account created", email, password, staffNumber);
            loadPage("Chatters");
        }));
    }

    private void beginAssignment() {
        api.get("/api/v1/admin/models?page=1&limit=100", callback(modelsResult -> {
            cachedModels = modelsResult.optJSONArray("items") == null ? new JSONArray() : modelsResult.optJSONArray("items");
            api.get("/api/v1/admin/chatters?page=1&limit=100", callback(staffResult -> {
                cachedChatters = staffResult.optJSONArray("items") == null ? new JSONArray() : staffResult.optJSONArray("items");
                chooseModelForAssignment();
            }));
        }));
    }

    private void chooseModelForAssignment() {
        List<JSONObject> eligibleModels = new ArrayList<>();
        for (int i = 0; i < cachedModels.length(); i++) {
            JSONObject model = cachedModels.optJSONObject(i);
            if (model != null && isModelActive(model)) eligibleModels.add(model);
        }
        if (eligibleModels.isEmpty() || cachedChatters.length() == 0) {
            toast("An active model and an active chatter are required");
            return;
        }
        String[] names = new String[eligibleModels.size()];
        for (int i = 0; i < eligibleModels.size(); i++) {
            names[i] = eligibleModels.get(i).optString("displayName");
        }
        dialogBuilder().setTitle("Choose model")
                .setItems(names, (dialog, index) -> chooseChatters(eligibleModels.get(index)))
                .setNegativeButton("Cancel", null).show();
    }

    private void chooseChatters(JSONObject model) {
        List<JSONObject> eligible = new ArrayList<>();
        for (int i = 0; i < cachedChatters.length(); i++) {
            JSONObject staff = cachedChatters.optJSONObject(i);
            if (staff != null && staff.optBoolean("active", false) && isChatter(staff)) {
                eligible.add(staff);
            }
        }
        if (eligible.isEmpty()) {
            toast("No active chatters are available");
            return;
        }
        String[] names = new String[eligible.size()];
        boolean[] selected = new boolean[eligible.size()];
        for (int i = 0; i < eligible.size(); i++) names[i] = eligible.get(i).optString("displayName");
        dialogBuilder().setTitle("Select chatter(s)")
                .setMultiChoiceItems(names, selected, (dialog, index, checked) -> selected[index] = checked)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Assign", (dialog, which) -> {
                    JSONArray workerIds = new JSONArray();
                    for (int i = 0; i < eligible.size(); i++) if (selected[i]) workerIds.put(eligible.get(i).optString("_id"));
                    if (workerIds.length() == 0) {
                        toast("Select at least one chatter");
                        return;
                    }
                    JSONObject body = new JSONObject();
                    try {
                        body.put("modelAccountId", model.optString("_id"));
                        body.put("workerIds", workerIds);
                    } catch (JSONException error) {
                        toast("Could not prepare assignment");
                        return;
                    }
                    api.post("/api/v1/admin/assignments", body, callback(result -> {
                        toast("Assignment saved");
                        loadPage("Model Assignments");
                    }));
                }).show();
    }

    private void preloadDirectoryData() {
        api.get("/api/v1/admin/models?page=1&limit=100", callback(result ->
                cachedModels = result.optJSONArray("items") == null ? new JSONArray() : result.optJSONArray("items")));
        api.get("/api/v1/admin/chatters?page=1&limit=100", callback(result ->
                cachedChatters = result.optJSONArray("items") == null ? new JSONArray() : result.optJSONArray("items")));
    }

    private boolean isModelActive(JSONObject model) {
        String status = model.optString("status", "active");
        return model.optBoolean("active", true)
                && ("active".equals(status) || status.isEmpty());
    }

    private boolean isChatter(JSONObject staff) {
        String role = staff.optString("role", "");
        return "CHATTER".equals(role) || "worker".equals(role);
    }

    private ApiClient.Callback callback(java.util.function.Consumer<JSONObject> onSuccess) {
        return callback(onSuccess, message -> {
            if (!api.hasSession()) {
                startActivity(new Intent(AdminActivity.this, LoginActivity.class));
                finish();
            } else {
                Toast.makeText(AdminActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private ApiClient.Callback callback(
            java.util.function.Consumer<JSONObject> onSuccess,
            java.util.function.Consumer<String> onError) {
        activeRequests++;
        progress.setVisibility(View.VISIBLE);
        return new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject result) {
                finishRequest();
                onSuccess.accept(result);
            }

            @Override
            public void onError(String message) {
                finishRequest();
                if (content.getVisibility() == View.INVISIBLE) {
                    content.setVisibility(View.VISIBLE);
                    addText(message);
                }
                onError.accept(message);
            }
        };
    }

    private void finishRequest() {
        activeRequests = Math.max(0, activeRequests - 1);
        progress.setVisibility(activeRequests == 0 ? View.GONE : View.VISIBLE);
    }

    private void signOut() {
        api.clearSession();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private void addMetricRow(String[] labels, String[] values) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setBackgroundResource(R.drawable.bg_skeumorphic_card);
            tile.setGravity(Gravity.CENTER_VERTICAL);
            tile.setPadding(dp(14), dp(12), dp(14), dp(12));
            TextView label = new TextView(this);
            label.setText(labels[i]);
            label.setTextSize(11);
            label.setTextColor(getResources().getColor(R.color.lovanta_gray));
            TextView value = new TextView(this);
            value.setText(values[i]);
            value.setTextSize(23);
            value.setTypeface(null, android.graphics.Typeface.BOLD);
            value.setTextColor(getResources().getColor(R.color.lovanta_pink));
            value.setPadding(0, dp(4), 0, 0);
            tile.addView(label);
            tile.addView(value);
            tile.setMinimumHeight(dp(74));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    labels.length == 1 ? -1 : 0, -2, labels.length == 1 ? 0 : 1);
            if (i > 0) params.leftMargin = dp(8);
            row.addView(tile, params);
        }
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.bottomMargin = dp(8);
        content.addView(row, rowParams);
    }

    private void addActivityLine(String primary, String secondary) {
        LinearLayout entry = new LinearLayout(this);
        entry.setGravity(Gravity.CENTER_VERTICAL);
        entry.setBackgroundResource(R.drawable.bg_skeumorphic_card);
        entry.setPadding(dp(12), dp(10), dp(12), dp(10));
        View accent = new View(this);
        accent.setBackgroundResource(R.drawable.bg_pill_active);
        entry.addView(accent, new LinearLayout.LayoutParams(dp(4), dp(34)));
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(dp(10), 0, 0, 0);
        TextView primaryText = new TextView(this);
        primaryText.setText(primary);
        primaryText.setTextSize(13);
        primaryText.setTypeface(null, android.graphics.Typeface.BOLD);
        primaryText.setTextColor(getResources().getColor(R.color.lovanta_dark));
        TextView secondaryText = new TextView(this);
        secondaryText.setText(secondary);
        secondaryText.setTextSize(11);
        secondaryText.setTextColor(getResources().getColor(R.color.lovanta_gray));
        secondaryText.setPadding(0, dp(3), 0, 0);
        text.addView(primaryText);
        if (!secondary.isEmpty()) text.addView(secondaryText);
        entry.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        content.addView(entry, params);
    }

    private void addSectionTitle(String text) {
        TextView heading = new TextView(this);
        heading.setText(text);
        heading.setTextSize(17);
        heading.setTextColor(getResources().getColor(R.color.lovanta_dark));
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setPadding(dp(2), dp(5), 0, dp(8));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = content.getChildCount() == 0 ? 0 : dp(10);
        content.addView(heading, params);
    }

    private void addText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(getResources().getColor(R.color.lovanta_dark));
        view.setBackgroundResource(R.drawable.bg_skeumorphic_card);
        view.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        content.addView(view, params);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_skeumorphic_card);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        card.setLayoutParams(params);
        return card;
    }

    private LinearLayout formLayout() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), 0);
        return form;
    }

    private EditText field(String hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setSingleLine(true);
        field.setTextSize(14);
        field.setTextColor(getResources().getColor(R.color.lovanta_dark));
        field.setHintTextColor(getResources().getColor(R.color.lovanta_gray));
        field.setBackgroundResource(R.drawable.bg_admin_input);
        field.setPadding(dp(12), dp(10), dp(12), dp(10));
        field.setMinHeight(dp(48));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        field.setLayoutParams(params);
        return field;
    }

    private Button smallButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setMinHeight(dp(40));
        button.setMinWidth(0);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setBackgroundTintList(ColorStateList.valueOf(
                getResources().getColor(R.color.lovanta_pink_light)));
        button.setTextColor(getResources().getColor(R.color.lovanta_pink));
        return button;
    }

    private AlertDialog.Builder dialogBuilder() {
        return new AlertDialog.Builder(
                new ContextThemeWrapper(this, R.style.ThemeOverlay_Lovanta_AdminDialog));
    }

    private LinearLayout.LayoutParams weightedButton() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1);
        params.setMargins(0, dp(6), dp(5), 0);
        return params;
    }

    private String nestedName(Object value) {
        if (value instanceof JSONObject) return ((JSONObject) value).optString("displayName", "");
        return value == null ? "" : value.toString();
    }

    private String money(double value) {
        return String.format(java.util.Locale.getDefault(), "$%.2f", value);
    }

    private String formatAdminAction(String action) {
        String readable = action.replace('.', ' ').replace('_', ' ').trim();
        if (readable.isEmpty()) return "Administrative action";
        return Character.toUpperCase(readable.charAt(0)) + readable.substring(1);
    }

    private String jsonDisplayValue(JSONObject object, String key, String fallback) {
        if (object == null || object.isNull(key)) return fallback;
        String value = object.optString(key, "");
        return value.isEmpty() ? fallback
                : key.endsWith("At") ? formatDateTime(value) : value;
    }

    private String formatDateTime(String value) {
        if (value == null || value.isEmpty()) return "—";
        try {
            return new SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
                    .format(Date.from(java.time.Instant.parse(value)));
        } catch (RuntimeException error) {
            return value;
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
