package com.schoolproject.ChatAPP.controllers;

import com.schoolproject.ChatAPP.config.UserPrincipal;
import com.schoolproject.ChatAPP.model.Message;
import com.schoolproject.ChatAPP.repository.MessageRepository;
import com.schoolproject.ChatAPP.responses.ErrorResponse;
import com.schoolproject.ChatAPP.responses.MessageResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/messages")
@Validated
public class MessageController {

    private static final Logger logger = LoggerFactory.getLogger(MessageController.class);

    @Autowired
    private MessageRepository messageRepository;

    @PostMapping("/get-messages")
    public ResponseEntity<?> getMessages(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody MessageRequest request) {
        if (principal == null || principal.getUserId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "Unauthorized"));
        }

        String user1 = principal.getUserId();
        String user2 = request.getId();

        if (user2 == null || user2.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Recipient user ID is required."));
        }

        List<MessageResponseDTO> messages = messageRepository
                .findBySenderAndRecipientOrRecipientAndSenderOrderByTimestamps(user1, user2, user1, user2)
                .stream()
                .map(MessageResponseDTO::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(messages);
    }

    // Request DTO
    public static class MessageRequest {
        private String id;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }
}
