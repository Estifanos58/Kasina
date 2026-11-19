package com.schoolproject.ChatAPP.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginRateLimiter {

    @Value("${security.rate-limit.login.max-attempts:5}")
    private int maxLoginAttemptsPerEmail = 5;

    @Value("${security.rate-limit.login.max-ip-attempts:20}")
    private int maxLoginAttemptsPerIp = 20;

    @Value("${security.rate-limit.signup.max-ip-attempts:10}")
    private int maxSignupAttemptsPerIp = 10;

    @Value("${security.rate-limit.window-ms:900000}")
    private long windowMs = 900_000L; // 15 minutes

    private final ConcurrentHashMap<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    public RateLimitResult checkLoginAllowed(String clientIp, String normalizedEmail) {
        long now = System.currentTimeMillis();
        String ipKey = "login:ip:" + safeKey(clientIp);
        String emailKey = "login:email:" + safeKey(clientIp) + ":" + safeKey(normalizedEmail);

        RateLimitResult ipCheck = checkKey(ipKey, maxLoginAttemptsPerIp, now);
        if (!ipCheck.isAllowed()) {
            return ipCheck;
        }
        return checkKey(emailKey, maxLoginAttemptsPerEmail, now);
    }

    public void recordLoginFailure(String clientIp, String normalizedEmail) {
        long now = System.currentTimeMillis();
        recordAttempt("login:ip:" + safeKey(clientIp), now);
        recordAttempt("login:email:" + safeKey(clientIp) + ":" + safeKey(normalizedEmail), now);
        cleanupIfNeeded(now);
    }

    public void recordLoginSuccess(String clientIp, String normalizedEmail) {
        attempts.remove("login:email:" + safeKey(clientIp) + ":" + safeKey(normalizedEmail));
    }

    public RateLimitResult checkAndRecordSignup(String clientIp) {
        long now = System.currentTimeMillis();
        String key = "signup:ip:" + safeKey(clientIp);
        RateLimitResult result = checkKey(key, maxSignupAttemptsPerIp, now);
        if (result.isAllowed()) {
            recordAttempt(key, now);
            cleanupIfNeeded(now);
        }
        return result;
    }

    private RateLimitResult checkKey(String key, int maxAttempts, long now) {
        Deque<Long> timestamps = attempts.get(key);
        if (timestamps == null) {
            return RateLimitResult.allowed();
        }
        synchronized (timestamps) {
            pruneExpired(timestamps, now);
            if (timestamps.size() >= maxAttempts) {
                Long oldest = timestamps.peekFirst();
                long retryMs = oldest != null ? Math.max(1000L, (oldest + windowMs) - now) : windowMs;
                long retrySeconds = (retryMs + 999L) / 1000L;
                return RateLimitResult.blocked(retrySeconds);
            }
        }
        return RateLimitResult.allowed();
    }

    private void recordAttempt(String key, long now) {
        Deque<Long> timestamps = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            pruneExpired(timestamps, now);
            timestamps.addLast(now);
        }
    }

    private void pruneExpired(Deque<Long> timestamps, long now) {
        long cutoff = now - windowMs;
        while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
            timestamps.pollFirst();
        }
    }

    private void cleanupIfNeeded(long now) {
        if (attempts.size() < 5000) {
            return;
        }
        Iterator<Map.Entry<String, Deque<Long>>> it = attempts.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Deque<Long>> entry = it.next();
            Deque<Long> deque = entry.getValue();
            synchronized (deque) {
                pruneExpired(deque, now);
                if (deque.isEmpty()) {
                    it.remove();
                }
            }
        }
    }

    private String safeKey(String value) {
        return value == null ? "unknown" : value.trim().toLowerCase();
    }

    public void reset() {
        attempts.clear();
    }

    public static class RateLimitResult {
        private final boolean allowed;
        private final long retryAfterSeconds;

        private RateLimitResult(boolean allowed, long retryAfterSeconds) {
            this.allowed = allowed;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public static RateLimitResult allowed() {
            return new RateLimitResult(true, 0);
        }

        public static RateLimitResult blocked(long retryAfterSeconds) {
            return new RateLimitResult(false, retryAfterSeconds);
        }

        public boolean isAllowed() {
            return allowed;
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}
