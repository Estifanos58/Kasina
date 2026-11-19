package com.schoolproject.ChatAPP.controllers;

import com.schoolproject.ChatAPP.config.UserPrincipal;
import com.schoolproject.ChatAPP.model.Message;
import com.schoolproject.ChatAPP.repository.MessageRepository;
import com.schoolproject.ChatAPP.responses.ErrorResponse;
import com.schoolproject.ChatAPP.responses.MessagePageResponse;
import com.schoolproject.ChatAPP.responses.MessageResponseDTO;
import com.schoolproject.ChatAPP.util.MessageCursor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/messages")
@Validated
public class MessageController {

    private static final int DEFAULT_PAGE_LIMIT = 50;
    private static final int MAX_PAGE_LIMIT = 100;

    @Autowired
    private MessageRepository messageRepository;

    @PostMapping("/get-messages")
    public ResponseEntity<?> getMessages(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody MessageRequest request,
            @RequestParam(value = "cursor", required = false) String queryCursor,
            @RequestParam(value = "limit", required = false) Integer queryLimit,
            @RequestParam(value = "paginated", required = false) Boolean queryPaginated) {

        if (principal == null || principal.getUserId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "Unauthorized"));
        }

        if (request == null || request.getId() == null || request.getId().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Recipient user ID is required."));
        }

        String user1 = principal.getUserId();
        String user2 = request.getId().trim();

        String cursor = queryCursor != null ? queryCursor : request.getCursor();
        Integer requestedLimit = queryLimit != null ? queryLimit : request.getLimit();
        boolean returnPaginatedEnvelope = Boolean.TRUE.equals(queryPaginated)
                || Boolean.TRUE.equals(request.getPaginated())
                || (cursor != null && !cursor.isBlank())
                || requestedLimit != null;

        int limit = clampLimit(requestedLimit);
        Instant beforeTimestamp = null;
        String beforeId = null;

        if (cursor != null && !cursor.isBlank()) {
            MessageCursor parsed = MessageCursor.decode(cursor);
            beforeTimestamp = parsed.getTimestamp();
            beforeId = parsed.getId();
        }

        List<Message> fetched = messageRepository.findDirectMessagesPage(user1, user2, beforeTimestamp, beforeId, limit + 1);
        boolean hasMore = fetched.size() > limit;
        List<Message> pageEntities = hasMore ? new ArrayList<>(fetched.subList(0, limit)) : new ArrayList<>(fetched);

        // Reverse from descending DB order to chronological ascending order for chat display
        Collections.reverse(pageEntities);

        List<MessageResponseDTO> messages = pageEntities.stream()
                .map(MessageResponseDTO::fromEntity)
                .collect(Collectors.toList());

        if (returnPaginatedEnvelope) {
            String nextCursor = null;
            if (hasMore && !pageEntities.isEmpty()) {
                Message oldest = pageEntities.get(0);
                nextCursor = MessageCursor.encode(oldest.getTimestamps(), oldest.getId());
            }
            return ResponseEntity.ok(new MessagePageResponse(messages, nextCursor, hasMore));
        }

        return ResponseEntity.ok(messages);
    }

    private int clampLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_PAGE_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_PAGE_LIMIT));
    }

    public static class MessageRequest {
        private String id;
        private String cursor;
        private Integer limit;
        private Boolean paginated;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getCursor() {
            return cursor;
        }

        public void setCursor(String cursor) {
            this.cursor = cursor;
        }

        public Integer getLimit() {
            return limit;
        }

        public void setLimit(Integer limit) {
            this.limit = limit;
        }

        public Boolean getPaginated() {
            return paginated;
        }

        public void setPaginated(Boolean paginated) {
            this.paginated = paginated;
        }
    }
}
