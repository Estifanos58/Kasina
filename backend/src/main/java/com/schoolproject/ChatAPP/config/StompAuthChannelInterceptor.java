package com.schoolproject.ChatAPP.config;

import com.schoolproject.ChatAPP.model.Channel;
import com.schoolproject.ChatAPP.repository.ChannelRepository;
import com.schoolproject.ChatAPP.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Enforces cryptographic JWT authentication on STOMP CONNECT and authorization
 * on STOMP SUBSCRIBE and SEND frames. Username-header fallback is intentionally absent.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(StompAuthChannelInterceptor.class);

    private final JwtUtil jwtUtil;
    private final ChannelRepository channelRepository;

    @Autowired
    public StompAuthChannelInterceptor(JwtUtil jwtUtil, ChannelRepository channelRepository) {
        this.jwtUtil = jwtUtil;
        this.channelRepository = channelRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT.equals(command)) {
            authenticateConnection(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            validateSubscription(accessor);
        } else if (StompCommand.SEND.equals(command)) {
            validateSend(accessor);
        }

        return message;
    }

    private void authenticateConnection(StompHeaderAccessor accessor) {
        UserPrincipal principal = resolvePrincipalFromStompToken(accessor);

        if (principal == null) {
            principal = resolvePrincipalFromHandshake(accessor);
        }

        if (principal == null || principal.getUserId() == null || principal.getUserId().isBlank()) {
            logger.warn("Rejecting unauthenticated STOMP CONNECT frame");
            throw new AccessDeniedException("WebSocket authentication required");
        }

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        accessor.setUser(auth);
        logger.debug("STOMP connection authenticated for user {}", principal.getUserId());
    }

    private UserPrincipal resolvePrincipalFromStompToken(StompHeaderAccessor accessor) {
        String token = extractNativeToken(accessor);
        if (token == null || token.isBlank()) {
            return null;
        }

        if (!jwtUtil.validateToken(token)) {
            logger.warn("Rejecting STOMP CONNECT with invalid JWT token");
            throw new AccessDeniedException("Invalid WebSocket authentication token");
        }

        String userId = jwtUtil.extractUserId(token);
        String email = jwtUtil.extractEmail(token);
        if (userId == null || userId.isBlank()) {
            throw new AccessDeniedException("Invalid WebSocket authentication token");
        }

        return new UserPrincipal(userId, email != null ? email : userId);
    }

    private UserPrincipal resolvePrincipalFromHandshake(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            return null;
        }
        Object attr = sessionAttributes.get(JwtHandshakeInterceptor.VERIFIED_PRINCIPAL_ATTR);
        if (attr instanceof UserPrincipal verifiedPrincipal) {
            return verifiedPrincipal;
        }
        return null;
    }

    private String extractNativeToken(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        String tokenHeader = accessor.getFirstNativeHeader("token");
        if (tokenHeader != null && !tokenHeader.isBlank()) {
            return tokenHeader.trim();
        }
        String jwtHeader = accessor.getFirstNativeHeader("jwt");
        if (jwtHeader != null && !jwtHeader.isBlank()) {
            return jwtHeader.trim();
        }
        return null;
    }

    private void validateSubscription(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null || accessor.getUser().getName() == null) {
            logger.warn("Rejecting unauthenticated STOMP SUBSCRIBE frame");
            throw new AccessDeniedException("Authentication required to subscribe");
        }

        String destination = accessor.getDestination();
        if (destination == null || destination.isBlank()) {
            throw new AccessDeniedException("Subscription destination is required");
        }

        String userId = accessor.getUser().getName();

        // Explicitly block legacy/public broadcast topics
        if ("/topic/public".equals(destination) || destination.startsWith("/topic/public/")) {
            logger.warn("User {} denied subscription to disallowed public topic {}", userId, destination);
            throw new AccessDeniedException("Public topic subscriptions are not permitted");
        }

        // Authorize channel topic subscriptions
        if (destination.startsWith("/topic/channel/")) {
            String channelId = destination.substring("/topic/channel/".length()).trim();
            if (channelId.isEmpty() || channelId.contains("/")) {
                throw new AccessDeniedException("Invalid channel destination");
            }
            Optional<Channel> channelOpt = channelRepository.findById(channelId);
            if (channelOpt.isEmpty()) {
                throw new AccessDeniedException("Channel not found");
            }
            Channel ch = channelOpt.get();
            boolean isMember = (ch.getAdmin() != null && userId.equals(ch.getAdmin().getId()))
                    || (ch.getMembers() != null && ch.getMembers().stream().anyMatch(m -> m != null && userId.equals(m.getId())));
            if (!isMember) {
                logger.warn("User {} denied subscription to unauthorized channel {}", userId, channelId);
                throw new AccessDeniedException("Not authorized to subscribe to this channel");
            }
            return;
        }

        // Deny unknown /topic/* destinations
        if (destination.startsWith("/topic/")) {
            logger.warn("User {} denied subscription to unknown topic {}", userId, destination);
            throw new AccessDeniedException("Not authorized to subscribe to this destination");
        }

        // Allow authenticated user's own /user/queue/* destinations, block cross-user subscriptions
        if (destination.startsWith("/user/")) {
            if (destination.startsWith("/user/queue/")) {
                return;
            }
            String expectedOwnPrefix = "/user/" + userId + "/queue/";
            if (!destination.startsWith(expectedOwnPrefix)) {
                logger.warn("User {} denied cross-user subscription to {}", userId, destination);
                throw new AccessDeniedException("Not authorized to subscribe to another user's queue");
            }
            return;
        }

        logger.warn("User {} denied subscription to unsupported destination {}", userId, destination);
        throw new AccessDeniedException("Unsupported subscription destination");
    }

    private void validateSend(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null || accessor.getUser().getName() == null) {
            logger.warn("Rejecting unauthenticated STOMP SEND frame");
            throw new AccessDeniedException("Authentication required to send messages");
        }
    }
}
