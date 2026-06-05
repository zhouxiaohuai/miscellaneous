package com.aichat.chat.dto;

public class ChatResponse {
    private String response;
    private String error;
    private String conversationId;

    public static ChatResponse ok(String conversationId, String response) {
        ChatResponse r = new ChatResponse();
        r.conversationId = conversationId;
        r.response = response;
        return r;
    }

    public static ChatResponse error(String error) {
        ChatResponse r = new ChatResponse();
        r.error = error;
        return r;
    }

    public String getResponse() {
        return response;
    }

    public String getError() {
        return error;
    }

    public String getConversationId() {
        return conversationId;
    }
}

