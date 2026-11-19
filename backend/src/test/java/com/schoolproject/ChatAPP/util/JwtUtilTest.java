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
        ReflectionTestUtils.setField(jwtUtil, "expirationTimeMs", 3600000L);
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

    @Test
    void testMissingJwtSecretFailsStartup() {
        JwtUtil unconfigured = new JwtUtil();
        ReflectionTestUtils.setField(unconfigured, "configuredSecret", "");
        assertThrows(IllegalStateException.class, unconfigured::init);
    }

    @Test
    void testShortJwtSecretFailsStartup() {
        JwtUtil weak = new JwtUtil();
        ReflectionTestUtils.setField(weak, "configuredSecret", "too-short-secret");
        assertThrows(IllegalStateException.class, weak::init);
    }

    @Test
    void testPlaceholderJwtSecretFailsStartup() {
        JwtUtil placeholder = new JwtUtil();
        ReflectionTestUtils.setField(placeholder, "configuredSecret", "kasina-jwt-secret-key-change-in-production-min-32-chars");
        assertThrows(IllegalStateException.class, placeholder::init);
    }
}
