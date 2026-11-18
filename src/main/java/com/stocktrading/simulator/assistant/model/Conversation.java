package com.stocktrading.simulator.assistant.model;

import com.stocktrading.simulator.assistant.client.model.ChatMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe, in-memory representation of an individual assistant conversation session.
 * Enforces authenticated user ownership and bounded message history (max 20 messages).
 *
 * <p><strong>Note:</strong> In-memory conversation history is not persistent and is discarded on application restart.</p>
 */
public class Conversation {

    public static final int MAX_MESSAGES = 20;
    public static final int MAX_CONTEXT_CHARS = 32_000;
    private static final String TRUNCATION_SUFFIX = "... [truncated]";

    private final String id;
    private final String username;
    private final List<ChatMessage> messages = new ArrayList<>();
    private final ReentrantLock turnLock = new ReentrantLock(true);
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

    private void removeOrphanedLeadingTools() {
        while (!this.messages.isEmpty() && "tool".equalsIgnoreCase(this.messages.get(0).getRole())) {
            this.messages.remove(0);
        }
    }

    private void removeBrokenToolCallBlocks() {
        int i = 0;
        while (i < this.messages.size()) {
            ChatMessage msg = this.messages.get(i);
            if (msg == null) {
                this.messages.remove(i);
                continue;
            }
            if ("tool".equalsIgnoreCase(msg.getRole())) {
                // Any tool message reached outside a valid assistant(tool_calls) block is orphaned
                this.messages.remove(i);
                continue;
            }
            if (hasToolCalls(msg)) {
                int blockLen = atomicBlockLength(i);
                Set<String> expectedIds = new HashSet<>();
                boolean validExpected = true;
                for (var tc : msg.getToolCalls()) {
                    if (tc == null || tc.getId() == null || tc.getId().isBlank() || !expectedIds.add(tc.getId())) {
                        validExpected = false;
                        break;
                    }
                }
                Set<String> presentIds = new HashSet<>();
                boolean validPresent = validExpected && (blockLen - 1 == expectedIds.size());
                if (validPresent) {
                    for (int j = i + 1; j < i + blockLen; j++) {
                        String id = this.messages.get(j).getToolCallId();
                        if (id == null || id.isBlank() || !expectedIds.contains(id) || !presentIds.add(id)) {
                            validPresent = false;
                            break;
                        }
                    }
                }
                if (!validPresent || !presentIds.equals(expectedIds)) {
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

    /**
     * Compacts tool result payloads in a single remaining tool-call block so total characters stay
     * within {@link #MAX_CONTEXT_CHARS}. Returns false if the assistant tool-call metadata alone
     * exceeds {@link #MAX_CONTEXT_CHARS}, signalling the caller to drop the entire block atomically.
     */
    private boolean compactSingleToolBlockToFit() {
        if (this.messages.isEmpty() || !hasToolCalls(this.messages.get(0))) {
            return false;
        }
        int assistantChars = messageCharLength(this.messages.get(0));
        int remainingBudget = MAX_CONTEXT_CHARS - assistantChars;
        if (remainingBudget < 0) {
            return false;
        }
        int toolCount = this.messages.size() - 1;
        if (toolCount <= 0) {
            return false;
        }
        for (int idx = 1; idx < this.messages.size(); idx++) {
            int remainingTools = this.messages.size() - idx;
            int allowance = Math.max(0, remainingBudget / remainingTools);
            ChatMessage toolMsg = this.messages.get(idx);
            String truncated = truncateToLimit(toolMsg.getContent(), allowance);
            this.messages.set(idx, ChatMessage.tool(toolMsg.getToolCallId(), toolMsg.getName(), truncated));
            remainingBudget -= truncated.length();
        }
        return getTotalContextChars() <= MAX_CONTEXT_CHARS;
    }

    private static String truncateToLimit(String content, int maxChars) {
        if (content == null || maxChars <= 0) {
            return "";
        }
        if (content.length() <= maxChars) {
            return content;
        }
        if (maxChars <= TRUNCATION_SUFFIX.length()) {
            return content.substring(0, maxChars);
        }
        return content.substring(0, maxChars - TRUNCATION_SUFFIX.length()) + TRUNCATION_SUFFIX;
    }

    private static ChatMessage copyWithContent(ChatMessage original, String newContent) {
        ChatMessage copy = new ChatMessage(original.getRole(), newContent);
        copy.setName(original.getName());
        copy.setToolCallId(original.getToolCallId());
        copy.setToolCalls(original.getToolCalls());
        return copy;
    }

    private void trimHistoryIfNeeded() {
        // 1. Remove any orphaned leading tool responses and broken tool-call blocks
        removeOrphanedLeadingTools();
        removeBrokenToolCallBlocks();

        // 2. Evict oldest atomic blocks while exceeding MAX_MESSAGES (including an oversized single block)
        while (this.messages.size() > MAX_MESSAGES && !this.messages.isEmpty()) {
            removeLeadingBlock();
        }

        // 3. Evict oldest atomic blocks while exceeding MAX_CONTEXT_CHARS; if a single remaining block
        //    still exceeds MAX_CONTEXT_CHARS, compact its payload or drop the block atomically
        while (getTotalContextChars() > MAX_CONTEXT_CHARS && !this.messages.isEmpty()) {
            int blockLen = atomicBlockLength(0);
            if (blockLen < this.messages.size()) {
                removeLeadingBlock();
            } else {
                ChatMessage head = this.messages.get(0);
                if (hasToolCalls(head)) {
                    if (!compactSingleToolBlockToFit()) {
                        removeLeadingBlock();
                    }
                } else {
                    String truncated = truncateToLimit(head.getContent(), MAX_CONTEXT_CHARS);
                    this.messages.set(0, copyWithContent(head, truncated));
                }
                break;
            }
        }

        // 4. Final integrity pass guaranteeing zero orphans or unmatched tool calls
        removeOrphanedLeadingTools();
        removeBrokenToolCallBlocks();
    }

    public ReentrantLock getTurnLock() {
        return turnLock;
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
