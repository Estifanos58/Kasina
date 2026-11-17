package com.schoolproject.ChatAPP.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

@Component
public class WebSocketEventListener {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketEventListener.class);

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = headerAccessor.getUser();
        String userId = user != null ? user.getName() : "Anonymous";
        String sessionId = headerAccessor.getSessionId();

        logger.info("WebSocket connected - User: {}, Session: {}", userId, sessionId);
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        Principal user = event.getUser();
        String userId = user != null ? user.getName() : "Unknown";

        logger.info("WebSocket disconnected - User: {}, Session: {}", userId, sessionId);
    }
}
