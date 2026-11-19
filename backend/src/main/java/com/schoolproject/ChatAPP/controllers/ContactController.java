package com.schoolproject.ChatAPP.controllers;

import com.schoolproject.ChatAPP.config.UserPrincipal;
import com.schoolproject.ChatAPP.model.User;
import com.schoolproject.ChatAPP.repository.DmContactSummary;
import com.schoolproject.ChatAPP.repository.MessageRepository;
import com.schoolproject.ChatAPP.repository.UserRepository;
import com.schoolproject.ChatAPP.requests.SearchRequest;
import com.schoolproject.ChatAPP.responses.ContactResponseDTO;
import com.schoolproject.ChatAPP.responses.ErrorResponse;
import com.schoolproject.ChatAPP.responses.UserDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/contacts")
@Validated
public class ContactController {

    private static final int DEFAULT_CONTACT_LIMIT = 50;
    private static final int MAX_CONTACT_LIMIT = 100;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MessageRepository messageRepository;

    @PostMapping("/search")
    public ResponseEntity<?> searchContacts(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody SearchRequest request,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        if (principal == null || principal.getUserId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "Unauthorized"));
        }

        String userId = principal.getUserId();
        String searchTerm = request != null ? request.getSearchTerm() : null;
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Search term is required."));
        }

        String sanitizedSearchTerm = Pattern.quote(searchTerm.trim());
        Pattern regex = Pattern.compile(sanitizedSearchTerm, Pattern.CASE_INSENSITIVE);
        int boundedLimit = clampLimit(limit);

        List<User> contacts = userRepository.findByIdNotAndFirstnameRegexIgnoreCaseOrIdNotAndLastnameRegexIgnoreCaseOrIdNotAndEmailRegexIgnoreCase(
                userId, regex.pattern(), userId, regex.pattern(), userId, regex.pattern(),
                PageRequest.of(0, boundedLimit, Sort.by(Sort.Direction.ASC, "firstname", "email"))
        );

        List<UserDTO> dtoList = contacts.stream()
                .map(UserDTO::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtoList);
    }

    @GetMapping("/get-all-contacts")
    public ResponseEntity<?> getAllContacts(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        if (principal == null || principal.getUserId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "Unauthorized"));
        }

        String userId = principal.getUserId();
        int safePage = Math.max(0, page);
        int boundedLimit = clampLimit(limit);

        List<UserDTO> contacts = userRepository.findByIdNot(
                        userId,
                        PageRequest.of(safePage, boundedLimit, Sort.by(Sort.Direction.ASC, "firstname", "email"))
                )
                .stream()
                .map(UserDTO::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(contacts);
    }

    @GetMapping("/dm-list")
    public ResponseEntity<?> getContactsForDMList(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        if (principal == null || principal.getUserId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "Unauthorized"));
        }

        String userId = principal.getUserId();
        int boundedLimit = clampLimit(limit);

        // Use MongoDB aggregation pipeline to retrieve only the latest message per contact
        List<DmContactSummary> summaries = messageRepository.findRecentDmContacts(userId, boundedLimit);
        if (summaries == null || summaries.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<String> contactIds = summaries.stream()
                .map(DmContactSummary::getContactId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (contactIds.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        Map<String, User> userMap = userRepository.findAllById(contactIds)
                .stream()
                .collect(Collectors.toMap(User::getId, u -> u, (first, second) -> first));

        List<ContactResponseDTO> response = summaries.stream()
                .filter(s -> s.getContactId() != null && userMap.containsKey(s.getContactId()))
                .map(s -> {
                    User contact = userMap.get(s.getContactId());
                    return new ContactResponseDTO(
                            contact.getId(),
                            contact.getEmail(),
                            contact.getFirstname(),
                            contact.getLastname(),
                            contact.getImage(),
                            contact.getColor(),
                            s.getLastMessageTime()
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

    @PostMapping("/find-user")
    public ResponseEntity<?> getUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody FindUserRequest request) {
        if (principal == null || principal.getUserId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "Unauthorized"));
        }

        String targetId = request != null ? request.getUserId() : null;
        if (targetId == null || targetId.isEmpty()) {
            targetId = request != null ? request.getId() : null;
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

    private int clampLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_CONTACT_LIMIT;
        }
        return Math.min(limit, MAX_CONTACT_LIMIT);
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
