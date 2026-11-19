package com.schoolproject.ChatAPP.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

@Component
public class WebSocketEventListener {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketEventListener.class);

    private final WebSocketSessionManager sessionManager;

    @Autowired
    public WebSocketEventListener(WebSocketSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @EventListener
    public void handleWebSocketConnectedListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = event.getUser() != null ? event.getUser() : headerAccessor.getUser();
        String sessionId = headerAccessor.getSessionId();

        if (user != null && user.getName() != null && sessionId != null) {
            sessionManager.addSession(user.getName(), sessionId);
            logger.info("WebSocket connected - User: {}, Session: {}, ActiveSessions: {}",
                    user.getName(), sessionId, sessionManager.getActiveSessionCount());
        } else {
            logger.warn("WebSocket connected event missing user or sessionId (session={})", sessionId);
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        String removedUserId = sessionManager.removeSession(sessionId);
        Principal user = event.getUser();
        String userId = removedUserId != null ? removedUserId : (user != null ? user.getName() : "Unknown");

        logger.info("WebSocket disconnected - User: {}, Session: {}, ActiveSessions: {}",
                userId, sessionId, sessionManager.getActiveSessionCount());
    }
}
