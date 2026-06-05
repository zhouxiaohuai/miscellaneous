package com.aichat.memory.web.dto;

import java.time.Instant;

public class ConversationResponse {
    private String id;
    private String title;
    private Instant updatedAt;

    public ConversationResponse(String id, String title, Instant updatedAt) {
        this.id = id;
        this.title = title;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

