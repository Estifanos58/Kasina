package com.schoolproject.ChatAPP.controllers;

import com.schoolproject.ChatAPP.config.UserPrincipal;
import com.schoolproject.ChatAPP.model.Message;
import com.schoolproject.ChatAPP.model.User;
import com.schoolproject.ChatAPP.repository.MessageRepository;
import com.schoolproject.ChatAPP.repository.UserRepository;
import com.schoolproject.ChatAPP.requests.SearchRequest;
import com.schoolproject.ChatAPP.responses.ContactResponseDTO;
import com.schoolproject.ChatAPP.responses.ErrorResponse;
import com.schoolproject.ChatAPP.responses.UserDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/contacts")
@Validated
public class ContactController {

    private static final Logger logger = LoggerFactory.getLogger(ContactController.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MessageRepository messageRepository;

    // Search contacts
    @PostMapping("/search")
    public ResponseEntity<?> searchContacts(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody SearchRequest request) {
        if (principal == null || principal.getUserId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "Unauthorized"));
        }

        String userId = principal.getUserId();
        String searchTerm = request.getSearchTerm();
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Search term is required."));
        }

        // Sanitize search term to prevent regex injection
        String sanitizedSearchTerm = Pattern.quote(searchTerm.trim());
        Pattern regex = Pattern.compile(sanitizedSearchTerm, Pattern.CASE_INSENSITIVE);

        // Fetch matching users excluding current user
        List<User> contacts = userRepository.findByIdNotAndFirstnameRegexIgnoreCaseOrIdNotAndLastnameRegexIgnoreCaseOrIdNotAndEmailRegexIgnoreCase(
                userId, regex.pattern(), userId, regex.pattern(), userId, regex.pattern()
        );

        List<UserDTO> dtoList = contacts.stream()
                .map(UserDTO::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtoList);
    }

    // Get all contacts excluding current user
    @GetMapping("/get-all-contacts")
    public ResponseEntity<?> getAllContacts(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "Unauthorized"));
        }

        String userId = principal.getUserId();
        List<UserDTO> contacts = userRepository.findByIdNot(userId)
                .stream()
                .map(UserDTO::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(contacts);
    }

    // Get contacts for DM list with correct timestamp ordering
    @GetMapping("/dm-list")
    public ResponseEntity<?> getContactsForDMList(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "Unauthorized"));
        }

        String userId = principal.getUserId();

        // Fetch all direct messages involving the user ordered by timestamp descending
        List<Message> allMessages = messageRepository.findBySenderOrRecipientOrderByTimestampsDesc(userId, userId);

        // Group by contactId and find the latest message per contact
        Map<String, Message> lastMessagesMap = new LinkedHashMap<>();
        for (Message message : allMessages) {
            // Ignore channel messages if any were tagged with recipient
            if (message.getChannelId() != null) {
                continue;
            }
            String contactId = message.getSender().equals(userId) ? message.getRecipient() : message.getSender();
            if (contactId == null || contactId.equals(userId)) {
                continue;
            }
            if (!lastMessagesMap.containsKey(contactId)) {
                lastMessagesMap.put(contactId, message);
            } else {
                Message existing = lastMessagesMap.get(contactId);
                if (existing.getTimestamps() == null || (message.getTimestamps() != null && message.getTimestamps().isAfter(existing.getTimestamps()))) {
                    lastMessagesMap.put(contactId, message);
                }
            }
        }

        if (lastMessagesMap.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<String> contactIds = new ArrayList<>(lastMessagesMap.keySet());
        Map<String, User> userMap = userRepository.findAllById(contactIds)
                .stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // Combine contact info with last message time and sort by FULL LocalDateTime descending
        List<ContactResponseDTO> response = contactIds.stream()
                .filter(userMap::containsKey)
                .map(contactId -> {
                    User contact = userMap.get(contactId);
                    Message lastMessage = lastMessagesMap.get(contactId);
                    LocalDateTime lastMsgTime = lastMessage != null ? lastMessage.getTimestamps() : null;
                    return new ContactResponseDTO(
                            contact.getId(),
                            contact.getEmail(),
                            contact.getFirstname(),
                            contact.getLastname(),
                            contact.getImage(),
                            contact.getColor(),
                            lastMsgTime
                    );
                })
                .sorted((a, b) -> {
                    if (a.getLastMessageTime() == null && b.getLastMessageTime() == null) return 0;
                    if (a.getLastMessageTime() == null) return 1;
                    if (b.getLastMessageTime() == null) return -1;
                    return b.getLastMessageTime().compareTo(a.getLastMessageTime());
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    // Find a specific user by ID
    @PostMapping("/find-user")
    public ResponseEntity<?> getUser(@RequestBody FindUserRequest request) {
        String targetId = request.getUserId();
        if (targetId == null || targetId.isEmpty()) {
            targetId = request.getId();
        }

        if (targetId == null || targetId.isEmpty()) {
            return ResponseEntity.badRequest().body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "User ID is required"));
        }

        Optional<User> user = userRepository.findById(targetId);
        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(HttpStatus.NOT_FOUND.value(), "User not found"));
        }

        return ResponseEntity.ok(UserDTO.fromEntity(user.get()));
    }

    public static class FindUserRequest {
        private String userId;
        private String id;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }
}
