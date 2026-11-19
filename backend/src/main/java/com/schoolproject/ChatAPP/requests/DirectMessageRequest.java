package com.schoolproject.ChatAPP.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class DirectMessageRequest {

    private String sender;

    @NotBlank(message = "Recipient is required")
    private String recipient;

    @NotBlank(message = "Message type is required")
    @Pattern(regexp = "^(text|file)$", message = "Message type must be 'text' or 'file'")
    private String messageType;

    @Size(max = 5000, message = "Message content cannot exceed 5000 characters")
    private String content;

    @Size(max = 1024, message = "File URL is too long")
    private String fileUrl;

    @Size(max = 128, message = "Client message ID is too long")
    private String clientMessageId;

    public DirectMessageRequest() {}

    public DirectMessageRequest(String sender, String recipient, String messageType, String content, String fileUrl, String clientMessageId) {
        this.sender = sender;
        this.recipient = recipient;
        this.messageType = messageType;
        this.content = content;
        this.fileUrl = fileUrl;
        this.clientMessageId = clientMessageId;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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
