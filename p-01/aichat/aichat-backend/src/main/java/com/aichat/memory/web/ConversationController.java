package com.aichat.memory.web;

import com.aichat.auth.AuthSession;
import com.aichat.memory.model.Conversation;
import com.aichat.memory.model.Message;
import com.aichat.memory.repo.ConversationRepository;
import com.aichat.memory.repo.MessageRepository;
import com.aichat.memory.web.dto.ConversationResponse;
import com.aichat.memory.web.dto.CreateConversationRequest;
import com.aichat.memory.web.dto.MessageResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public ConversationController(ConversationRepository conversationRepository, MessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    @PostMapping
    public ResponseEntity<ConversationResponse> createConversation(
            @RequestBody(required = false) CreateConversationRequest request,
            HttpSession session
    ) {
        String userId = AuthSession.requireUserId(session);
        String title = request == null ? null : request.getTitle();

        Instant now = Instant.now();
        Conversation c = new Conversation(
                UUID.randomUUID().toString(),
                userId,
                title,
                null,
                null,
                now,
                now
        );

        Conversation saved = conversationRepository.save(c);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ConversationResponse(saved.getId(), saved.getTitle(), saved.getUpdatedAt()));
    }

    @GetMapping
    public List<ConversationResponse> listConversations(HttpSession session) {
        String userId = AuthSession.requireUserId(session);
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(c -> new ConversationResponse(c.getId(), c.getTitle(), c.getUpdatedAt()))
                .toList();
    }

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<List<MessageResponse>> listMessages(
            @PathVariable String conversationId,
            @RequestParam(name = "limit", defaultValue = "50") int limit,
            HttpSession session
    ) {
        String userId = AuthSession.requireUserId(session);
        if (limit <= 0 || limit > 200) {
            return ResponseEntity.badRequest().build();
        }

        return conversationRepository.findByIdAndUserId(conversationId, userId)
                .map(c -> {
                    List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(
                            conversationId,
                            PageRequest.of(0, limit)
                    );
                    List<MessageResponse> response = messages.stream()
                            .map(m -> new MessageResponse(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt()))
                            .toList();
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}

