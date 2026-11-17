package com.schoolproject.ChatAPP.responses;

import com.schoolproject.ChatAPP.model.Message;
import java.time.LocalDateTime;

public class MessageResponseDTO {
    private String id;
    private String sender;
    private String recipient;
    private String messageType;
    private String content;
    private String fileUrl;
    private LocalDateTime timestamps;
    private String channelId;
    private String senderName;
    private int color;
    private String image;

    public MessageResponseDTO() {}

    public MessageResponseDTO(String id, String sender, String recipient, String messageType, String content,
                              String fileUrl, LocalDateTime timestamps, String channelId, String senderName,
                              int color, String image) {
        this.id = id;
        this.sender = sender;
        this.recipient = recipient;
        this.messageType = messageType;
        this.content = content;
        this.fileUrl = fileUrl;
        this.timestamps = timestamps;
        this.channelId = channelId;
        this.senderName = senderName;
        this.color = color;
        this.image = image;
    }

    public static MessageResponseDTO fromEntity(Message msg) {
        if (msg == null) {
            return null;
        }
        return new MessageResponseDTO(
                msg.getId(),
                msg.getSender(),
                msg.getRecipient(),
                msg.getMessageType(),
                msg.getContent(),
                msg.getFileUrl(),
                msg.getTimestamps(),
                msg.getChannelId(),
                msg.getSenderName(),
                msg.getColor(),
                msg.getImage()
        );
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public LocalDateTime getTimestamps() {
        return timestamps;
    }

    public void setTimestamps(LocalDateTime timestamps) {
        this.timestamps = timestamps;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }
}
