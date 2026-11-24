package com.example.lovanta;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    private final List<ChatConversation> chats;
    private final OnChatClickListener listener;

    public interface OnChatClickListener {
        void onChatClick(ChatConversation chat);
    }

    public ChatAdapter(List<ChatConversation> chats, OnChatClickListener listener) {
        this.chats = chats;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatConversation chat = chats.get(position);
        holder.tvChatName.setText(chat.getName());
        holder.tvChatTime.setText(chat.getTimestamp());
        holder.tvChatLastMessage.setText(chat.getLastMessage());
        holder.ivChatAvatar.setImageResource(chat.getAvatarResId());
        holder.ivChatAvatar.setBackgroundResource(R.drawable.bg_chat_avatar);
        if (chat.getAvatarResId() == R.drawable.ic_person) {
            holder.ivChatAvatar.setColorFilter(
                    holder.itemView.getResources().getColor(R.color.lovanta_gray));
        } else {
            holder.ivChatAvatar.clearColorFilter();
        }

        if (chat.getUnreadCount() > 0) {
            holder.tvUnreadBadge.setVisibility(View.VISIBLE);
            holder.tvUnreadBadge.setText(String.valueOf(chat.getUnreadCount()));
        } else {
            holder.tvUnreadBadge.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> listener.onChatClick(chat));
    }

    @Override
    public int getItemCount() {
        return chats.size();
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        ImageView ivChatAvatar;
        TextView tvChatName;
        TextView tvChatTime;
        TextView tvChatLastMessage;
        TextView tvUnreadBadge;

        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            ivChatAvatar = itemView.findViewById(R.id.ivChatAvatar);
            tvChatName = itemView.findViewById(R.id.tvChatName);
            tvChatTime = itemView.findViewById(R.id.tvChatTime);
            tvChatLastMessage = itemView.findViewById(R.id.tvChatLastMessage);
            tvUnreadBadge = itemView.findViewById(R.id.tvUnreadBadge);
        }
    }
}
