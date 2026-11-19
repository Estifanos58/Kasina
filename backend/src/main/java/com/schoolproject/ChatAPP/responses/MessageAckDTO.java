package com.schoolproject.ChatAPP.responses;

import java.time.Instant;

public class MessageAckDTO {
    private String clientMessageId;
    private String messageId;
    private String status; // "DELIVERED" or "FAILED"
    private Instant timestamps;
    private String recipient;
    private String channelId;
    private String error;

    public MessageAckDTO() {}

    public static MessageAckDTO delivered(String clientMessageId, String messageId, Instant timestamps, String recipient, String channelId) {
        MessageAckDTO ack = new MessageAckDTO();
        ack.clientMessageId = clientMessageId;
        ack.messageId = messageId;
        ack.status = "DELIVERED";
        ack.timestamps = timestamps;
        ack.recipient = recipient;
        ack.channelId = channelId;
        return ack;
    }

    public static MessageAckDTO failed(String clientMessageId, String recipient, String channelId, String error) {
        MessageAckDTO ack = new MessageAckDTO();
        ack.clientMessageId = clientMessageId;
        ack.status = "FAILED";
        ack.timestamps = Instant.now();
        ack.recipient = recipient;
        ack.channelId = channelId;
        ack.error = error;
        return ack;
    }

    public String getClientMessageId() {
        return clientMessageId;
    }

    public void setClientMessageId(String clientMessageId) {
        this.clientMessageId = clientMessageId;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getTimestamps() {
        return timestamps;
    }

    public void setTimestamps(Instant timestamps) {
        this.timestamps = timestamps;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
