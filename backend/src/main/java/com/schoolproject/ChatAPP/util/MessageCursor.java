package com.schoolproject.ChatAPP.util;

import java.time.Instant;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

/**
 * Encodes and decodes opaque keyset pagination cursors composed of a UTC timestamp and message ID.
 */
public final class MessageCursor {

    private final Instant timestamp;
    private final String id;

    public MessageCursor(Instant timestamp, String id) {
        this.timestamp = timestamp;
        this.id = id;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getId() {
        return id;
    }

    public static String encode(Instant timestamp, String id) {
        if (timestamp == null) {
            return null;
        }
        String raw = timestamp.toEpochMilli() + "|" + (id != null ? id : "");
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static MessageCursor decode(String cursor) {
        if (cursor == null || cursor.trim().isEmpty()) {
            return null;
        }
        try {
            String trimmed = cursor.trim();
            // Support direct ISO-8601 timestamp fallback as well as opaque base64 cursors
            if (trimmed.contains("T") && (trimmed.endsWith("Z") || trimmed.contains("+"))) {
                return new MessageCursor(Instant.parse(trimmed), null);
            }
            byte[] decoded = Base64.getUrlDecoder().decode(trimmed);
            String raw = new String(decoded, StandardCharsets.UTF_8);
            int sep = raw.indexOf('|');
            if (sep <= 0) {
                throw new IllegalArgumentException("Invalid pagination cursor");
            }
            long epochMilli = Long.parseLong(raw.substring(0, sep));
            String idPart = raw.substring(sep + 1);
            return new MessageCursor(Instant.ofEpochMilli(epochMilli), idPart.isEmpty() ? null : idPart);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid pagination cursor");
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid pagination cursor");
        }
    }
}
