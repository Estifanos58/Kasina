package com.schoolproject.ChatAPP.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Validates uploaded attachments using an explicit extension allowlist,
 * MIME type validation, and binary magic-byte file signature inspection.
 */
@Service
public class FileValidationService {

    private static final int SIGNATURE_PROBE_BYTES = 4096;

    private static final Map<String, String> ALLOWED_EXTENSIONS_TO_MIME = Map.ofEntries(
            // Safe raster images (SVG intentionally excluded to prevent stored XSS)
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            // Documents & text
            Map.entry("pdf", "application/pdf"),
            Map.entry("txt", "text/plain"),
            Map.entry("csv", "text/csv"),
            Map.entry("md", "text/markdown"),
            Map.entry("json", "application/json"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("zip", "application/zip"),
            Map.entry("doc", "application/msword"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            // Media
            Map.entry("mp3", "audio/mpeg"),
            Map.entry("wav", "audio/wav"),
            Map.entry("ogg", "audio/ogg"),
            Map.entry("mp4", "video/mp4"),
            Map.entry("m4a", "audio/mp4"),
            Map.entry("webm", "video/webm")
    );

    private static final Set<String> INLINE_SAFE_MIME_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/gif",
            "image/webp"
    );

    private static final Set<String> TEXT_EXTENSIONS = Set.of("txt", "csv", "md", "json");
    private static final Set<String> ZIP_BASED_EXTENSIONS = Set.of("zip", "docx", "xlsx", "pptx");
    private static final Set<String> OLE2_EXTENSIONS = Set.of("doc", "xls", "ppt");

    public ValidatedFile validate(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required and cannot be empty");
        }

        String rawName = file.getOriginalFilename();
        if (rawName == null || rawName.trim().isEmpty()) {
            throw new IllegalArgumentException("File name is required");
        }

        // Strip directory segments and sanitize filename
        String baseName = Paths.get(rawName.replace("\\", "/")).getFileName().toString();
        String cleanName = baseName.replaceAll("[^a-zA-Z0-9._-]", "_");
        while (cleanName.startsWith(".")) {
            cleanName = cleanName.substring(1);
        }
        if (cleanName.isEmpty()) {
            throw new IllegalArgumentException("Invalid file name");
        }

        int dotIndex = cleanName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == cleanName.length() - 1) {
            throw new IllegalArgumentException("File must have an allowed extension");
        }

        String extension = cleanName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        String canonicalMime = ALLOWED_EXTENSIONS_TO_MIME.get(extension);
        if (canonicalMime == null) {
            throw new IllegalArgumentException("File type not permitted for upload");
        }

        // Reject blatantly dangerous declared Content-Types (e.g. HTML/JS/SVG/executables)
        String declaredContentType = file.getContentType();
        if (declaredContentType != null && !declaredContentType.isBlank()) {
            String normalizedDeclared = declaredContentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
            if (isDangerousDeclaredMime(normalizedDeclared)) {
                throw new IllegalArgumentException("File content type not permitted for upload");
            }
        }

        byte[] header = readHeaderBytes(file);
        if (header.length == 0) {
            throw new IllegalArgumentException("File is required and cannot be empty");
        }

        // Reject executable binary signatures unconditionally
        if (isExecutableSignature(header)) {
            throw new IllegalArgumentException("Executable file content is not permitted");
        }

        // Verify actual file content matches the allowed extension signature
        if (!matchesExpectedSignature(extension, header)) {
            throw new IllegalArgumentException("File content does not match the declared file type");
        }

