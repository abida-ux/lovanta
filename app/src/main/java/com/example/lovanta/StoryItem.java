package com.example.lovanta;

public class StoryItem {
    private final String name;
    private final String modelId;
    private final int avatarResId;
    private final String avatarUrl;
    private final String bio;
    private final int subscriberCount;
    private final int ppvUnlockCount;

    public StoryItem(String name, int avatarResId, String bio, int subscriberCount, int ppvUnlockCount) {
        this(name, avatarResId, "", bio, subscriberCount, ppvUnlockCount);
    }

    public StoryItem(
            String name,
            int avatarResId,
            String avatarUrl,
            String bio,
            int subscriberCount,
            int ppvUnlockCount) {
        this(name, avatarResId, "", avatarUrl, bio, subscriberCount, ppvUnlockCount);
    }

    public StoryItem(
            String name,
            int avatarResId,
            String modelId,
            String avatarUrl,
            String bio,
            int subscriberCount,
            int ppvUnlockCount) {
        this.name = name;
        this.modelId = modelId == null ? "" : modelId;
        this.avatarResId = avatarResId;
        this.avatarUrl = avatarUrl == null ? "" : avatarUrl;
        this.bio = bio;
        this.subscriberCount = subscriberCount;
        this.ppvUnlockCount = ppvUnlockCount;
    }

    public String getName() {
        return name;
    }

    public String getModelId() {
        return modelId;
    }

    public int getAvatarResId() {
        return avatarResId;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getBio() {
        return bio;
    }

    public int getSubscriberCount() {
        return subscriberCount;
    }

    public int getPpvUnlockCount() {
        return ppvUnlockCount;
    }
}
