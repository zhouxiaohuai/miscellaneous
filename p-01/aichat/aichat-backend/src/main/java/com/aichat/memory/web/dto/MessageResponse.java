package com.aichat.memory.web.dto;

import java.time.Instant;

public class MessageResponse {
    private Long id;
    private String role;
    private String content;
    private Instant createdAt;

    public MessageResponse(Long id, String role, String content, Instant createdAt) {
        this.id = id;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

