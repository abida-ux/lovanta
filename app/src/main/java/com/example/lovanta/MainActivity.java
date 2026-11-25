package com.example.lovanta;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private RecyclerView rvStories, rvChats;
    private LinearLayout layoutNotificationBanner, btnRenew;
    private Button btnEnableNotif;
    private ImageView btnCloseBanner;
    private TextView chipAll, chipUnread, chipNewMatch, chipStar;
    private TextView tvEmptyChats;

    private List<StoryItem> storyList;
    private List<ChatConversation> chatList;
    private List<ChatConversation> filteredChatList;
    private ChatAdapter chatAdapter;

    private String currentFilter = "All";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ScreenInsets.applyToMainRoot(this);

        initViews();
        ((View) findViewById(R.id.tvActiveTimer).getParent()).setVisibility(View.GONE);
        updateStaffNumber();
        setupStories();
        setupChats();
        setupListeners();
        loadWorkspace();
        BottomNavigation.bind(this, R.id.navChat);
    }

    private void updateStaffNumber() {
        ApiClient api = ApiClient.get(this);
        TextView staffId = findViewById(R.id.tvWorkerId);
        String staffNumber = api.getStaffNumber();
        staffId.setText("Staff ID: " + (staffNumber.isEmpty() ? "—" : staffNumber));
        if (!staffNumber.isEmpty()) return;

        api.get("/api/v1/auth/me", new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject result) {
                JSONObject worker = result.optJSONObject("worker");
                if (worker == null) return;
                String resolvedNumber = worker.optString("staffNumber", "");
                api.saveStaffNumber(resolvedNumber);
                staffId.setText("Staff ID: " + (resolvedNumber.isEmpty() ? "—" : resolvedNumber));
            }

            @Override
            public void onError(String message) {
                if (!api.hasSession() && !isFinishing()) {
                    startActivity(new Intent(MainActivity.this, LoginActivity.class));
                    finish();
                    overridePendingTransition(0, 0);
                }
            }
        });
    }

    private void initViews() {
        rvStories = findViewById(R.id.rvStories);
        rvChats = findViewById(R.id.rvChats);
        layoutNotificationBanner = findViewById(R.id.layoutNotificationBanner);
        btnRenew = findViewById(R.id.btnRenew);
        btnEnableNotif = findViewById(R.id.btnEnableNotif);
        btnCloseBanner = findViewById(R.id.btnCloseBanner);

        chipAll = findViewById(R.id.chipAll);
        chipUnread = findViewById(R.id.chipUnread);
        chipNewMatch = findViewById(R.id.chipNewMatch);
        chipStar = findViewById(R.id.chipStar);
        tvEmptyChats = findViewById(R.id.tvEmptyChats);

    }

    private void setupStories() {
        storyList = new ArrayList<>();
        StoryAdapter storyAdapter = new StoryAdapter(storyList, story -> {
            Intent intent = new Intent(MainActivity.this, ModelProfileActivity.class);
            intent.putExtra(ModelProfileActivity.EXTRA_NAME, story.getName());
            intent.putExtra(ModelProfileActivity.EXTRA_AVATAR, story.getAvatarResId());
            intent.putExtra(ModelProfileActivity.EXTRA_BIO, story.getBio());
            startActivity(intent);
        });

        rvStories.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvStories.setAdapter(storyAdapter);
    }

    private void setupChats() {
        chatList = new ArrayList<>();
        filteredChatList = new ArrayList<>(chatList);
        chatAdapter = new ChatAdapter(filteredChatList, chat -> {
            Intent intent = new Intent(MainActivity.this, ChatDetailActivity.class);
            intent.putExtra(ChatDetailActivity.EXTRA_NAME, chat.getName());
            intent.putExtra(ChatDetailActivity.EXTRA_AVATAR, chat.getAvatarResId());
            intent.putExtra(ChatDetailActivity.EXTRA_CONVERSATION_ID, chat.getId());
            startActivity(intent);
        });

        rvChats.setLayoutManager(new LinearLayoutManager(this));
        rvChats.setAdapter(chatAdapter);
    }

    private void setupListeners() {
        chipAll.setOnClickListener(v -> filterChats("All"));
        chipUnread.setOnClickListener(v -> filterChats("Unread"));
        chipNewMatch.setOnClickListener(v -> filterChats("New match"));
        chipStar.setOnClickListener(v -> filterChats("Star"));
        layoutNotificationBanner.setVisibility(View.VISIBLE);
    }

    private void filterChats(String filter) {
        currentFilter = filter;
        updateChipStyles();
        filteredChatList.clear();
        filteredChatList.addAll(chatList);
        chatAdapter.notifyDataSetChanged();
    }

    private void updateChipStyles() {
        chipAll.setBackgroundResource(currentFilter.equals("All") ? R.drawable.bg_pill_active : R.drawable.bg_pill_inactive);
        chipAll.setTextColor(getResources().getColor(currentFilter.equals("All") ? R.color.white : R.color.lovanta_dark));

        chipUnread.setBackgroundResource(currentFilter.equals("Unread") ? R.drawable.bg_pill_active : R.drawable.bg_pill_inactive);
        chipUnread.setTextColor(getResources().getColor(currentFilter.equals("Unread") ? R.color.white : R.color.lovanta_dark));

        chipNewMatch.setBackgroundResource(currentFilter.equals("New match") ? R.drawable.bg_pill_active : R.drawable.bg_pill_inactive);
        chipNewMatch.setTextColor(getResources().getColor(currentFilter.equals("New match") ? R.color.white : R.color.lovanta_dark));

        chipStar.setBackgroundResource(currentFilter.equals("Star") ? R.drawable.bg_pill_active : R.drawable.bg_pill_inactive);
        chipStar.setTextColor(getResources().getColor(currentFilter.equals("Star") ? R.color.white : R.color.lovanta_dark));
    }

    private void loadWorkspace() {
        ApiClient.get(this).loadWorkspace(new ApiClient.WorkspaceCallback() {
            @Override
            public void onSuccess(JSONArray models, JSONArray conversations) {
                storyList.clear();
                for (int i = 0; i < models.length(); i++) {
                    JSONObject model = models.optJSONObject(i);
                    if (model == null) continue;
                    storyList.add(new StoryItem(
                            model.optString("displayName", "Model"),
                            R.drawable.ic_person,
                            model.optString("description", ""),
                            0,
                            0
                    ));
                }
                rvStories.getAdapter().notifyDataSetChanged();

                chatList.clear();
                for (int i = 0; i < conversations.length(); i++) {
                    JSONObject conversation = conversations.optJSONObject(i);
                    if (conversation == null) continue;
                    String subscriber = conversation.optString("subscriberId", "Subscriber");
                    String modelName = conversation.optString("modelName", "");
                    String displayName = modelName.isEmpty() ? subscriber : subscriber + " · " + modelName;
                    chatList.add(new ChatConversation(
                            conversation.optString("id"),
                            conversation.optString("modelAccountId"),
                            displayName,
                            R.drawable.ic_person,
                            conversation.optString("lastMessageText", ""),
                            formatTime(conversation.optString("lastMessageAt", "")),
                            0,
                            false,
                            false
                    ));
                }
                filteredChatList.clear();
                filteredChatList.addAll(chatList);
                chatAdapter.notifyDataSetChanged();
                boolean empty = chatList.isEmpty();
                rvChats.setVisibility(empty ? View.GONE : View.VISIBLE);
                tvEmptyChats.setVisibility(empty ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                if (!ApiClient.get(MainActivity.this).hasSession()) {
                    startActivity(new Intent(MainActivity.this, LoginActivity.class));
                    finish();
                    return;
                }
                rvChats.setVisibility(View.GONE);
                tvEmptyChats.setText("Could not load inbox. Check your connection and try again.");
                tvEmptyChats.setVisibility(View.VISIBLE);
                tvEmptyChats.setOnClickListener(v -> loadWorkspace());
            }
        });
    }

    private String formatTime(String value) {
        if (value.isEmpty()) return "";
        try {
            java.time.Instant instant = java.time.Instant.parse(value);
            return new java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date.from(instant));
        } catch (RuntimeException error) {
            return value;
        }
    }
}
