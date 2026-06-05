package com.aichat.auth.web.dto;

public record MeResponse(
        boolean loggedIn,
        String userId
) {}

