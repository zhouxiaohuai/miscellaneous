package com.aichat.chat.service;

import com.aichat.chat.llm.LlmMessage;
import com.aichat.chat.llm.PythonChatClient;
import com.aichat.memory.model.Conversation;
import com.aichat.memory.model.Message;
import com.aichat.memory.repo.ConversationRepository;
import com.aichat.memory.repo.MessageRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Main chat flow with server-side memory (user + multi-session).
 * <p>
 * Responsibilities:
 * - Validate conversation belongs to the caller\n
 * - Persist user and assistant messages\n
 * - Build context from summary + lastK recent messages\n
 * - Optionally refresh summary to keep context short (naive strategy)\n
 */
@Service
public class MemoryChatService {

    private static final int LAST_K = 20;
    private static final int SUMMARY_TRIGGER_TOTAL_MESSAGES = 60;
    private static final Duration SUMMARY_MIN_INTERVAL = Duration.ofMinutes(1);

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final PythonChatClient pythonChatClient;
    private final MemoryContextBuilder contextBuilder = new MemoryContextBuilder();

    public MemoryChatService(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            PythonChatClient pythonChatClient
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.pythonChatClient = pythonChatClient;
    }

    @Transactional
    public ChatResult chat(String userId, String conversationId, String userMessage) {
        Instant now = Instant.now();

        Conversation conversation = resolveConversation(userId, conversationId, now);

        // 1) Persist the user's message first so the full history is durable even if LLM fails.
        messageRepository.save(new Message(conversation.getId(), "user", userMessage, now));
        conversation.touch(now);
        conversationRepository.save(conversation);

        // 2) Load recent messages (window) and build LLM context.
        List<Message> lastK = loadLastK(conversation.getId());
        List<LlmMessage> llmMessages = contextBuilder.build(conversation.getSummary(), lastK, userMessage);

        Map<String, Object> pythonResp = pythonChatClient.chatWithMessages(llmMessages);
        Object responseObj = pythonResp == null ? null : pythonResp.get("response");
        Object errorObj = pythonResp == null ? null : pythonResp.get("error");

        if (responseObj instanceof String responseText && !responseText.isBlank()) {
            messageRepository.save(new Message(conversation.getId(), "assistant", responseText, Instant.now()));

            // 3) Optionally refresh summary (best-effort; failure does not fail the user request).
            maybeRefreshSummary(conversation, now);

            return ChatResult.ok(conversation.getId(), responseText);
        }

        String error = errorObj instanceof String s ? s : "Failed to get response from AI service";
        return ChatResult.error(conversation.getId(), error);
    }

    /**
     * Stream chat response tokens while persisting memory.
     * <p>
     * Persistence strategy:
     * - persist user message before streaming starts (durable history)
     * - stream assistant tokens to the caller as they arrive
     * - on done: persist full assistant message, then best-effort refresh summary
     */
    public void chatStream(
            String userId,
            String conversationId,
            String userMessage,
            Consumer<String> onToken,
            Consumer<String> onError,
            Consumer<ChatStreamDone> onDone
    ) {
        Instant now = Instant.now();

        ChatStreamStart start = startStream(userId, conversationId, userMessage, now);
        String resolvedConversationId = start.conversationId();

        StringBuilder full = new StringBuilder();
        AtomicBoolean doneCalled = new AtomicBoolean(false);

        pythonChatClient.streamChatWithMessages(
                start.llmMessages(),
                delta -> {
                    if (delta == null || delta.isBlank()) return;
                    full.append(delta);
                    onToken.accept(delta);
                },
                err -> {
                    if (doneCalled.compareAndSet(false, true)) {
                        onError.accept(err);
                    }
                },
                () -> {
                    if (!doneCalled.compareAndSet(false, true)) return;
                    String finalText = full.toString();
                    finishStream(resolvedConversationId, finalText, now);
                    onDone.accept(new ChatStreamDone(resolvedConversationId, finalText));
                }
        );
    }

    @Transactional
    protected ChatStreamStart startStream(String userId, String conversationId, String userMessage, Instant now) {
        Conversation conversation = resolveConversation(userId, conversationId, now);

        messageRepository.save(new Message(conversation.getId(), "user", userMessage, now));
        conversation.touch(now);
        conversationRepository.save(conversation);

        List<Message> lastK = loadLastK(conversation.getId());
        List<LlmMessage> llmMessages = contextBuilder.build(conversation.getSummary(), lastK, userMessage);
        return new ChatStreamStart(conversation.getId(), llmMessages);
    }

