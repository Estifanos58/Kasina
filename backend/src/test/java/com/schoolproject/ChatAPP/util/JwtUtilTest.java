package com.schoolproject.ChatAPP.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "configuredSecret", "a-very-long-and-secure-test-jwt-secret-key-32-characters!");
        ReflectionTestUtils.setField(jwtUtil, "expirationTimeMs", 3600000L); // 1 hour
        jwtUtil.init();
    }

    @Test
    void testGenerateAndValidateToken() {
        String email = "test@example.com";
        String userId = "user123";

        String token = jwtUtil.generateToken(email, userId);
        assertNotNull(token);
        assertTrue(jwtUtil.validateToken(token));

        assertEquals(userId, jwtUtil.extractUserId(token));
        assertEquals(email, jwtUtil.extractEmail(token));
    }

    @Test
    void testInvalidToken() {
        assertFalse(jwtUtil.validateToken("invalid.token.here"));
        assertFalse(jwtUtil.validateToken(null));
        assertFalse(jwtUtil.validateToken(""));
    }

    @Test
    void testTamperedToken() {
        String token = jwtUtil.generateToken("user@test.com", "id1");
        String tampered = token.substring(0, token.length() - 5) + "abcde";
        assertFalse(jwtUtil.validateToken(tampered));
    }
}
