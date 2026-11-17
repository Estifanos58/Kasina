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

    @Value("${jwt.secret:}")
    private String configuredSecret;

    @Value("${jwt.expiration-ms:86400000}")
    private long expirationTimeMs;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        String secret = configuredSecret;
        if (secret == null || secret.trim().isEmpty()) {
            logger.warn("No JWT secret provided in configuration! Using internal development key.");
            secret = "kasina-default-dev-secret-key-replace-in-production-environment-32bytes";
        }
        try {
            // Ensure minimum 256-bit key length via SHA-256 derivation
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            this.secretKey = Keys.hmacShaKeyFor(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to initialize JWT secret key", e);
        }
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
        Cookie jwtCookie = WebUtils.getCookie(request, "jwt");
        if (jwtCookie != null && jwtCookie.getValue() != null && !jwtCookie.getValue().isEmpty()) {
            return jwtCookie.getValue();
        }

        return null;
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
