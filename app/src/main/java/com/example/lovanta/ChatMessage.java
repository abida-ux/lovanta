package com.example.lovanta;

public class ChatMessage {
    private String text;
    private String timestamp;
    private boolean isSentByMe;

    public ChatMessage(String text, String timestamp, boolean isSentByMe) {
        this.text = text;
        this.timestamp = timestamp;
        this.isSentByMe = isSentByMe;
    }

    public String getText() { return text; }
    public String getTimestamp() { return timestamp; }
    public boolean isSentByMe() { return isSentByMe; }
}
