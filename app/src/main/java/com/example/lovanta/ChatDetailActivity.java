package com.example.lovanta;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

public class ChatDetailActivity extends AppCompatActivity {

    public static final String EXTRA_NAME = "extra_name";
    public static final String EXTRA_AVATAR = "extra_avatar";
    public static final String EXTRA_CONVERSATION_ID = "extra_conversation_id";

    private RecyclerView rvMessages;
    private EditText etMessageInput;
    private ImageButton btnSend, btnAttach, btnVideoCall, btnVoiceCall, btnMore;
    private ImageView btnBack, ivUserAvatar;
    private TextView tvUserName;
    private View header, inputBar, root;

    private List<ChatMessage> messageList;
    private MessageAdapter messageAdapter;
    private String conversationId;
    private ApiClient api;

    private int initialHeaderPaddingTop = -1;
    private int initialInputBottom = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_detail);

        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(true);

        String rawName = getIntent().getStringExtra(EXTRA_NAME);
        final String chatName = rawName != null ? rawName : "Conversation";
        conversationId = getIntent().getStringExtra(EXTRA_CONVERSATION_ID);
        api = ApiClient.get(this);
        int avatarRes = getIntent().getIntExtra(EXTRA_AVATAR, R.drawable.ic_person);

        root = findViewById(R.id.root);
        header = findViewById(R.id.header);
        inputBar = findViewById(R.id.inputBar);
        rvMessages = findViewById(R.id.rvMessages);
        etMessageInput = findViewById(R.id.etMessageInput);
        btnSend = findViewById(R.id.btnSend);
        btnAttach = findViewById(R.id.btnAttach);
        btnBack = findViewById(R.id.btnBack);
        ivUserAvatar = findViewById(R.id.ivUserAvatar);
        btnVideoCall = findViewById(R.id.btnVideoCall);
        btnVoiceCall = findViewById(R.id.btnVoiceCall);
        btnMore = findViewById(R.id.btnMore);
        tvUserName = findViewById(R.id.tvUserName);

        tvUserName.setText(chatName);
        ivUserAvatar.setImageResource(avatarRes);

        // Capture initial paddings once before setting window insets listener
        if (header != null) {
            initialHeaderPaddingTop = header.getPaddingTop();
        }
        if (inputBar != null) {
            initialInputBottom = inputBar.getPaddingBottom();
        }

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());

            if (header != null && initialHeaderPaddingTop != -1) {
                header.setPadding(
                        header.getPaddingLeft(),
                        initialHeaderPaddingTop + systemBars.top,
                        header.getPaddingRight(),
                        header.getPaddingBottom()
                );
            }

            if (inputBar != null && initialInputBottom != -1) {
                int bottomInset = Math.max(systemBars.bottom, ime.bottom);
                inputBar.setPadding(
                        inputBar.getPaddingLeft(),
                        inputBar.getPaddingTop(),
                        inputBar.getPaddingRight(),
                        initialInputBottom + bottomInset
                );
            }

            // Scroll to bottom when keyboard opens
            if (ime.bottom > 0 && messageList != null && !messageList.isEmpty()) {
                rvMessages.post(() -> rvMessages.scrollToPosition(messageList.size() - 1));
            }

            return WindowInsetsCompat.CONSUMED;
        });

        btnBack.setOnClickListener(v -> finish());
        btnVideoCall.setVisibility(View.GONE);
        btnVoiceCall.setVisibility(View.GONE);
        btnAttach.setVisibility(View.GONE);
        btnMore.setVisibility(View.GONE);

        messageList = new ArrayList<>();
        messageAdapter = new MessageAdapter(messageList);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setAdapter(messageAdapter);
        if (conversationId == null || conversationId.trim().isEmpty()) {
            Toast.makeText(this, "Conversation is unavailable", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        loadMessages();

        btnSend.setOnClickListener(v -> {
            String text = etMessageInput.getText().toString().trim();
            if (!text.isEmpty()) sendMessage(text);
        });
    }

    private void loadMessages() {
        api.getMessages(conversationId, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject result) {
                JSONArray messages = result.optJSONArray("messages");
                messageList.clear();
                if (messages != null) {
                    for (int i = 0; i < messages.length(); i++) {
                        JSONObject message = messages.optJSONObject(i);
                        if (message == null) continue;
                        boolean mine = api.getWorkerId().equals(message.optString("senderWorkerId"));
                        messageList.add(new ChatMessage(
                                message.optString("text", ""),
                                formatTimestamp(message.optString("sentAt", "")),
                                mine
                        ));
                    }
                }
                messageAdapter.notifyDataSetChanged();
                if (!messageList.isEmpty()) rvMessages.scrollToPosition(messageList.size() - 1);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ChatDetailActivity.this, message, Toast.LENGTH_LONG).show();
                if (!api.hasSession()) {
                    startActivity(new android.content.Intent(ChatDetailActivity.this, LoginActivity.class));
                    finish();
                }
            }
        });
    }

    private void sendMessage(String text) {
        btnSend.setEnabled(false);
        api.sendMessage(conversationId, text, new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject result) {
                btnSend.setEnabled(true);
                JSONObject message = result.optJSONObject("message");
                if (message == null) {
                    Toast.makeText(ChatDetailActivity.this, "Message was not returned by the server", Toast.LENGTH_LONG).show();
                    return;
                }
                messageList.add(new ChatMessage(
                        message.optString("text", text),
                        formatTimestamp(message.optString("sentAt", "")),
                        true
                ));
                messageAdapter.notifyItemInserted(messageList.size() - 1);
                rvMessages.scrollToPosition(messageList.size() - 1);
                etMessageInput.setText("");
            }

            @Override
            public void onError(String message) {
                btnSend.setEnabled(true);
                Toast.makeText(ChatDetailActivity.this, message, Toast.LENGTH_LONG).show();
                if (!api.hasSession()) {
                    startActivity(new android.content.Intent(ChatDetailActivity.this, LoginActivity.class));
                    finish();
                }
            }
        });
    }

    private String formatTimestamp(String value) {
        if (value.isEmpty()) return "";
        try {
            Date date = Date.from(java.time.Instant.parse(value));
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(date);
        } catch (RuntimeException error) {
            return value;
        }
    }

    private static class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_SENT = 1;
        private static final int TYPE_RECEIVED = 2;
        private final List<ChatMessage> messages;

        public MessageAdapter(List<ChatMessage> messages) {
            this.messages = messages;
        }

        @Override
        public int getItemViewType(int position) {
            return messages.get(position).isSentByMe() ? TYPE_SENT : TYPE_RECEIVED;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_SENT) {
                View v = inflater.inflate(R.layout.item_message_sent, parent, false);
                return new SentViewHolder(v);
            } else {
                View v = inflater.inflate(R.layout.item_message_received, parent, false);
                return new ReceivedViewHolder(v);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ChatMessage msg = messages.get(position);
            if (holder instanceof SentViewHolder) {
                ((SentViewHolder) holder).tvMessage.setText(msg.getText());
                ((SentViewHolder) holder).tvTime.setText(msg.getTimestamp());
            } else if (holder instanceof ReceivedViewHolder) {
                ((ReceivedViewHolder) holder).tvMessage.setText(msg.getText());
                ((ReceivedViewHolder) holder).tvTime.setText(msg.getTimestamp());
            }
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        static class SentViewHolder extends RecyclerView.ViewHolder {
            TextView tvMessage, tvTime;
            SentViewHolder(View itemView) {
                super(itemView);
                tvMessage = itemView.findViewById(R.id.tvMessage);
                tvTime = itemView.findViewById(R.id.tvTime);
            }
        }

        static class ReceivedViewHolder extends RecyclerView.ViewHolder {
            TextView tvMessage, tvTime;
            ReceivedViewHolder(View itemView) {
                super(itemView);
                tvMessage = itemView.findViewById(R.id.tvMessage);
                tvTime = itemView.findViewById(R.id.tvTime);
            }
        }
    }
}
