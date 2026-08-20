package com.example.project3_aadhika8_sguragai;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ChatMessageDao {

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    List<ChatMessage> getAllMessages();

    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC LIMIT :limit")
    List<ChatMessage> getRecentMessages(int limit);

    @Insert
    void insert(ChatMessage message);

    @Query("DELETE FROM chat_messages")
    void clearAll();

    @Query("SELECT COUNT(*) FROM chat_messages")
    int getMessageCount();
}
