package com.schoolproject.ChatAPP.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

@Component
public class JwtUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    private static final int MIN_SECRET_LENGTH = 32;

    @Value("${jwt.secret:}")
    private String configuredSecret;

    @Value("${jwt.expiration-ms:86400000}")
    private long expirationTimeMs;

    @Value("${jwt.cookie-name:jwt}")
    private String cookieName = "jwt";

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        if (configuredSecret == null || configuredSecret.trim().isEmpty()) {
            throw new IllegalStateException(
                    "JWT_SECRET (jwt.secret) must be configured and cannot be blank."
            );
        }

        String secret = configuredSecret.trim();
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "JWT_SECRET (jwt.secret) must be at least " + MIN_SECRET_LENGTH + " bytes long."
            );
        }

        String lower = secret.toLowerCase();
        if (lower.contains("your-random-jwt-secret")
                || lower.contains("change-in-production")
                || lower.contains("replace-in-production")
                || lower.contains("kasina-default-dev-secret")) {
            throw new IllegalStateException(
                    "JWT_SECRET (jwt.secret) cannot use a placeholder or default value."
            );
        }

        try {
            // Derive a consistent 256-bit HMAC key from the validated secret
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(secretBytes);
            this.secretKey = Keys.hmacShaKeyFor(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to initialize JWT secret key", e);
        }
    }

    public long getExpirationTimeMs() {
        return expirationTimeMs;
    }

    public String getCookieName() {
        return cookieName != null && !cookieName.isBlank() ? cookieName : "jwt";
    }

    /**
     * Generate JWT token with email and userId.
     */
    public String generateToken(String email, String userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationTimeMs);

        return Jwts.builder()
                .setSubject(email)
                .claim("userId", userId)
                .claim("email", email)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    /**
     * Extract userId from JWT token.
     */
    public String extractUserId(String token) {
        Claims claims = getClaims(token);
        return claims != null ? claims.get("userId", String.class) : null;
    }

    /**
     * Extract email/subject from JWT token.
     */
    public String extractEmail(String token) {
        Claims claims = getClaims(token);
        return claims != null ? claims.getSubject() : null;
    }

    /**
     * Extract JWT from request (checks Authorization header first, then jwt cookie).
     */
    public String getJwtFromRequest(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        // 1. Check Authorization Bearer header
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        // 2. Check jwt cookie
        Cookie jwtCookie = WebUtils.getCookie(request, getCookieName());
        if (jwtCookie != null && jwtCookie.getValue() != null && !jwtCookie.getValue().isEmpty()) {
            return jwtCookie.getValue();
        }

        return null;
    }

    public boolean hasJwtCookie(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        Cookie jwtCookie = WebUtils.getCookie(request, getCookieName());
        return jwtCookie != null && jwtCookie.getValue() != null && !jwtCookie.getValue().isEmpty();
    }

    /**
     * Validate JWT token.
     */
    public boolean validateToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }
        try {
            Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            logger.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    private Claims getClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            logger.debug("Failed to parse JWT claims: {}", e.getMessage());
            return null;
        }
    }

    public SecretKey getSecretKey() {
        return secretKey;
    }
}
