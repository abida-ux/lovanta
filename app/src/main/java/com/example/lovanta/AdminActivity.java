package com.example.lovanta;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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
import java.util.List;

public class AdminActivity extends AppCompatActivity {
    private static final String[] PAGES = {
            "Overview", "Models", "Chatters", "Assignments", "Subscribers",
            "Conversations", "PPV", "Transactions", "Commissions", "Audit"
    };

    private ApiClient api;
    private LinearLayout navigation;
    private LinearLayout content;
    private ProgressBar progress;
    private TextView title;
    private View modelFilters;
    private EditText modelSearch;
    private Spinner modelStatusFilter;
    private String currentPage = "Overview";
    private JSONArray cachedModels = new JSONArray();
    private JSONArray cachedChatters = new JSONArray();
    private int activeRequests;
    private int pageRequestVersion;
    private final Handler modelSearchHandler = new Handler(Looper.getMainLooper());
    private final Runnable modelSearchReload = () -> loadPage("Models");

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

        String role = api.getRole();
        ((TextView) findViewById(R.id.tvAdminRole)).setText(role);
        boolean isAdmin = "ADMIN".equals(role);
        findViewById(R.id.adminActions).setVisibility(isAdmin ? View.VISIBLE : View.GONE);
        setupModelFilters();
        setupNavigation();
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
        return Arrays.asList("Overview", "Models", "Chatters", "Conversations",
                "PPV", "Transactions", "Audit").contains(page);
    }

    private void updateNavigation() {
        title.setText(currentPage);
        modelFilters.setVisibility("Models".equals(currentPage) ? View.VISIBLE : View.GONE);
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
        api.get(path, callback(result -> {
            if (requestVersion != pageRequestVersion) return;
            JSONArray records = "Assignments".equals(page)
                    ? result.optJSONArray("assignments") : result.optJSONArray("items");
            renderRecords(page, records);
        }));
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
        super.onDestroy();
    }

    private String routeFor(String page) {
        switch (page) {
            case "Chatters": return "chatters";
            case "Assignments": return "assignments";
            case "Subscribers": return "subscribers";
            case "Conversations": return "conversations";
            case "PPV": return "ppv";
            case "Transactions": return "transactions";
            case "Commissions": return "commissions";
            case "Audit": return "audit";
            default: return "models";
        }
    }

    private void renderDashboard(JSONObject data) {
        content.removeAllViews();
        content.setVisibility(View.VISIBLE);
        addSectionTitle("Operational overview");
        addMetric("Subscribers", data.optString("subscribers", "—"), "Active " + data.optString("activeSubscribers", "—"));
        addMetric("Models", data.optString("models", "—"), "Active " + data.optString("activeModels", "—"));
        addMetric("Chatters", data.optString("chatters", "—"), "Assigned staff");
        addMetric("Conversations", data.optString("conversations", "—"), "Active " + data.optString("activeConversations", "—"));
        addMetric("PPV purchases", data.optString("ppvPurchases", "—"), "PPV messages " + data.optString("ppvMessages", "—"));
        addMetric("Revenue", money(data.optDouble("totalRevenue", 0)), "Today " + money(data.optDouble("revenueToday", 0)));
        addMetric("Transactions", data.optString("successfulTransactions", "—") + " successful",
                data.optString("pendingTransactions", "—") + " pending · " + data.optString("refunds", "—") + " refunds");
        addSectionTitle("Workspace");
        addText("Metrics are calculated from records currently available in the server database. An empty database remains empty; no sample figures are shown.");
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

            if ("Chatters".equals(page) && "ADMIN".equals(api.getRole())) {
                addStaffActions(card, row);
            } else if ("Models".equals(page) && "ADMIN".equals(api.getRole())) {
                addModelActions(card, row);
            } else if ("Subscribers".equals(page) && "ADMIN".equals(api.getRole())) {
                addSubscriberActions(card, row);
            } else if ("Assignments".equals(page) && "ADMIN".equals(api.getRole())
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
            case "Assignments":
                return nestedName(row.opt("modelAccountId")) + " ↔ " + nestedName(row.opt("workerId"));
            case "Subscribers": return row.optString("username", "Subscriber");
            case "Conversations": return "Subscriber " + row.optString("subscriberId", "");
            case "PPV": return "PPV " + row.optString("status", "pending");
            case "Transactions": return row.optString("reference", "Transaction");
            case "Commissions": return "Commission " + money(row.optDouble("amount", 0));
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
                return row.optString("email", "") + " · " + (row.optBoolean("active", false) ? "Active" : "Suspended")
                        + "\nLast login: " + row.optString("lastLoginAt", "Not available");
            case "Assignments":
                return "Status: " + row.optString("status", row.optBoolean("active") ? "active" : "removed")
                        + " · Assigned: " + row.optString("assignedAt", "—");
            case "Subscribers":
                return "Status: " + row.optString("status", "—") + " · Registered: " + row.optString("createdAt", "—");
            case "Conversations":
                return row.optString("lastMessageText", "") + "\n" + row.optString("lastMessageAt", "");
            case "PPV":
                return money(row.optDouble("amount", 0)) + " · " + row.optString("purchasedAt", "Not purchased");
            case "Transactions":
                return money(row.optDouble("amount", 0)) + " " + row.optString("currency", "")
                        + " · " + row.optString("status", "") + " · " + row.optString("createdAt", "");
            case "Commissions":
                return row.optString("status", "") + " · " + row.optString("createdAt", "");
            default:
                return row.optString("actorRole", "") + " · " + row.optString("affectedType", "")
                        + " " + row.optString("affectedId", "") + "\n" + row.optString("createdAt", "");
        }
    }

    private void addStaffActions(LinearLayout card, JSONObject staff) {
        String id = staff.optString("_id", "");
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button reset = smallButton("Reset password");
        reset.setOnClickListener(v -> resetPassword(id));
        Button status = smallButton(staff.optBoolean("active", false) ? "Suspend" : "Reactivate");
        status.setOnClickListener(v -> changeStaffStatus(id, !staff.optBoolean("active", false)));
        row.addView(reset, weightedButton());
        row.addView(status, weightedButton());
        card.addView(row);
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
        new AlertDialog.Builder(this).setTitle("Edit model").setView(form)
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
                    body, callback(result -> loadPage("Subscribers")));
        });
        card.addView(status);
    }

    private void addAssignmentRemoveAction(LinearLayout card, JSONObject assignment) {
        Button remove = smallButton("Remove assignment");
        remove.setOnClickListener(v -> api.delete(
                "/api/v1/admin/assignments/" + assignment.optString("_id"),
                callback(result -> loadPage("Assignments"))));
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
        new AlertDialog.Builder(this).setTitle("Create model profile").setView(form)
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
        api.get("/api/v1/admin/models?page=1&limit=100", callback(result -> {
            cachedModels = result.optJSONArray("items") == null
                    ? new JSONArray() : result.optJSONArray("items");
            showCreateChatterForm();
        }));
    }

    private void showCreateChatterForm() {
        LinearLayout form = formLayout();
        EditText name = field("Full name");
        TextView loginInfo = new TextView(this);
        loginInfo.setText("Login email and temporary password are generated automatically "
                + "(chatter1@gmail.com, chatter2@gmail.com, ...).");
        loginInfo.setTextColor(getResources().getColor(R.color.lovanta_gray));
        loginInfo.setTextSize(13);
        EditText username = field("Username (optional)");
        EditText commission = field("Commission rate % (optional)");
        TextView assignmentLabel = new TextView(this);
        assignmentLabel.setText("Assign to model");
        assignmentLabel.setTextColor(getResources().getColor(R.color.lovanta_dark));
        assignmentLabel.setTextSize(14);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(8);
        assignmentLabel.setLayoutParams(labelParams);

        List<String> modelIds = new ArrayList<>();
        List<String> modelOptions = new ArrayList<>();
        modelIds.add("");
        modelOptions.add(cachedModels.length() == 0 ? "No models available" : "No assignment");
        for (int i = 0; i < cachedModels.length(); i++) {
            JSONObject model = cachedModels.optJSONObject(i);
            if (model == null || !isModelActive(model)) continue;
            String id = model.optString("_id", "");
            if (id.isEmpty()) continue;
            modelIds.add(id);
            modelOptions.add(model.optString("displayName", "Model"));
        }
        Spinner modelAssignment = new Spinner(this);
        ArrayAdapter<String> modelAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, modelOptions);
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modelAssignment.setAdapter(modelAdapter);

        commission.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        form.addView(name);
        form.addView(loginInfo);
        form.addView(username);
        form.addView(commission);
        form.addView(assignmentLabel);
        form.addView(modelAssignment);
        new AlertDialog.Builder(this).setTitle("Create chatter account").setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", (dialog, which) -> {
                    String staffName = name.getText().toString().trim();
                    if (staffName.isEmpty()) {
                        toast("Chatter name is required");
                        return;
                    }
                    JSONObject body = new JSONObject();
                    try {
                        body.put("displayName", staffName);
                        body.put("role", "CHATTER");
                        if (!username.getText().toString().trim().isEmpty()) body.put("username", username.getText().toString().trim());
                        if (!commission.getText().toString().trim().isEmpty()) body.put("commissionRate",
                                Double.parseDouble(commission.getText().toString().trim()));
                    } catch (JSONException | NumberFormatException error) {
                        toast("Enter valid staff details");
                        return;
                    }
                    api.post("/api/v1/admin/chatters", body, callback(result -> {
                        JSONObject chatter = result.optJSONObject("chatter");
                        String password = result.optString("temporaryPassword");
                        String chatterEmail = chatter == null ? "" : chatter.optString("email", "");
                        if (chatterEmail.isEmpty() || password.isEmpty()) {
                            toast("Account may have been created, but login details were not returned. "
                                    + "Check Chatters and reset the password if needed.");
                            loadPage("Chatters");
                            return;
                        }
                        String chatterId = chatter.optString("id", "");
                        int selectedModel = modelAssignment.getSelectedItemPosition();
                        String modelId = selectedModel >= 0 && selectedModel < modelIds.size()
                                ? modelIds.get(selectedModel) : "";
                        String modelName = selectedModel >= 0 && selectedModel < modelOptions.size()
                                ? modelOptions.get(selectedModel) : "";
                        if (modelId.isEmpty()) {
                            showCreatedChatter(chatterEmail, password, "", null);
                            loadPage("Chatters");
                            return;
                        }
                        if (chatterId.isEmpty()) {
                            showCreatedChatter(chatterEmail, password, modelName,
                                    "The server did not return the new chatter ID.");
                            loadPage("Chatters");
                            return;
                        }
                        JSONObject assignment = new JSONObject();
                        try {
                            assignment.put("modelAccountId", modelId);
                            assignment.put("workerIds", new JSONArray().put(chatterId));
                        } catch (JSONException error) {
                            showCreatedChatter(chatterEmail, password, modelName,
                                    "Could not prepare the assignment request.");
                            loadPage("Chatters");
                            return;
                        }
                        api.post("/api/v1/admin/assignments", assignment,
                                callback(assigned -> {
                                    showCreatedChatter(chatterEmail, password, modelName, null);
                                    loadPage("Chatters");
                                }, error -> {
                                    showCreatedChatter(chatterEmail, password, modelName, error);
                                    loadPage("Chatters");
                                }));
                    }));
                }).show();
    }

    private void showCreatedChatter(
            String email, String password, String assignedModel, String assignmentError) {
        StringBuilder message = new StringBuilder();
        message.append("Email: ").append(email)
                .append("\nTemporary password: ").append(password);
        if (assignmentError != null) {
            message.append("\n\nAccount created, but automatic assignment to ")
                    .append(assignedModel)
                    .append(" failed: ").append(assignmentError)
                    .append("\nYou can assign this chatter later.");
        } else if (!assignedModel.isEmpty()) {
            message.append("\nAssigned to: ").append(assignedModel);
        } else {
            message.append("\nNot assigned to a model yet.");
        }
        message.append("\n\nSave these login details now; the password will not be shown again.");
        new AlertDialog.Builder(this)
                .setTitle("Chatter account created")
                .setMessage(message.toString())
                .setPositiveButton("Done", null)
                .show();
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
        new AlertDialog.Builder(this).setTitle("Choose model")
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
        new AlertDialog.Builder(this).setTitle("Select chatter(s)")
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
                        loadPage("Assignments");
                    }));
                }).show();
    }

    private void resetPassword(String id) {
        api.post("/api/v1/admin/chatters/" + id + "/password-reset", new JSONObject(), callback(result ->
                new AlertDialog.Builder(this)
                        .setTitle("Temporary password")
                        .setMessage(result.optString("temporaryPassword") + "\n\nShare securely. The chatter must change it.")
                        .setPositiveButton("Done", null)
                        .show()));
    }

    private void changeStaffStatus(String id, boolean suspended) {
        JSONObject body = new JSONObject();
        try { body.put("suspended", suspended); } catch (JSONException ignored) { return; }
        api.patch("/api/v1/admin/chatters/" + id + "/status", body, callback(result -> {
            toast(suspended ? "Staff suspended" : "Staff reactivated");
            loadPage("Chatters");
        }));
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

    private void addMetric(String name, String value, String detail) {
        LinearLayout tile = card();
        tile.setOrientation(LinearLayout.HORIZONTAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView heading = new TextView(this);
        heading.setText(name);
        heading.setTextSize(13);
        heading.setTextColor(getResources().getColor(R.color.lovanta_gray));
        TextView number = new TextView(this);
        number.setText(value);
        number.setTextSize(24);
        number.setTypeface(null, android.graphics.Typeface.BOLD);
        number.setTextColor(getResources().getColor(R.color.lovanta_dark));
        TextView sub = new TextView(this);
        sub.setText(detail);
        sub.setTextSize(12);
        sub.setTextColor(getResources().getColor(R.color.lovanta_gray));
        texts.addView(heading);
        texts.addView(number);
        texts.addView(sub);
        tile.addView(texts);
        content.addView(tile);
    }

    private void addSectionTitle(String text) {
        TextView heading = new TextView(this);
        heading.setText(text);
        heading.setTextSize(17);
        heading.setTextColor(getResources().getColor(R.color.lovanta_dark));
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setPadding(dp(2), dp(5), 0, dp(8));
        content.addView(heading);
    }

    private void addText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(getResources().getColor(R.color.lovanta_gray));
        view.setPadding(dp(4), dp(8), dp(4), dp(8));
        content.addView(view);
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
        return field;
    }

    private Button smallButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(11);
        button.setAllCaps(false);
        button.setMinHeight(dp(38));
        button.setPadding(dp(4), 0, dp(4), 0);
        return button;
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

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
