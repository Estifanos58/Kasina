package com.schoolproject.ChatAPP.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ChannelMessage {
    private String sender;
    private String senderName;

    @NotBlank(message = "Channel ID is required")
    private String channelId;

    @Size(max = 5000, message = "Message content cannot exceed 5000 characters")
    private String content;

    @NotBlank(message = "Message type is required")
    @Pattern(regexp = "^(text|file)$", message = "Message type must be 'text' or 'file'")
    private String messageType;

    @Size(max = 1024, message = "File URL is too long")
    private String fileUrl;

    @Size(max = 128, message = "Client message ID is too long")
    private String clientMessageId;

    public ChannelMessage() {}

    public ChannelMessage(String sender, String senderName, String channelId, String content, String messageType, String fileUrl) {
        this.sender = sender;
        this.senderName = senderName;
        this.channelId = channelId;
        this.content = content;
        this.messageType = messageType;
        this.fileUrl = fileUrl;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public void setSenderId(String sender) {
        this.sender = sender;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    // Alias for old misspelled getter/setter
    public String getSendeName() {
        return senderName;
    }

    public void setSendeName(String sendeName) {
        this.senderName = sendeName;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public String getClientMessageId() {
        return clientMessageId;
    }

    public void setClientMessageId(String clientMessageId) {
        this.clientMessageId = clientMessageId;
    }
}
