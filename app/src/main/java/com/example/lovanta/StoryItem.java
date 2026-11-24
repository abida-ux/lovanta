package com.example.lovanta;

public class StoryItem {
    private final String name;
    private final int avatarResId;
    private final String bio;
    private final int subscriberCount;
    private final int ppvUnlockCount;

    public StoryItem(String name, int avatarResId, String bio, int subscriberCount, int ppvUnlockCount) {
        this.name = name;
        this.avatarResId = avatarResId;
        this.bio = bio;
        this.subscriberCount = subscriberCount;
        this.ppvUnlockCount = ppvUnlockCount;
    }

    public String getName() {
        return name;
    }

    public int getAvatarResId() {
        return avatarResId;
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
