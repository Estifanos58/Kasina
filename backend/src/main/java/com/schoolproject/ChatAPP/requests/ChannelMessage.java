package com.schoolproject.ChatAPP.requests;

public class ChannelMessage {
    private String sender;
    private String senderName;
    private String channelId;
    private String content;
    private String messageType;
    private String fileUrl;

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
}
