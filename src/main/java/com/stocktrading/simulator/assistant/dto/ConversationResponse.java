package com.stocktrading.simulator.assistant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.stocktrading.simulator.assistant.client.model.ChatMessage;
import com.stocktrading.simulator.assistant.model.Conversation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Response DTO representing an authenticated user's conversation session history.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConversationResponse {

    private String conversationId;
    private String id;
    private String username;
    private List<ChatMessage> messages = new ArrayList<>();
    private int messageCount;
    private Instant createdAt;
    private Instant lastAccessedAt;

    public ConversationResponse() {}

    public ConversationResponse(String conversationId,
                                String username,
                                List<ChatMessage> messages,
                                int messageCount,
                                Instant createdAt,
                                Instant lastAccessedAt) {
        this.conversationId = conversationId;
        this.id = conversationId;
        this.username = username;
        this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
        this.messageCount = messageCount;
        this.createdAt = createdAt;
        this.lastAccessedAt = lastAccessedAt;
    }

    public static ConversationResponse from(Conversation conversation) {
        if (conversation == null) {
            return null;
        }
        return new ConversationResponse(
                conversation.getId(),
                conversation.getUsername(),
                conversation.getMessages(),
                conversation.getMessageCount(),
                conversation.getCreatedAt(),
                conversation.getLastAccessedAt()
        );
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
        this.id = conversationId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
        this.conversationId = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public List<ChatMessage> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
        this.messageCount = this.messages.size();
    }

    public int getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(Instant lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }
}
