package com.schoolproject.ChatAPP.controllers;

import com.schoolproject.ChatAPP.model.User;
import com.schoolproject.ChatAPP.repository.UserRepository;
import com.schoolproject.ChatAPP.requests.LoginRequest;
import com.schoolproject.ChatAPP.requests.SignUpRequest;
import com.schoolproject.ChatAPP.responses.ErrorResponse;
import com.schoolproject.ChatAPP.responses.LoginResponse;
import com.schoolproject.ChatAPP.responses.SignUpResponse;
import com.schoolproject.ChatAPP.service.LoginRateLimiter;
import com.schoolproject.ChatAPP.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password.";
    // Valid BCrypt hash used for constant-time fallback check when email does not exist
    private static final String DUMMY_BCRYPT_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoOHi6S6v1/h2t9uG6R6hF4/T5E5V1q5iK";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired(required = false)
    private LoginRateLimiter loginRateLimiter;

    @Value("${jwt.cookie-secure:false}")
    private boolean cookieSecure;

    @Value("${jwt.cookie-same-site:Lax}")
    private String cookieSameSite;

    @Value("${security.trust-forwarded-headers:true}")
    private boolean trustForwardedHeaders = true;

    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrf(CsrfToken csrfToken) {
        String token = csrfToken != null ? csrfToken.getToken() : "";
        return ResponseEntity.ok(Map.of("token", token));
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(
            @Valid @RequestBody SignUpRequest signUpRequest,
            HttpServletRequest request,
            HttpServletResponse response) {

        String clientIp = resolveClientIp(request);
        if (loginRateLimiter != null) {
            LoginRateLimiter.RateLimitResult limitResult = loginRateLimiter.checkAndRecordSignup(clientIp);
            if (!limitResult.isAllowed()) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, String.valueOf(limitResult.getRetryAfterSeconds()))
                        .body(new ErrorResponse(
                                HttpStatus.TOO_MANY_REQUESTS.value(),
                                "Too many registration attempts. Please try again later."
                        ));
            }
        }

        String email = signUpRequest.getEmail().trim().toLowerCase();

        Optional<User> existingUser = userRepository.findByEmail(email);
        if (existingUser.isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(HttpStatus.CONFLICT.value(), "Email already exists."));
        }

        String hashedPassword = passwordEncoder.encode(signUpRequest.getPassword());
        User newUser = new User();
        newUser.setEmail(email);
        newUser.setPassword(hashedPassword);
        newUser.setProfileSetup(false);

        User savedUser;
        try {
            savedUser = userRepository.save(newUser);
        } catch (DuplicateKeyException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(HttpStatus.CONFLICT.value(), "Email already exists."));
        }

        logger.info("New user registered successfully with ID: {}", savedUser.getId());

        String jwtToken = jwtUtil.generateToken(savedUser.getEmail(), savedUser.getId());
        addJwtCookie(response, jwtToken, getCookieMaxAgeSeconds());

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new SignUpResponse(savedUser.getId(), savedUser.getEmail(), savedUser.isProfileSetup())
        );
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response) {

        String email = loginRequest.getEmail().trim().toLowerCase();
        String clientIp = resolveClientIp(request);

        if (loginRateLimiter != null) {
            LoginRateLimiter.RateLimitResult limitResult = loginRateLimiter.checkLoginAllowed(clientIp, email);
            if (!limitResult.isAllowed()) {
                logger.warn("Login rate limit exceeded for IP {}", clientIp);
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, String.valueOf(limitResult.getRetryAfterSeconds()))
                        .body(new ErrorResponse(
                                HttpStatus.TOO_MANY_REQUESTS.value(),
                                "Too many login attempts. Please try again later."
                        ));
            }
        }

        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            // Perform dummy comparison to reduce timing discrepancy for user enumeration
            passwordEncoder.matches(loginRequest.getPassword(), DUMMY_BCRYPT_HASH);
            if (loginRateLimiter != null) {
                loginRateLimiter.recordLoginFailure(clientIp, email);
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), INVALID_CREDENTIALS_MESSAGE));
        }

        User user = userOptional.get();

        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            if (loginRateLimiter != null) {
                loginRateLimiter.recordLoginFailure(clientIp, email);
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), INVALID_CREDENTIALS_MESSAGE));
        }

        if (loginRateLimiter != null) {
            loginRateLimiter.recordLoginSuccess(clientIp, email);
        }

        String jwtToken = jwtUtil.generateToken(email, user.getId());
        addJwtCookie(response, jwtToken, getCookieMaxAgeSeconds());

        logger.info("User {} logged in successfully", user.getId());

        return ResponseEntity.ok(new LoginResponse(
                user.getId(),
                user.getEmail(),
                user.isProfileSetup(),
                user.getFirstname(),
                user.getLastname(),
                user.getImage(),
                user.getColor()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logOut(HttpServletResponse response) {
        addJwtCookie(response, "", 0);
        return ResponseEntity.ok(Map.of("message", "Logout successful."));
    }

    private long getCookieMaxAgeSeconds() {
        long ms = jwtUtil != null ? jwtUtil.getExpirationTimeMs() : 86400000L;
        return Math.max(60L, ms / 1000L);
    }

    private void addJwtCookie(HttpServletResponse response, String token, long maxAgeSeconds) {
        String cookieName = jwtUtil != null ? jwtUtil.getCookieName() : "jwt";
        ResponseCookie cookie = ResponseCookie.from(cookieName, token)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(maxAgeSeconds)
                .sameSite(cookieSameSite)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        if (trustForwardedHeaders) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }
}
