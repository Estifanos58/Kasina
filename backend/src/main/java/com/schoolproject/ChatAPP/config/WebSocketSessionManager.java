package com.schoolproject.ChatAPP.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionManager {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketSessionManager.class);

    // Multi-session map: userId -> Set of sessionIds
    private final ConcurrentHashMap<String, Set<String>> userSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionToUser = new ConcurrentHashMap<>();

    public void addSession(String userId, String sessionId) {
        if (userId == null || sessionId == null) return;
        userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        sessionToUser.put(sessionId, userId);
        logger.debug("Session added: user={} session={}", userId, sessionId);
    }

    public void removeSession(String sessionId) {
        if (sessionId == null) return;
        String userId = sessionToUser.remove(sessionId);
        if (userId != null) {
            Set<String> sessions = userSessions.get(userId);
            if (sessions != null) {
                sessions.remove(sessionId);
                if (sessions.isEmpty()) {
                    userSessions.remove(userId);
                }
            }
            logger.debug("Session removed: user={} session={}", userId, sessionId);
        }
    }

    public Set<String> getSessions(String userId) {
        if (userId == null) return Collections.emptySet();
        return userSessions.getOrDefault(userId, Collections.emptySet());
    }

    public boolean isUserOnline(String userId) {
        return userId != null && userSessions.containsKey(userId) && !userSessions.get(userId).isEmpty();
    }
}
