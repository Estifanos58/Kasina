package com.schoolproject.ChatAPP.config;

import com.schoolproject.ChatAPP.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Extracts and cryptographically validates JWT credentials from HTTP handshake
 * cookies (HttpOnly jwt cookie) or Authorization header.
 */
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    public static final String VERIFIED_PRINCIPAL_ATTR = "kasina.verifiedUserPrincipal";

    private static final Logger logger = LoggerFactory.getLogger(JwtHandshakeInterceptor.class);

    private final JwtUtil jwtUtil;

    @Autowired
    public JwtHandshakeInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        if (request instanceof ServletServerHttpRequest servletRequestWrapper) {
            HttpServletRequest servletRequest = servletRequestWrapper.getServletRequest();
            String token = jwtUtil.getJwtFromRequest(servletRequest);

            if (token != null && !token.isBlank()) {
                if (!jwtUtil.validateToken(token)) {
                    logger.warn("Rejecting WebSocket handshake with invalid or expired JWT");
                    response.setStatusCode(HttpStatus.UNAUTHORIZED);
                    return false;
                }

                String userId = jwtUtil.extractUserId(token);
                String email = jwtUtil.extractEmail(token);
                if (userId == null || userId.isBlank()) {
                    logger.warn("Rejecting WebSocket handshake: JWT missing userId claim");
                    response.setStatusCode(HttpStatus.UNAUTHORIZED);
                    return false;
                }

                attributes.put(VERIFIED_PRINCIPAL_ATTR, new UserPrincipal(userId, email != null ? email : userId));
            }
        }

        // Allow handshake to proceed; STOMP CONNECT will still require either this verified
        // principal or a cryptographically valid JWT in STOMP native headers.
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // No-op
    }
}