    @Transactional
    protected void finishStream(String conversationId, String responseText, Instant startedAt) {
        if (responseText == null || responseText.isBlank()) return;

        conversationRepository.findById(conversationId).ifPresent(conversation -> {
            messageRepository.save(new Message(conversation.getId(), "assistant", responseText, Instant.now()));
            conversation.touch(Instant.now());
            conversationRepository.save(conversation);

            // Best-effort summary refresh (same behavior as non-streaming)
            maybeRefreshSummary(conversation, startedAt);
        });
    }

    private Conversation resolveConversation(String userId, String conversationId, Instant now) {
        if (conversationId != null && !conversationId.isBlank()) {
            return conversationRepository.findByIdAndUserId(conversationId, userId)
                    .orElseThrow(() -> new ConversationNotFoundException(conversationId));
        }

        // Backward-compat for old frontend: use latest conversation if exists; otherwise create one.
        return conversationRepository.findTopByUserIdOrderByUpdatedAtDesc(userId)
                .orElseGet(() -> conversationRepository.save(new Conversation(
                        UUID.randomUUID().toString(),
                        userId,
                        "默认会话",
                        null,
                        null,
                        now,
                        now
                )));
    }

    private List<Message> loadLastK(String conversationId) {
        // We fetch lastK newest then reverse to chronological order.
        List<Message> newestFirst = messageRepository.findByConversationIdOrderByCreatedAtDesc(
                conversationId,
                PageRequest.of(0, LAST_K)
        );
        Collections.reverse(newestFirst);
        return newestFirst;
    }

    private void maybeRefreshSummary(Conversation conversation, Instant now) {
        long total = messageRepository.countByConversationId(conversation.getId());
        if (total < SUMMARY_TRIGGER_TOTAL_MESSAGES) return;

        Instant lastUpdated = conversation.getSummaryUpdatedAt();
        if (lastUpdated != null && Duration.between(lastUpdated, now).compareTo(SUMMARY_MIN_INTERVAL) < 0) return;

        // Naive summary: summarize everything except lastK messages.
        // This is intentionally simple; later iterations can make it incremental using a cursor.
        int summarizeLimit = Math.max(0, (int) Math.min(total, 500) - LAST_K);
        if (summarizeLimit <= 0) return;

        List<Message> oldest = messageRepository.findByConversationIdOrderByCreatedAtAsc(
                conversation.getId(),
                PageRequest.of(0, summarizeLimit)
        );

        String prompt = buildSummaryPrompt(conversation.getSummary(), oldest);
        List<LlmMessage> summaryReq = List.of(new LlmMessage("user", prompt));

        try {
            Map<String, Object> resp = pythonChatClient.chatWithMessages(summaryReq);
            Object responseObj = resp == null ? null : resp.get("response");
            if (responseObj instanceof String newSummary && !newSummary.isBlank()) {
                conversation.setSummary(newSummary, Instant.now());
                conversationRepository.save(conversation);
            }
        } catch (Exception ignored) {
            // Best-effort: summary refresh must never break the primary chat response.
        }
    }

    private String buildSummaryPrompt(String existingSummary, List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("请将下面对话内容总结成“长期记忆摘要”，要求：\n");
        sb.append("1) 保留用户关键信息、偏好、约束、未完成事项；\n");
        sb.append("2) 不要编造；\n");
        sb.append("3) 输出尽量精炼，条目化。\n\n");

        if (existingSummary != null && !existingSummary.isBlank()) {
            sb.append("已有摘要：\n").append(existingSummary).append("\n\n");
        }

        sb.append("需要总结的对话（按时间顺序）：\n");
        for (Message m : messages) {
            sb.append(m.getRole()).append(": ").append(m.getContent()).append("\n");
        }
        return sb.toString();
    }

    public record ChatResult(String conversationId, String response, String error) {
        public static ChatResult ok(String conversationId, String response) {
            return new ChatResult(conversationId, response, null);
        }

        public static ChatResult error(String conversationId, String error) {
            return new ChatResult(conversationId, null, error);
        }
    }

    public record ChatStreamDone(String conversationId, String fullResponse) {}

    protected record ChatStreamStart(String conversationId, List<LlmMessage> llmMessages) {}

    public static class ConversationNotFoundException extends RuntimeException {
        public ConversationNotFoundException(String conversationId) {
            super("Conversation not found: " + conversationId);
        }
    }
}

