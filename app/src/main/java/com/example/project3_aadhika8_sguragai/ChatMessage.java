package com.example.project3_aadhika8_sguragai;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Entity for storing chat history with the AI financial assistant.
 */
@Entity(tableName = "chat_messages")
public class ChatMessage {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String content;

    public boolean isUser; // true if from user, false if from AI

    public long timestamp;

    public ChatMessage() {
        this.timestamp = System.currentTimeMillis();
    }

    public ChatMessage(String content, boolean isUser) {
        this.content = content;
        this.isUser = isUser;
        this.timestamp = System.currentTimeMillis();
    }
}
