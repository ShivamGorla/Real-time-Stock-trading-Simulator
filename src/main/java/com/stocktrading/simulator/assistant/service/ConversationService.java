package com.stocktrading.simulator.assistant.service;

import com.stocktrading.simulator.assistant.model.Conversation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, in-memory conversation memory service.
 *
 * <p><strong>Key Design & Safety Features:</strong></p>
 * <ul>
 *   <li>Associates every conversation strictly with its authenticated username.</li>
 *   <li>Enforces user isolation: cross-user conversation access throws {@link SecurityException}.</li>
 *   <li>Bounded overall memory: applies LRU eviction when reaching {@link #MAX_CONVERSATIONS} (1,000 sessions).</li>
 *   <li>Transient memory: conversations reside purely in memory and are discarded upon application restart.</li>
 * </ul>
 */
@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);
    public static final int MAX_CONVERSATIONS = 1000;

    private final Map<String, Conversation> conversations = new ConcurrentHashMap<>();

    /**
     * Retrieves an existing conversation or creates a new one if conversationId is blank.
     *
     * @param conversationId optional conversation UUID
     * @param username       authenticated username
     * @return the resolved or newly initialized Conversation
     * @throws SecurityException        if conversation exists but belongs to a different user
     * @throws IllegalArgumentException if specified conversationId does not exist
     */
    public synchronized Conversation getOrCreateConversation(String conversationId, String username) {
        if (conversationId == null || conversationId.isBlank()) {
            return createConversation(username);
        }
        return getConversation(conversationId, username);
    }

    /**
     * Retrieves an existing conversation belonging to the authenticated user.
     *
     * @param conversationId conversation UUID
     * @param username       authenticated username
     * @return the Conversation session
     * @throws IllegalArgumentException if conversationId or username is blank, or conversation is not found
     * @throws SecurityException        if conversation exists but belongs to another user
     */
    public synchronized Conversation getConversation(String conversationId, String username) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("Conversation ID cannot be null or blank");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be null or blank");
        }

        Conversation conversation = conversations.get(conversationId);
        if (conversation == null) {
            throw new IllegalArgumentException("Conversation not found: " + conversationId);
        }

        if (!conversation.getUsername().equals(username)) {
            log.warn("Access denied: User '{}' attempted to access conversation '{}' owned by '{}'",
                    username, conversationId, conversation.getUsername());
            throw new SecurityException("Access denied: Conversation '" + conversationId + "' does not belong to user '" + username + "'");
        }

        conversation.touch();
        return conversation;
    }

    /**
     * Creates and stores a new conversation session for the authenticated user.
     *
     * @param username authenticated username
     * @return the new Conversation
     */
    public synchronized Conversation createConversation(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be null or blank");
        }

        evictOldestIfAtCapacity();

        String id = UUID.randomUUID().toString();
        Conversation conversation = new Conversation(id, username);
        conversations.put(id, conversation);
        log.debug("Created new conversation session '{}' for user '{}'", id, username);
        return conversation;
    }

    /**
     * Checks whether a conversation exists.
     */
    public boolean hasConversation(String conversationId) {
        return conversationId != null && conversations.containsKey(conversationId);
    }

    /**
     * Returns the total active in-memory conversation count.
     */
    public int getConversationCount() {
        return conversations.size();
    }

    /**
     * Clears all in-memory conversations.
     */
    public synchronized void clear() {
        conversations.clear();
    }

    private void evictOldestIfAtCapacity() {
        if (conversations.size() >= MAX_CONVERSATIONS) {
            String oldestId = null;
            Instant oldestTimestamp = Instant.MAX;

            for (Map.Entry<String, Conversation> entry : conversations.entrySet()) {
                Instant accessed = entry.getValue().getLastAccessedAt();
                if (accessed.isBefore(oldestTimestamp)) {
                    oldestTimestamp = accessed;
                    oldestId = entry.getKey();
                }
            }

            if (oldestId != null) {
                conversations.remove(oldestId);
                log.info("Evicted oldest conversation '{}' to respect capacity bound ({})", oldestId, MAX_CONVERSATIONS);
            }
        }
    }
}
