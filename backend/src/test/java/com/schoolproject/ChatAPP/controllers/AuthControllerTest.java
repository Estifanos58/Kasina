package com.schoolproject.ChatAPP.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolproject.ChatAPP.model.User;
import com.schoolproject.ChatAPP.repository.UserRepository;
import com.schoolproject.ChatAPP.requests.LoginRequest;
import com.schoolproject.ChatAPP.requests.SignUpRequest;
import com.schoolproject.ChatAPP.service.LoginRateLimiter;
import com.schoolproject.ChatAPP.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private PasswordEncoder passwordEncoder;

    private LoginRateLimiter loginRateLimiter;

    @InjectMocks
    private AuthController authController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        loginRateLimiter = new LoginRateLimiter();
        ReflectionTestUtils.setField(loginRateLimiter, "maxLoginAttemptsPerEmail", 3);
        ReflectionTestUtils.setField(loginRateLimiter, "maxLoginAttemptsPerIp", 10);
        ReflectionTestUtils.setField(loginRateLimiter, "windowMs", 60_000L);

        ReflectionTestUtils.setField(authController, "loginRateLimiter", loginRateLimiter);
        ReflectionTestUtils.setField(authController, "cookieSecure", false);
        ReflectionTestUtils.setField(authController, "cookieSameSite", "Lax");

        lenient().when(jwtUtil.getCookieName()).thenReturn("jwt");
        lenient().when(jwtUtil.getExpirationTimeMs()).thenReturn(86400000L);

        mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
    }

    @Test
    void testSignupSuccess() throws Exception {
        SignUpRequest request = new SignUpRequest("user@test.com", "password123");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("hashedPassword");

        User savedUser = new User();
        savedUser.setId("u123");
        savedUser.setEmail("user@test.com");
        savedUser.setPassword("hashedPassword");
        savedUser.setProfileSetup(false);

        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtUtil.generateToken("user@test.com", "u123")).thenReturn("mock-jwt-token");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("u123"))
                .andExpect(jsonPath("$.email").value("user@test.com"))
                .andExpect(jsonPath("$.profileSetup").value(false))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(header().exists("Set-Cookie"));

        verify(userRepository).save(any(User.class));
    }

    @Test
    void testSignupDuplicateEmailReturnsConflict() throws Exception {
        SignUpRequest request = new SignUpRequest("existing@test.com", "password123");
        User existingUser = new User();
        existingUser.setEmail("existing@test.com");
        when(userRepository.findByEmail("existing@test.com")).thenReturn(Optional.of(existingUser));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already exists."));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testLoginSuccess() throws Exception {
        LoginRequest request = new LoginRequest("user@test.com", "secret123");

        User user = new User();
        user.setId("u456");
        user.setEmail("user@test.com");
        user.setPassword("hashedSecret");
        user.setFirstname("Alice");
        user.setLastname("Smith");
        user.setColor(2);
        user.setImage("avatar.png");
        user.setProfileSetup(true);

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "hashedSecret")).thenReturn(true);
        when(jwtUtil.generateToken("user@test.com", "u456")).thenReturn("mock-login-token");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("u456"))
                .andExpect(jsonPath("$.email").value("user@test.com"))
                .andExpect(jsonPath("$.firstname").value("Alice"))
                .andExpect(jsonPath("$.firstName").value("Alice"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(header().exists("Set-Cookie"));
    }

    @Test
    void testLoginWrongPasswordAndUnknownUserReturnConsistentUnauthorizedError() throws Exception {
        LoginRequest wrongPassReq = new LoginRequest("user@test.com", "wrongPass");
        User user = new User();
        user.setEmail("user@test.com");
        user.setPassword("hashedSecret");

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPass", "hashedSecret")).thenReturn(false);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongPassReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password."));

        LoginRequest unknownReq = new LoginRequest("nobody@test.com", "anyPass");
        when(userRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unknownReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    void testLoginRateLimitingBlocksAfterMaxFailedAttempts() throws Exception {
        LoginRequest request = new LoginRequest("victim@test.com", "wrongPass");
        when(userRepository.findByEmail("victim@test.com")).thenReturn(Optional.empty());

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .header("X-Forwarded-For", "203.0.113.10")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        // 4th attempt should be rate limited with 429 and Retry-After header
        mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", "203.0.113.10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").value("Too many login attempts. Please try again later."));
    }

    @Test
    void testLogoutClearsCookie() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        String setCookie = response.getHeader("Set-Cookie");
        assertNotNull(setCookie);
        assertTrue(setCookie.contains("Max-Age=0") || setCookie.contains("max-age=0") || setCookie.contains("jwt="));
    }
}
