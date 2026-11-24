package com.example.lovanta;

public class VipClient {
    private String id;
    private String name;
    private String modelName;
    private int avatarResId;
    private boolean isActive;
    private boolean isHighSpender;
    private double totalSpent;
    private int purchasesCount;
    private String lastPurchaseDate;
    private String lastInteraction;

    public VipClient(String id, String name, String modelName, int avatarResId, boolean isActive, boolean isHighSpender, double totalSpent, int purchasesCount, String lastPurchaseDate, String lastInteraction) {
        this.id = id;
        this.name = name;
        this.modelName = modelName;
        this.avatarResId = avatarResId;
        this.isActive = isActive;
        this.isHighSpender = isHighSpender;
        this.totalSpent = totalSpent;
        this.purchasesCount = purchasesCount;
        this.lastPurchaseDate = lastPurchaseDate;
        this.lastInteraction = lastInteraction;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getModelName() { return modelName; }
    public int getAvatarResId() { return avatarResId; }
    public boolean isActive() { return isActive; }
    public boolean isHighSpender() { return isHighSpender; }
    public double getTotalSpent() { return totalSpent; }
    public int getPurchasesCount() { return purchasesCount; }
    public String getLastPurchaseDate() { return lastPurchaseDate; }
    public String getLastInteraction() { return lastInteraction; }
}
