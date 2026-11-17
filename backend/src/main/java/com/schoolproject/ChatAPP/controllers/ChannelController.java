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
import com.schoolproject.ChatAPP.responses.MessageResponseDTO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/channels")
@Validated
public class ChannelController {

    private static final Logger logger = LoggerFactory.getLogger(ChannelController.class);

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MessageRepository messageRepository;

    // Create a new channel
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

        List<User> validMembers = userRepository.findAllById(request.getMembers());
        if (validMembers.size() != request.getMembers().size()) {
            return ResponseEntity.badRequest().body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Some members are not valid users."));
        }

        // Ensure admin is also in member list if not already
        if (validMembers.stream().noneMatch(m -> m.getId().equals(userId))) {
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

    // Get channels for authenticated user
    @GetMapping("/user-channels")
    public ResponseEntity<?> getUserChannels(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "Unauthorized"));
        }

        String userId = principal.getUserId();

        List<ChannelResponseDTO> channels = channelRepository.findAll()
                .stream()
                .filter(channel ->
                        (channel.getAdmin() != null && channel.getAdmin().getId().equals(userId)) ||
                        (channel.getMembers() != null && channel.getMembers().stream().anyMatch(m -> m != null && m.getId().equals(userId)))
                )
                .map(ChannelResponseDTO::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(channels);
    }

    // Get messages for a specific channel
    @GetMapping("/get-channel-messages/{channelId}")
    public ResponseEntity<?> getChannelMessages(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String channelId) {
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

        // Verify channel membership
        boolean isMember = (channel.getAdmin() != null && channel.getAdmin().getId().equals(userId)) ||
                (channel.getMembers() != null && channel.getMembers().stream().anyMatch(m -> m != null && m.getId().equals(userId)));

        if (!isMember) {
            logger.warn("User {} attempted unauthorized access to channel {}", userId, channelId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(HttpStatus.FORBIDDEN.value(), "You are not a member of this channel"));
        }

        // Query messages directly from MessageRepository ordered by timestamp ascending
        List<MessageResponseDTO> messages = messageRepository.findByChannelIdOrderByTimestampsAsc(channelId)
                .stream()
                .map(MessageResponseDTO::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(messages);
    }
}