        boolean inlineSafe = INLINE_SAFE_MIME_TYPES.contains(canonicalMime);
        return new ValidatedFile(cleanName, extension, canonicalMime, inlineSafe);
    }

    public boolean isInlineSafe(String mimeType) {
        return mimeType != null && INLINE_SAFE_MIME_TYPES.contains(mimeType.toLowerCase(Locale.ROOT));
    }

    private byte[] readHeaderBytes(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream()) {
            byte[] buffer = new byte[SIGNATURE_PROBE_BYTES];
            int totalRead = 0;
            int read;
            while (totalRead < buffer.length && (read = is.read(buffer, totalRead, buffer.length - totalRead)) != -1) {
                totalRead += read;
            }
            if (totalRead == buffer.length) {
                return buffer;
            }
            byte[] exact = new byte[totalRead];
            System.arraycopy(buffer, 0, exact, 0, totalRead);
            return exact;
        }
    }

    private boolean isDangerousDeclaredMime(String mime) {
        return mime.contains("html")
                || mime.contains("javascript")
                || mime.contains("ecmascript")
                || mime.contains("svg")
                || mime.contains("x-msdownload")
                || mime.contains("x-sh")
                || mime.contains("x-executable")
                || mime.contains("x-dosexec")
                || mime.contains("x-php")
                || mime.contains("java-archive");
    }

    private boolean isExecutableSignature(byte[] b) {
        // Windows PE / MZ
        if (b.length >= 2 && b[0] == 0x4D && b[1] == 0x5A) return true;
        // Linux ELF
        if (b.length >= 4 && b[0] == 0x7F && b[1] == 'E' && b[2] == 'L' && b[3] == 'F') return true;
        // Mach-O / Java class
        if (b.length >= 4 && ((b[0] == (byte) 0xCA && b[1] == (byte) 0xFE && b[2] == (byte) 0xBA && b[3] == (byte) 0xBE)
                || (b[0] == (byte) 0xCF && b[1] == (byte) 0xFA && b[2] == (byte) 0xED && b[3] == (byte) 0xFE)
                || (b[0] == (byte) 0xFE && b[1] == (byte) 0xED && b[2] == (byte) 0xFA && b[3] == (byte) 0xCF))) {
            return true;
        }
        // Shebang script (#!)
        if (b.length >= 2 && b[0] == '#' && b[1] == '!') return true;
        return false;
    }

    private boolean matchesExpectedSignature(String ext, byte[] b) {
        if ("png".equals(ext)) {
            return b.length >= 8
                    && (b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47
                    && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A;
        }
        if ("jpg".equals(ext) || "jpeg".equals(ext)) {
            return b.length >= 3
                    && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
        }
        if ("gif".equals(ext)) {
            return b.length >= 6
                    && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8'
                    && (b[4] == '7' || b[4] == '9') && b[5] == 'a';
        }
        if ("webp".equals(ext)) {
            return b.length >= 12
                    && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                    && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
        }
        if ("pdf".equals(ext)) {
            return b.length >= 5
                    && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-';
        }
        if (ZIP_BASED_EXTENSIONS.contains(ext)) {
            return b.length >= 4
                    && b[0] == 0x50 && b[1] == 0x4B
                    && ((b[2] == 0x03 && b[3] == 0x04)
                    || (b[2] == 0x05 && b[3] == 0x06)
                    || (b[2] == 0x07 && b[3] == 0x08));
        }
        if (OLE2_EXTENSIONS.contains(ext)) {
            return b.length >= 8
                    && (b[0] & 0xFF) == 0xD0 && (b[1] & 0xFF) == 0xCF
                    && (b[2] & 0xFF) == 0x11 && (b[3] & 0xFF) == 0xE0
                    && (b[4] & 0xFF) == 0xA1 && (b[5] & 0xFF) == 0xB1
                    && (b[6] & 0xFF) == 0x1A && (b[7] & 0xFF) == 0xE1;
        }
        if ("mp3".equals(ext)) {
            boolean id3 = b.length >= 3 && b[0] == 'I' && b[1] == 'D' && b[2] == '3';
            boolean frameSync = b.length >= 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xE0) == 0xE0;
            return id3 || frameSync;
        }
        if ("wav".equals(ext)) {
            return b.length >= 12
                    && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                    && b[8] == 'W' && b[9] == 'A' && b[10] == 'V' && b[11] == 'E';
        }
        if ("ogg".equals(ext)) {
            return b.length >= 4 && b[0] == 'O' && b[1] == 'g' && b[2] == 'g' && b[3] == 'S';
        }
        if ("mp4".equals(ext) || "m4a".equals(ext)) {
            return b.length >= 8 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p';
        }
        if ("webm".equals(ext)) {
            return b.length >= 4
                    && (b[0] & 0xFF) == 0x1A && (b[1] & 0xFF) == 0x45
                    && (b[2] & 0xFF) == 0xDF && (b[3] & 0xFF) == 0xA3;
        }
        if (TEXT_EXTENSIONS.contains(ext)) {
            return isSafeUtf8Text(b);
        }
        return false;
    }

    private boolean isSafeUtf8Text(byte[] b) {
        // Reject NUL bytes or excessive non-printable control characters
        for (byte value : b) {
            int unsigned = value & 0xFF;
            if (unsigned == 0) {
                return false;
            }
            if (unsigned < 0x09 || (unsigned > 0x0D && unsigned < 0x20 && unsigned != 0x1B)) {
                return false;
            }
        }

        // Validate UTF-8 decoding
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        String preview;
        try {
            preview = decoder.decode(ByteBuffer.wrap(b)).toString();
        } catch (CharacterCodingException e) {
            return false;
        }

        // Guard against HTML/SVG/Script polyglot payloads disguised as plain text
        String lower = preview.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("<!doctype html")
                || lower.startsWith("<html")
                || lower.startsWith("<script")
                || lower.startsWith("<svg")
                || lower.startsWith("<?php")) {
            return false;
        }
        return true;
    }

    public static class ValidatedFile {
        private final String sanitizedFilename;
        private final String extension;
        private final String mimeType;
        private final boolean inlineSafe;

        public ValidatedFile(String sanitizedFilename, String extension, String mimeType, boolean inlineSafe) {
            this.sanitizedFilename = sanitizedFilename;
            this.extension = extension;
            this.mimeType = mimeType;
            this.inlineSafe = inlineSafe;
        }

        public String getSanitizedFilename() {
            return sanitizedFilename;
        }

        public String getExtension() {
            return extension;
        }

        public String getMimeType() {
            return mimeType;
        }

        public boolean isInlineSafe() {
            return inlineSafe;
        }
    }
}
