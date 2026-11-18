package com.stocktrading.simulator.assistant.model;

import com.stocktrading.simulator.assistant.client.model.ChatMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Thread-safe, in-memory representation of an individual assistant conversation session.
 * Enforces authenticated user ownership and bounded message history (max 20 messages).
 *
 * <p><strong>Note:</strong> In-memory conversation history is not persistent and is discarded on application restart.</p>
 */
public class Conversation {

    public static final int MAX_MESSAGES = 20;
    public static final int MAX_CONTEXT_CHARS = 32_000;

    private final String id;
    private final String username;
    private final List<ChatMessage> messages = new ArrayList<>();
    private final Instant createdAt;
    private volatile Instant lastAccessedAt;

    public Conversation(String id, String username) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Conversation ID cannot be null or blank");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be null or blank");
        }
        this.id = id;
        this.username = username;
        this.createdAt = Instant.now();
        this.lastAccessedAt = this.createdAt;
    }

    public synchronized void addMessage(ChatMessage message) {
        if (message != null) {
            this.messages.add(message);
            this.lastAccessedAt = Instant.now();
            trimHistoryIfNeeded();
        }
    }

    public synchronized void addMessages(List<ChatMessage> newMessages) {
        if (newMessages != null && !newMessages.isEmpty()) {
            this.messages.addAll(newMessages);
            this.lastAccessedAt = Instant.now();
            trimHistoryIfNeeded();
        }
    }

    public synchronized List<ChatMessage> getMessages() {
        return Collections.unmodifiableList(new ArrayList<>(this.messages));
    }

    public synchronized int getMessageCount() {
        return this.messages.size();
    }

    public synchronized void touch() {
        this.lastAccessedAt = Instant.now();
    }

    public synchronized int getTotalContextChars() {
        int total = 0;
        for (ChatMessage m : this.messages) {
            total += messageCharLength(m);
        }
        return total;
    }

    private int messageCharLength(ChatMessage m) {
        if (m == null) return 0;
        int len = m.getContent() != null ? m.getContent().length() : 0;
        if (m.getToolCalls() != null) {
            for (var tc : m.getToolCalls()) {
                if (tc != null && tc.getFunction() != null) {
                    if (tc.getFunction().getName() != null) len += tc.getFunction().getName().length();
                    if (tc.getFunction().getArguments() != null) len += tc.getFunction().getArguments().length();
                }
            }
        }
        return len;
    }

    private boolean hasToolCalls(ChatMessage message) {
        return message != null
                && "assistant".equalsIgnoreCase(message.getRole())
                && message.getToolCalls() != null
                && !message.getToolCalls().isEmpty();
    }

    /**
     * Returns the size of the atomic message block starting at {@code start}.
     * An assistant message carrying {@code tool_calls} forms an atomic unit together with all
     * immediately following {@code tool} response messages so trimming never leaves partial sequences.
     */
    private int atomicBlockLength(int start) {
        if (start >= this.messages.size()) {
            return 0;
        }
        int len = 1;
        if (hasToolCalls(this.messages.get(start))) {
            while (start + len < this.messages.size()
                    && "tool".equalsIgnoreCase(this.messages.get(start + len).getRole())) {
                len++;
            }
        }
        return len;
    }

    private void removeLeadingBlock() {
        int blockLen = atomicBlockLength(0);
        for (int i = 0; i < blockLen && !this.messages.isEmpty(); i++) {
            this.messages.remove(0);
        }
    }

    private void trimHistoryIfNeeded() {
        // 1. Remove any orphaned leading tool responses
        while (!this.messages.isEmpty() && "tool".equalsIgnoreCase(this.messages.get(0).getRole())) {
            this.messages.remove(0);
        }

        // 2. Evict oldest atomic blocks while exceeding MAX_MESSAGES or MAX_CONTEXT_CHARS,
        //    always keeping at least the final atomic block intact
        while (this.messages.size() > MAX_MESSAGES && atomicBlockLength(0) < this.messages.size()) {
            removeLeadingBlock();
        }
        while (getTotalContextChars() > MAX_CONTEXT_CHARS && atomicBlockLength(0) < this.messages.size()) {
            removeLeadingBlock();
        }

        // 3. Re-clean any orphaned leading tool messages
        while (!this.messages.isEmpty() && "tool".equalsIgnoreCase(this.messages.get(0).getRole())) {
            this.messages.remove(0);
        }

        // 4. Integrity guard: ensure every retained assistant(tool_calls) has all matching tool results
        int i = 0;
        while (i < this.messages.size()) {
            ChatMessage msg = this.messages.get(i);
            if (hasToolCalls(msg)) {
                int blockLen = atomicBlockLength(i);
                Set<String> expectedIds = new HashSet<>();
                for (var tc : msg.getToolCalls()) {
                    if (tc != null && tc.getId() != null) {
                        expectedIds.add(tc.getId());
                    }
                }
                Set<String> presentIds = new HashSet<>();
                for (int j = i + 1; j < i + blockLen; j++) {
                    String id = this.messages.get(j).getToolCallId();
                    if (id != null) {
                        presentIds.add(id);
                    }
                }
                if (!presentIds.containsAll(expectedIds)) {
                    for (int k = 0; k < blockLen; k++) {
                        this.messages.remove(i);
                    }
                    continue;
                }
                i += blockLen;
            } else {
                i++;
            }
        }
    }

    public String getId() {
        return id;
    }

    public String getConversationId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }
}
