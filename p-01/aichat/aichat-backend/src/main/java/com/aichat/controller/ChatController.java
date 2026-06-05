package com.aichat.controller;


import com.aichat.chat.dto.ChatRequest;
import com.aichat.chat.dto.ChatResponse;
import com.aichat.chat.service.MemoryChatService;
import com.aichat.auth.AuthSession;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api")
public class ChatController {
    
    @Autowired
    private MemoryChatService memoryChatService;
    
    @PostMapping("/chat")
    public Object chat(
            @RequestBody(required = false) ChatRequest request,
            HttpSession session
    ) {
        try {
            String userId = AuthSession.requireUserId(session);
            if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
                return ChatResponse.error("Missing message parameter");
            }

            MemoryChatService.ChatResult result = memoryChatService.chat(
                    userId,
                    request.getConversationId(),
                    request.getMessage()
            );

            if (result.response() != null) {
                return ChatResponse.ok(result.conversationId(), result.response());
            }
            return ChatResponse.error(result.error());
        } catch (Exception e) {
            return ChatResponse.error("Failed to process request: " + e.getMessage());
        }
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> chatStream(
            @RequestBody(required = false) ChatRequest request,
            HttpSession session
    ) {
        try {
            AuthSession.requireUserId(session);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        SseEmitter emitter = new SseEmitter(0L);

        CompletableFuture.runAsync(() -> {
            try {
                String userId = AuthSession.requireUserId(session);
                if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(Map.of("error", "Missing message parameter")));
                    emitter.complete();
                    return;
                }

                memoryChatService.chatStream(
                        userId,
                        request.getConversationId(),
                        request.getMessage(),
                        delta -> {
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("token")
                                        .data(Map.of("delta", delta)));
                            } catch (Exception e) {
                                emitter.completeWithError(e);
                            }
                        },
                        error -> {
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("error")
                                        .data(Map.of("error", error)));
                            } catch (Exception e) {
                                emitter.completeWithError(e);
                            } finally {
                                emitter.complete();
                            }
                        },
                        done -> {
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("done")
                                        .data(Map.of(
                                                "conversationId", done.conversationId(),
                                                "full", done.fullResponse()
                                        )));
                            } catch (Exception e) {
                                emitter.completeWithError(e);
                            } finally {
                                emitter.complete();
                            }
                        }
                );
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(Map.of("error", "Failed to process request: " + e.getMessage())));
                } catch (Exception ignored) {
                    // ignore secondary failure
                } finally {
                    emitter.complete();
                }
            }
        });

        return ResponseEntity.ok(emitter);
    }
    
    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "ok");
        return response;
    }
}
