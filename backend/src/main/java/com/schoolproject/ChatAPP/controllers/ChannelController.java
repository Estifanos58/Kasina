package com.schoolproject.ChatAPP.controllers;

import com.schoolproject.ChatAPP.config.UserPrincipal;
import com.schoolproject.ChatAPP.model.Channel;
import com.schoolproject.ChatAPP.model.Message;
import com.schoolproject.ChatAPP.model.User;
import com.schoolproject.ChatAPP.repository.ChannelRepository;
import com.schoolproject.ChatAPP.repository.MessageRepository;
import com.schoolproject.ChatAPP.repository.UserRepository;
import com.schoolproject.ChatAPP.requests.ChannelRequest;
import com.schoolproject.ChatAPP.responses.ChannelResponseDTO;
import com.schoolproject.ChatAPP.responses.ErrorResponse;
import com.schoolproject.ChatAPP.responses.MessagePageResponse;
import com.schoolproject.ChatAPP.responses.MessageResponseDTO;
import com.schoolproject.ChatAPP.util.MessageCursor;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/channels")
@Validated
public class ChannelController {

    private static final Logger logger = LoggerFactory.getLogger(ChannelController.class);
    private static final int DEFAULT_PAGE_LIMIT = 50;
    private static final int MAX_PAGE_LIMIT = 100;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MessageRepository messageRepository;

    @PostMapping("/create-channel")
    public ResponseEntity<?> createChannel(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChannelRequest request) {
        if (principal == null || principal.getUserId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "Unauthorized"));
        }

        String userId = principal.getUserId();
        Optional<User> admin = userRepository.findById(userId);
        if (admin.isEmpty()) {
            return ResponseEntity.badRequest().body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Admin user not found"));
        }

        // Deduplicate member IDs while preserving order
        List<String> uniqueMemberIds = new ArrayList<>(new LinkedHashSet<>(request.getMembers()));
        List<User> validMembers = userRepository.findAllById(uniqueMemberIds);
        if (validMembers.size() != uniqueMemberIds.size()) {
            return ResponseEntity.badRequest().body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Some members are not valid users."));
        }

        // Ensure admin is also in member list if not already
        if (validMembers.stream().noneMatch(m -> userId.equals(m.getId()))) {
            validMembers = new ArrayList<>(validMembers);
            validMembers.add(admin.get());
        }

        Channel newChannel = new Channel();
        newChannel.setName(request.getName().trim());
        newChannel.setMembers(validMembers);
        newChannel.setAdmin(admin.get());

        Channel savedChannel = channelRepository.save(newChannel);
        logger.info("Channel '{}' ({}) created by user {}", savedChannel.getName(), savedChannel.getId(), userId);

        return ResponseEntity.status(HttpStatus.CREATED).body(ChannelResponseDTO.fromEntity(savedChannel));
    }

    @GetMapping("/user-channels")
    public ResponseEntity<?> getUserChannels(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "Unauthorized"));
        }

        String userId = principal.getUserId();

        // Query directly via indexed MongoDB fields rather than loading the entire channels collection
        List<Channel> userChannels = channelRepository.findByAdmin_IdOrMembers_IdOrderByUpdatedAtDesc(userId, userId);

        // Deduplicate by channel ID in case Mongo returns duplicate references
        Map<String, Channel> uniqueChannels = new LinkedHashMap<>();
        for (Channel ch : userChannels) {
            if (ch != null && ch.getId() != null) {
                uniqueChannels.putIfAbsent(ch.getId(), ch);
            }
        }

        List<ChannelResponseDTO> channels = uniqueChannels.values()
                .stream()
                .map(ChannelResponseDTO::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(channels);
    }

    @GetMapping("/get-channel-messages/{channelId}")
    public ResponseEntity<?> getChannelMessages(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String channelId,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false) Integer requestedLimit,
            @RequestParam(value = "paginated", required = false) Boolean paginated) {

        if (principal == null || principal.getUserId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "Unauthorized"));
        }

        String userId = principal.getUserId();
        Optional<Channel> channelOpt = channelRepository.findById(channelId);
        if (channelOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(HttpStatus.NOT_FOUND.value(), "Channel not found"));
        }

        Channel channel = channelOpt.get();

        boolean isMember = (channel.getAdmin() != null && userId.equals(channel.getAdmin().getId()))
                || (channel.getMembers() != null && channel.getMembers().stream().anyMatch(m -> m != null && userId.equals(m.getId())));

        if (!isMember) {
            logger.warn("User {} attempted unauthorized access to channel {}", userId, channelId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(HttpStatus.FORBIDDEN.value(), "You are not a member of this channel"));
        }

        boolean returnPaginatedEnvelope = Boolean.TRUE.equals(paginated)
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

        List<Message> fetched = messageRepository.findChannelMessagesPage(channelId, beforeTimestamp, beforeId, limit + 1);
        boolean hasMore = fetched.size() > limit;
        List<Message> pageEntities = hasMore ? new ArrayList<>(fetched.subList(0, limit)) : new ArrayList<>(fetched);
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
}
