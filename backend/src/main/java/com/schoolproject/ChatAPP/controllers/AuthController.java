package com.schoolproject.ChatAPP.controllers;

import com.schoolproject.ChatAPP.model.User;
import com.schoolproject.ChatAPP.repository.UserRepository;
import com.schoolproject.ChatAPP.requests.LoginRequest;
import com.schoolproject.ChatAPP.requests.SignUpRequest;
import com.schoolproject.ChatAPP.responses.ErrorResponse;
import com.schoolproject.ChatAPP.responses.LoginResponse;
import com.schoolproject.ChatAPP.responses.SignUpResponse;
import com.schoolproject.ChatAPP.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${jwt.cookie-secure:false}")
    private boolean cookieSecure;

    @Value("${jwt.cookie-same-site:Lax}")
    private String cookieSameSite;

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignUpRequest signUpRequest, HttpServletResponse response) {
        String email = signUpRequest.getEmail().trim().toLowerCase();

        Optional<User> existingUser = userRepository.findByEmail(email);
        if (existingUser.isPresent()) {
            return ResponseEntity.badRequest().body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Email already exists."));
        }

        String hashedPassword = passwordEncoder.encode(signUpRequest.getPassword());
        User newUser = new User();
        newUser.setEmail(email);
        newUser.setPassword(hashedPassword);
        newUser.setProfileSetup(false);

        User savedUser = userRepository.save(newUser);
        logger.info("New user registered successfully with ID: {}", savedUser.getId());

        String jwtToken = jwtUtil.generateToken(savedUser.getEmail(), savedUser.getId());
        addJwtCookie(response, jwtToken, 86400);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new SignUpResponse(savedUser.getId(), savedUser.getEmail(), savedUser.isProfileSetup())
        );
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletResponse response) {
        String email = loginRequest.getEmail().trim().toLowerCase();

        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(HttpStatus.NOT_FOUND.value(), "User not found."));
        }

        User user = userOptional.get();

        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            return ResponseEntity.badRequest().body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Password is incorrect."));
        }

        String jwtToken = jwtUtil.generateToken(email, user.getId());
        addJwtCookie(response, jwtToken, 86400);

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
        // Clear the JWT cookie
        addJwtCookie(response, "", 0);
        return ResponseEntity.ok("Logout Successful.");
    }

    private void addJwtCookie(HttpServletResponse response, String token, int maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from("jwt", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(maxAgeSeconds)
                .sameSite(cookieSameSite)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }
}
