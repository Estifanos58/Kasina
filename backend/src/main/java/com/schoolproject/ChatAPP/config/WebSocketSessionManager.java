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
        if (userId == null || userId.isBlank() || sessionId == null || sessionId.isBlank()) {
            return;
        }
        String previousUser = sessionToUser.put(sessionId, userId);
        if (previousUser != null && !previousUser.equals(userId)) {
            removeSessionFromUserMap(previousUser, sessionId);
        }
        userSessions.compute(userId, (key, existing) -> {
            Set<String> set = existing != null ? existing : ConcurrentHashMap.newKeySet();
            set.add(sessionId);
            return set;
        });
        logger.debug("Session added: user={} session={}", userId, sessionId);
    }

    public String removeSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        String userId = sessionToUser.remove(sessionId);
        if (userId != null) {
            removeSessionFromUserMap(userId, sessionId);
            logger.debug("Session removed: user={} session={}", userId, sessionId);
        }
        return userId;
    }

    private void removeSessionFromUserMap(String userId, String sessionId) {
        userSessions.computeIfPresent(userId, (key, sessions) -> {
            sessions.remove(sessionId);
            return sessions.isEmpty() ? null : sessions;
        });
    }

    public Set<String> getSessions(String userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        Set<String> sessions = userSessions.get(userId);
        return sessions != null ? Collections.unmodifiableSet(sessions) : Collections.emptySet();
    }

    public boolean isUserOnline(String userId) {
        if (userId == null) {
            return false;
        }
        Set<String> sessions = userSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    public int getActiveSessionCount() {
        return sessionToUser.size();
    }

    public int getOnlineUserCount() {
        return userSessions.size();
    }
}
