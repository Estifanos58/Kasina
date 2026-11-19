package com.schoolproject.ChatAPP.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "messages")
@CompoundIndexes({
        @CompoundIndex(name = "dm_sender_recipient_ts_idx", def = "{'sender': 1, 'recipient': 1, 'timestamps': -1, '_id': -1}"),
        @CompoundIndex(name = "dm_recipient_sender_ts_idx", def = "{'recipient': 1, 'sender': 1, 'timestamps': -1, '_id': -1}"),
        @CompoundIndex(name = "channel_msg_ts_idx", def = "{'channelId': 1, 'timestamps': -1, '_id': -1}"),
        @CompoundIndex(
                name = "sender_client_msg_unique_idx",
                def = "{'sender': 1, 'clientMessageId': 1}",
                unique = true,
                partialFilter = "{'clientMessageId': {'$type': 'string'}}"
        )
})
public class Message {

    @Id
    private String id;

    private String sender;
    private String recipient;

    private String messageType;

    private String content;

    @Indexed(sparse = true)
    private String fileUrl;

    private Instant timestamps = Instant.now();

    private String channelId = null;

    private String senderName;

    private int color;

    private String image;

    private String clientMessageId;

    public Message() {}

    public Message(String sender, String recipient, String messageType, String content, String fileUrl) {
        this.sender = sender;
        this.recipient = recipient;
        this.messageType = messageType;

        if ("text".equals(messageType)) {
            this.content = content;
            this.fileUrl = null;
        } else if ("file".equals(messageType)) {
            this.fileUrl = fileUrl;
            this.content = null;
        } else {
            throw new IllegalArgumentException("Invalid message type");
        }

        this.timestamps = Instant.now();
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

    public Instant getTimestamps() {
        return timestamps;
    }

    public void setTimestamps(Instant timestamps) {
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

    public String getClientMessageId() {
        return clientMessageId;
    }

    public void setClientMessageId(String clientMessageId) {
        this.clientMessageId = clientMessageId;
    }

    @Override
    public String toString() {
        return "Message{" +
                "id='" + id + '\'' +
                ", sender='" + sender + '\'' +
                ", recipient='" + recipient + '\'' +
                ", messageType='" + messageType + '\'' +
                ", channelId='" + channelId + '\'' +
                ", clientMessageId='" + clientMessageId + '\'' +
                ", timestamps=" + timestamps +
                '}';
    }
}
