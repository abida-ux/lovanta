package com.example.lovanta;

public class ChatConversation {
    private String id;
    private String modelAccountId;
    private String name;
    private int avatarResId;
    private String lastMessage;
    private String timestamp;
    private int unreadCount;
    private boolean isStarred;
    private boolean isNewMatch;

    public ChatConversation(String id, String name, int avatarResId, String lastMessage, String timestamp, int unreadCount, boolean isStarred, boolean isNewMatch) {
        this(id, "", name, avatarResId, lastMessage, timestamp, unreadCount, isStarred, isNewMatch);
    }

    public ChatConversation(String id, String modelAccountId, String name, int avatarResId, String lastMessage,
                            String timestamp, int unreadCount, boolean isStarred, boolean isNewMatch) {
        this.id = id;
        this.modelAccountId = modelAccountId;
        this.name = name;
        this.avatarResId = avatarResId;
        this.lastMessage = lastMessage;
        this.timestamp = timestamp;
        this.unreadCount = unreadCount;
        this.isStarred = isStarred;
        this.isNewMatch = isNewMatch;
    }

    public String getId() { return id; }
    public String getModelAccountId() { return modelAccountId; }
    public String getName() { return name; }
    public int getAvatarResId() { return avatarResId; }
    public String getLastMessage() { return lastMessage; }
    public String getTimestamp() { return timestamp; }
    public int getUnreadCount() { return unreadCount; }
    public boolean isStarred() { return isStarred; }
    public boolean isNewMatch() { return isNewMatch; }
}
