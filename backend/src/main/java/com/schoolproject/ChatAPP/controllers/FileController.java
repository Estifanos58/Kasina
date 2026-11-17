package com.schoolproject.ChatAPP.controllers;

import com.schoolproject.ChatAPP.config.UserPrincipal;
import com.schoolproject.ChatAPP.responses.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

@RestController
public class FileController {

    private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    @Value("${file.upload-dir:uploads/files}")
    private String uploadDir;

    private static final Set<String> DISALLOWED_EXTENSIONS = Set.of(
            "exe", "bat", "cmd", "sh", "php", "jsp", "asp", "aspx", "jar", "war", "py", "pl", "vbs", "html", "htm", "js", "msi"
    );

    @PostMapping("/api/messages/upload-file")
    public ResponseEntity<?> uploadFile(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("file") MultipartFile file) {

        if (principal == null || principal.getUserId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "Unauthorized"));
        }

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "File is required and cannot be empty"));
        }

        String rawOriginalFilename = file.getOriginalFilename();
        if (rawOriginalFilename == null || rawOriginalFilename.trim().isEmpty()) {
            rawOriginalFilename = "attachment";
        }

        // Sanitize filename: strip paths, allow only safe characters
        String cleanOriginalName = Paths.get(rawOriginalFilename).getFileName().toString();
        cleanOriginalName = cleanOriginalName.replaceAll("[^a-zA-Z0-9._-]", "_");

        // Validate extension
        String extension = "";
        int dotIndex = cleanOriginalName.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = cleanOriginalName.substring(dotIndex + 1).toLowerCase();
        }

        if (DISALLOWED_EXTENSIONS.contains(extension)) {
            logger.warn("User {} attempted to upload disallowed file type: {}", principal.getUserId(), extension);
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "File type not permitted for upload"));
        }

        try {
            String dateDir = String.valueOf(System.currentTimeMillis());
            Path targetDir = Paths.get(uploadDir, dateDir).normalize().toAbsolutePath();
            Files.createDirectories(targetDir);

            String uniqueFilename = UUID.randomUUID().toString() + "_" + cleanOriginalName;
            Path targetPath = targetDir.resolve(uniqueFilename).normalize();

            // Prevent path traversal
            if (!targetPath.startsWith(targetDir)) {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Invalid file destination"));
            }

            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            logger.info("File uploaded successfully by user {}: {}", principal.getUserId(), uniqueFilename);

            // Construct relative web-accessible URL
            String relativeUrl = "uploads/files/" + dateDir + "/" + uniqueFilename;

            Map<String, Object> response = new HashMap<>();
            response.put("filePath", relativeUrl);
            response.put("message", "File uploaded successfully");
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            logger.error("Failed to store uploaded file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Failed to store uploaded file"));
        }
    }

    // Serve / download files
    @GetMapping({"/uploads/files/{dateDir}/{fileName:.+}", "/api/messages/download-file"})
    public ResponseEntity<Resource> serveFile(
            @PathVariable(value = "dateDir", required = false) String pathDateDir,
            @PathVariable(value = "fileName", required = false) String pathFileName,
            @RequestParam(value = "path", required = false) String queryPath) {

        String dateDir = pathDateDir;
        String fileName = pathFileName;

        if (queryPath != null && !queryPath.trim().isEmpty()) {
            // Strip leading slashes or "uploads/files/"
            String clean = queryPath.replace("\\", "/").replaceAll("^/+", "");
            if (clean.startsWith("uploads/files/")) {
                clean = clean.substring("uploads/files/".length());
            }
            String[] parts = clean.split("/");
            if (parts.length >= 2) {
                dateDir = parts[0];
                fileName = parts[parts.length - 1];
            }
        }

        if (dateDir == null || fileName == null || dateDir.contains("..") || fileName.contains("..")) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Path baseDirPath = Paths.get(uploadDir).normalize().toAbsolutePath();
            Path filePath = baseDirPath.resolve(dateDir).resolve(fileName).normalize();

            // Check that the resolved path is within baseDirPath
            if (!filePath.startsWith(baseDirPath) || !Files.exists(filePath) || !Files.isReadable(filePath)) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }

            String contentType = null;
            try {
                contentType = Files.probeContentType(filePath);
            } catch (IOException ignored) {}

            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);

        } catch (MalformedURLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
