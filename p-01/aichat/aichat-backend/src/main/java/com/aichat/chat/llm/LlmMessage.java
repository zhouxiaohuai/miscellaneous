package com.aichat.chat.llm;

/**
 * LLM chat message payload (role/content) compatible with common chat-completion APIs.
 * <p>
 * We keep this as a small POJO so we can serialize to JSON when calling the Python gateway.
 */
public class LlmMessage {
    private String role;
    private String content;

    public LlmMessage() {
    }

    public LlmMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}

