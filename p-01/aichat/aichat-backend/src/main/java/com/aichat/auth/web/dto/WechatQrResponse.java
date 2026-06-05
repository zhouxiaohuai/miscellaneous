package com.aichat.auth.web.dto;

public record WechatQrResponse(
        String state,
        String qrUrl,
        long expiresInSeconds
) {}

