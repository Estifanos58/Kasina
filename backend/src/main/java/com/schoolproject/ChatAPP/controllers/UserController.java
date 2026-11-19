package com.schoolproject.ChatAPP.controllers;

import com.schoolproject.ChatAPP.config.UserPrincipal;
import com.schoolproject.ChatAPP.model.User;
import com.schoolproject.ChatAPP.repository.UserRepository;
import com.schoolproject.ChatAPP.requests.UpdateProfileRequest;
import com.schoolproject.ChatAPP.responses.ErrorResponse;
import com.schoolproject.ChatAPP.responses.UpdateProfileResponse;
import com.schoolproject.ChatAPP.responses.UserInfoResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/user")
@Validated
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/info")
    public ResponseEntity<?> getUserInfo(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "Unauthorized"));
        }

        Optional<User> userOptional = userRepository.findById(principal.getUserId());
        if (userOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(HttpStatus.NOT_FOUND.value(), "User not found"));
        }

        User user = userOptional.get();
        return ResponseEntity.ok(new UserInfoResponse(
                user.getId(),
                user.getEmail(),
                user.isProfileSetup(),
                user.getFirstname(),
                user.getLastname(),
                user.getImage(),
                user.getColor()
        ));
    }

    @PostMapping("/update-profile")
    public ResponseEntity<?> updateProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest profileRequest) {
        if (principal == null || principal.getUserId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "Unauthorized"));
        }

        Optional<User> userOptional = userRepository.findById(principal.getUserId());
        if (userOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(HttpStatus.NOT_FOUND.value(), "User not found"));
        }

        User user = userOptional.get();
        user.setFirstname(profileRequest.getFirstname().trim());
        user.setLastname(profileRequest.getLastname().trim());
        user.setColor(profileRequest.getColor());
        user.setProfileSetup(true);

        if (profileRequest.getImage() != null && !profileRequest.getImage().isEmpty()) {
            String img = profileRequest.getImage().trim();
            if (img.length() > 2_000_000) {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Profile image is too large"));
            }
            String lowerImg = img.toLowerCase();
            boolean validDataUrl = lowerImg.startsWith("data:image/png;base64,")
                    || lowerImg.startsWith("data:image/jpeg;base64,")
                    || lowerImg.startsWith("data:image/jpg;base64,")
                    || lowerImg.startsWith("data:image/webp;base64,")
                    || lowerImg.startsWith("data:image/gif;base64,");
            boolean validUploadPath = img.startsWith("uploads/files/") && !img.contains("..");
            if (!validDataUrl && !validUploadPath) {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Unsupported profile image format"));
            }
            user.setImage(img);
        }

        User updatedUser = userRepository.save(user);
        logger.info("Profile updated for user: {}", updatedUser.getId());

        return ResponseEntity.ok(new UpdateProfileResponse(
                updatedUser.getId(),
                updatedUser.getEmail(),
                updatedUser.isProfileSetup(),
                updatedUser.getFirstname(),
                updatedUser.getLastname(),
                updatedUser.getImage(),
                updatedUser.getColor()
        ));
    }

    @DeleteMapping("/remove-profileImage")
    public ResponseEntity<?> removeProfile(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "Unauthorized"));
        }

        Optional<User> userOptional = userRepository.findById(principal.getUserId());
        if (userOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(HttpStatus.NOT_FOUND.value(), "User not found"));
        }

        User user = userOptional.get();
        user.setImage(null);
        userRepository.save(user);
        logger.info("Profile image removed for user: {}", user.getId());

        return ResponseEntity.ok("Image deleted");
    }
}
