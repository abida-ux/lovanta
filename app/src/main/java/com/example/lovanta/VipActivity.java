package com.example.lovanta;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class VipActivity extends AppCompatActivity {

    private RecyclerView rvVipModels;
    private RecyclerView rvVipClients;

    private List<VipClient> vipList;
    private VipAdapter vipAdapter;
    private List<StoryItem> modelList;
    private StoryAdapter modelAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vip);
        ScreenInsets.applyToMainRoot(this);

        rvVipModels = findViewById(R.id.rvVipModels);
        rvVipClients = findViewById(R.id.rvVipClients);
        setupModels();
        setupVipData();
        loadWorkspace();
        BottomNavigation.bind(this, R.id.navVip);
    }

    private void setupModels() {
        modelList = new ArrayList<>();
        modelAdapter = new StoryAdapter(modelList, model -> {
            Intent intent = new Intent(this, ModelProfileActivity.class);
            intent.putExtra(ModelProfileActivity.EXTRA_NAME, model.getName());
            intent.putExtra(ModelProfileActivity.EXTRA_MODEL_ID, model.getModelId());
            intent.putExtra(ModelProfileActivity.EXTRA_AVATAR, model.getAvatarResId());
            intent.putExtra(ModelProfileActivity.EXTRA_AVATAR_URL, model.getAvatarUrl());
            intent.putExtra(ModelProfileActivity.EXTRA_BIO, model.getBio());
            startActivity(intent);
        });
        rvVipModels.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvVipModels.setAdapter(modelAdapter);
    }

    private void setupVipData() {
        vipList = new ArrayList<>();
        vipAdapter = new VipAdapter(vipList, client -> {
            Intent intent = new Intent(VipActivity.this, ChatDetailActivity.class);
            intent.putExtra(ChatDetailActivity.EXTRA_NAME, client.getName());
            intent.putExtra(ChatDetailActivity.EXTRA_AVATAR, client.getAvatarResId());
            intent.putExtra(ChatDetailActivity.EXTRA_CONVERSATION_ID, client.getId());
            startActivity(intent);
        });

        rvVipClients.setLayoutManager(new LinearLayoutManager(this));
        rvVipClients.setAdapter(vipAdapter);
    }

    private void loadWorkspace() {
        ApiClient.get(this).loadWorkspace(new ApiClient.WorkspaceCallback() {
            @Override
            public void onSuccess(JSONArray models, JSONArray conversations) {
                modelList.clear();
                for (int i = 0; i < models.length(); i++) {
                    JSONObject model = models.optJSONObject(i);
                    if (model == null) continue;
                    modelList.add(new StoryItem(model.optString("displayName", "Model"),
                            R.drawable.ic_person, model.optString("id", ""),
                            model.optString("avatarUrl", ""),
                            model.optString("description", ""), 0, 0));
                }
                modelAdapter.notifyDataSetChanged();
                vipList.clear();
                for (int i = 0; i < conversations.length(); i++) {
                    JSONObject conversation = conversations.optJSONObject(i);
                    if (conversation == null) continue;
                    vipList.add(new VipClient(
                            conversation.optString("id"),
                            conversation.optString("subscriberId", "Subscriber"),
                            conversation.optString("modelName", "Model"),
                            R.drawable.ic_person,
                            true,
                            false,
                            0,
                            0,
                            "",
                            conversation.optString("lastMessageAt", "")
                    ));
                }
                vipAdapter.notifyDataSetChanged();
                boolean empty = vipList.isEmpty();
                rvVipClients.setVisibility(empty ? View.GONE : View.VISIBLE);
                findViewById(R.id.tvVipEmpty).setVisibility(empty ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(VipActivity.this, message, Toast.LENGTH_LONG).show();
                if (!ApiClient.get(VipActivity.this).hasSession()) {
                    startActivity(new Intent(VipActivity.this, LoginActivity.class));
                    finish();
                    return;
                }
                rvVipClients.setVisibility(View.GONE);
                findViewById(R.id.tvVipEmpty).setVisibility(View.VISIBLE);
            }
        });
    }
}
