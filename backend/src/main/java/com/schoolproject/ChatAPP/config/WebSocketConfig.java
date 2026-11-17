package com.schoolproject.ChatAPP.config;

import com.schoolproject.ChatAPP.model.Channel;
import com.schoolproject.ChatAPP.repository.ChannelRepository;
import com.schoolproject.ChatAPP.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketConfig.class);

    @Value("${cors.allowed-origins:${FRONTEND_URL:http://localhost:5173}}")
    private String allowedOrigins;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ChannelRepository channelRepository;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable in-memory message broker for topics and user queues
        config.enableSimpleBroker("/topic", "/queue");
        // Prefix for messages bound for @MessageMapping methods
        config.setApplicationDestinationPrefixes("/app");
        // Prefix for user-specific destinations (/user/{userId}/queue/...)
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        registry.addEndpoint("/ws")
                .setAllowedOrigins(origins)
                .withSockJS();

        registry.addEndpoint("/ws")
                .setAllowedOrigins(origins);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor != null) {
                    if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                        authenticateConnection(accessor);
                    } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                        validateSubscription(accessor);
                    }
                }

                return message;
            }
        });
    }

    private void authenticateConnection(StompHeaderAccessor accessor) {
        // Extract token from native headers (token, Authorization, or jwt)
        String token = accessor.getFirstNativeHeader("token");
        if (token == null || token.isEmpty()) {
            token = accessor.getFirstNativeHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
        }
        if (token == null || token.isEmpty()) {
            token = accessor.getFirstNativeHeader("jwt");
        }

        String userId = null;
        String email = null;

        if (token != null && jwtUtil.validateToken(token)) {
            userId = jwtUtil.extractUserId(token);
            email = jwtUtil.extractEmail(token);
        } else {
            // Fallback for development/testing: check username header
            String usernameHeader = accessor.getFirstNativeHeader("username");
            if (usernameHeader != null && !usernameHeader.trim().isEmpty()) {
                userId = usernameHeader.trim();
                email = usernameHeader.trim();
            }
        }

        if (userId != null && !userId.isEmpty()) {
            UserPrincipal principal = new UserPrincipal(userId, email != null ? email : userId);
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            accessor.setUser(auth);
            logger.info("WebSocket connection authenticated for user: {}", userId);
        } else {
            logger.warn("WebSocket connection attempt without valid authentication credentials");
            throw new AccessDeniedException("WebSocket authentication required");
        }
    }

    private void validateSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || accessor.getUser() == null) {
            return;
        }

        String userId = accessor.getUser().getName();

        // If subscribing to a channel topic, verify membership
        if (destination.startsWith("/topic/channel/")) {
            String channelId = destination.substring("/topic/channel/".length());
            Optional<Channel> channelOpt = channelRepository.findById(channelId);
            if (channelOpt.isPresent()) {
                Channel ch = channelOpt.get();
                boolean isMember = (ch.getAdmin() != null && ch.getAdmin().getId().equals(userId)) ||
                        (ch.getMembers() != null && ch.getMembers().stream().anyMatch(m -> m != null && m.getId().equals(userId)));
                if (!isMember) {
                    logger.warn("User {} denied subscription to unauthorized channel {}", userId, channelId);
                    throw new AccessDeniedException("Not authorized to subscribe to this channel");
                }
            } else {
                throw new AccessDeniedException("Channel not found");
            }
        }
    }
}
