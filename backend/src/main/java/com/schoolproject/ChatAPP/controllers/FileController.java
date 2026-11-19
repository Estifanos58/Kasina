package com.schoolproject.ChatAPP.controllers;

import com.schoolproject.ChatAPP.config.UserPrincipal;
import com.schoolproject.ChatAPP.model.Attachment;
import com.schoolproject.ChatAPP.model.Channel;
import com.schoolproject.ChatAPP.model.Message;
import com.schoolproject.ChatAPP.repository.AttachmentRepository;
import com.schoolproject.ChatAPP.repository.ChannelRepository;
import com.schoolproject.ChatAPP.repository.MessageRepository;
import com.schoolproject.ChatAPP.responses.ErrorResponse;
import com.schoolproject.ChatAPP.service.FileValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
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

    @Autowired
    private FileValidationService fileValidationService;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ChannelRepository channelRepository;

    @PostMapping("/api/messages/upload-file")
    public ResponseEntity<?> uploadFile(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "recipientId", required = false) String recipientId,
            @RequestParam(value = "channelId", required = false) String channelId) {

        if (principal == null || principal.getUserId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "Unauthorized"));
        }

        String userId = principal.getUserId();

        // If targeting a channel, verify membership before accepting upload
        String normalizedChannelId = (channelId != null && !channelId.isBlank()) ? channelId.trim() : null;
        String normalizedRecipientId = (recipientId != null && !recipientId.isBlank()) ? recipientId.trim() : null;

        if (normalizedChannelId != null && !isChannelMember(normalizedChannelId, userId)) {
            logger.warn("User {} attempted to upload attachment to unauthorized channel {}", userId, normalizedChannelId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(HttpStatus.FORBIDDEN.value(), "You are not a member of this channel"));
        }

        FileValidationService.ValidatedFile validated;
        try {
            validated = fileValidationService.validate(file);
        } catch (IllegalArgumentException e) {
            logger.warn("User {} file upload rejected: {}", userId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), e.getMessage()));
        } catch (IOException e) {
            logger.error("Failed to inspect uploaded file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Failed to process uploaded file"));
        }

        try {
            String dateDir = String.valueOf(System.currentTimeMillis());
            Path baseDir = Paths.get(uploadDir).normalize().toAbsolutePath();
            Path targetDir = baseDir.resolve(dateDir).normalize();
            if (!targetDir.startsWith(baseDir)) {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Invalid file destination"));
            }
            Files.createDirectories(targetDir);

            String uniqueFilename = UUID.randomUUID() + "_" + validated.getSanitizedFilename();
            Path targetPath = targetDir.resolve(uniqueFilename).normalize();

            if (!targetPath.startsWith(targetDir)) {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Invalid file destination"));
            }

            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            String relativeUrl = "uploads/files/" + dateDir + "/" + uniqueFilename;

            Attachment attachment = new Attachment(
                    relativeUrl,
                    validated.getSanitizedFilename(),
                    uniqueFilename,
                    validated.getMimeType(),
                    file.getSize(),
                    userId,
                    normalizedChannelId == null ? normalizedRecipientId : null,
                    normalizedChannelId
            );
            attachmentRepository.save(attachment);

            logger.info("File uploaded successfully by user {}: {} ({})", userId, uniqueFilename, validated.getMimeType());

            Map<String, Object> response = new HashMap<>();
            response.put("filePath", relativeUrl);
            response.put("fileName", validated.getSanitizedFilename());
            response.put("contentType", validated.getMimeType());
            response.put("size", file.getSize());
            response.put("message", "File uploaded successfully");
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            logger.error("Failed to store uploaded file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Failed to store uploaded file"));
        }
    }

    @GetMapping({
            "/uploads/files/{dateDir}/{fileName:.+}",
            "/api/messages/file/{dateDir}/{fileName:.+}",
            "/api/messages/download-file"
    })
    public ResponseEntity<?> serveFile(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable(value = "dateDir", required = false) String pathDateDir,
            @PathVariable(value = "fileName", required = false) String pathFileName,
            @RequestParam(value = "path", required = false) String queryPath) {

        if (principal == null || principal.getUserId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "Unauthorized"));
        }

        String dateDir = pathDateDir;
        String fileName = pathFileName;

        if (queryPath != null && !queryPath.trim().isEmpty()) {
            String clean = queryPath.replace("\\", "/").replaceAll("^/+", "");
            if (clean.contains("..")) {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Invalid file path"));
            }
            if (clean.startsWith("uploads/files/")) {
                clean = clean.substring("uploads/files/".length());
            }
            String[] parts = clean.split("/");
            if (parts.length != 2) {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Invalid file path"));
            }
            dateDir = parts[0];
            fileName = parts[1];
        }

        if (isInvalidSegment(dateDir) || isInvalidSegment(fileName)) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Invalid file path"));
        }

        String canonicalRelativePath = "uploads/files/" + dateDir + "/" + fileName;
        String userId = principal.getUserId();

        Optional<Attachment> attachmentOpt = attachmentRepository.findByFilePath(canonicalRelativePath);
        List<Message> linkedMessages = messageRepository.findByFileUrl(canonicalRelativePath);

        if (!isUserAuthorizedForFile(userId, attachmentOpt.orElse(null), linkedMessages)) {
            logger.warn("User {} denied access to private attachment {}", userId, canonicalRelativePath);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(HttpStatus.FORBIDDEN.value(), "Not authorized to access this attachment"));
        }

        try {
            Path baseDirPath = Paths.get(uploadDir).normalize().toAbsolutePath();
            Path filePath = baseDirPath.resolve(dateDir).resolve(fileName).normalize();

            if (!filePath.startsWith(baseDirPath) || !Files.exists(filePath) || !Files.isReadable(filePath)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse(HttpStatus.NOT_FOUND.value(), "Attachment not found"));
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse(HttpStatus.NOT_FOUND.value(), "Attachment not found"));
            }

            String contentType = attachmentOpt
                    .map(Attachment::getContentType)
                    .filter(ct -> ct != null && !ct.isBlank())
                    .orElse("application/octet-stream");

            String downloadName = attachmentOpt
                    .map(Attachment::getOriginalFileName)
                    .filter(n -> n != null && !n.isBlank())
                    .orElse(resource.getFilename() != null ? resource.getFilename() : "attachment");

            boolean inlineSafe = fileValidationService.isInlineSafe(contentType);
            ContentDisposition disposition = (inlineSafe ? ContentDisposition.inline() : ContentDisposition.attachment())
                    .filename(downloadName, StandardCharsets.UTF_8)
                    .build();

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .cacheControl(CacheControl.noStore().cachePrivate())
                    .header("X-Content-Type-Options", "nosniff")
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .body(resource);

        } catch (MalformedURLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Failed to read attachment"));
        }
    }

    private boolean isInvalidSegment(String segment) {
        return segment == null
                || segment.isBlank()
                || segment.contains("..")
                || segment.contains("/")
                || segment.contains("\\");
    }

    private boolean isUserAuthorizedForFile(String userId, Attachment attachment, List<Message> linkedMessages) {
        // 1. Check authorization via messages referencing this attachment
        if (linkedMessages != null && !linkedMessages.isEmpty()) {
            for (Message msg : linkedMessages) {
                if (msg == null) continue;
                if (msg.getChannelId() != null && !msg.getChannelId().isBlank()) {
                    if (isChannelMember(msg.getChannelId(), userId)) {
                        return true;
                    }
                } else {
                    if (userId.equals(msg.getSender()) || userId.equals(msg.getRecipient())) {
                        return true;
                    }
                }
            }
            return false;
        }

        // 2. Fallback to Attachment metadata (e.g., pending upload before message send or bound on upload)
        if (attachment != null) {
            if (attachment.getChannelId() != null && !attachment.getChannelId().isBlank()) {
                return isChannelMember(attachment.getChannelId(), userId);
            }
            if (userId.equals(attachment.getUploaderId())) {
                return true;
            }
            if (attachment.getRecipientId() != null && userId.equals(attachment.getRecipientId())) {
                return true;
            }
        }

        // 3. Untracked file on disk: deny by default
        return false;
    }

    private boolean isChannelMember(String channelId, String userId) {
        Optional<Channel> channelOpt = channelRepository.findById(channelId);
        if (channelOpt.isEmpty()) {
            return false;
        }
        Channel channel = channelOpt.get();
        return (channel.getAdmin() != null && userId.equals(channel.getAdmin().getId()))
                || (channel.getMembers() != null && channel.getMembers().stream().anyMatch(m -> m != null && userId.equals(m.getId())));
    }
}
