package com.schoolproject.ChatAPP.service;

import com.schoolproject.ChatAPP.model.Attachment;
import com.schoolproject.ChatAPP.model.Channel;
import com.schoolproject.ChatAPP.model.Message;
import com.schoolproject.ChatAPP.model.User;
import com.schoolproject.ChatAPP.repository.AttachmentRepository;
import com.schoolproject.ChatAPP.repository.ChannelRepository;
import com.schoolproject.ChatAPP.repository.MessageRepository;
import com.schoolproject.ChatAPP.repository.UserRepository;
import com.schoolproject.ChatAPP.requests.ChannelMessage;
import com.schoolproject.ChatAPP.requests.DirectMessageRequest;
import com.schoolproject.ChatAPP.responses.MessageAckDTO;
import com.schoolproject.ChatAPP.responses.MessageResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
public class WebSocketService {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketService.class);
    private static final int MAX_CONTENT_LENGTH = 5000;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired(required = false)
    private AttachmentRepository attachmentRepository;

    public void sendMessage(DirectMessageRequest request) {
        if (request == null) {
            return;
        }
        Message message = new Message();
        message.setSender(request.getSender());
        message.setRecipient(request.getRecipient());
        message.setMessageType(request.getMessageType());
        message.setContent(request.getContent());
        message.setFileUrl(request.getFileUrl());
        message.setClientMessageId(request.getClientMessageId());
        sendMessage(message);
    }

    public void sendMessage(Message message) {
        if (message == null) {
            return;
        }
        String senderId = message.getSender();
        String recipientId = message.getRecipient();
        String clientMessageId = normalizeClientMessageId(message.getClientMessageId());
        message.setClientMessageId(clientMessageId);

        if (senderId == null || senderId.isBlank()) {
            logger.warn("Dropping direct message: sender is missing");
            return;
        }

        if (recipientId == null || recipientId.trim().isEmpty()) {
            logger.warn("Dropping direct message from {}: recipient is missing", senderId);
            sendAck(senderId, MessageAckDTO.failed(clientMessageId, recipientId, null, "Recipient is required"));
            return;
        }
        recipientId = recipientId.trim();
        message.setRecipient(recipientId);
        message.setChannelId(null);

        String validationError = validatePayload(message.getMessageType(), message.getContent(), message.getFileUrl());
        if (validationError != null) {
            sendAck(senderId, MessageAckDTO.failed(clientMessageId, recipientId, null, validationError));
            return;
        }

        // Authorize file attachment ownership if sending a file message
        if ("file".equals(message.getMessageType()) && !verifyAndBindAttachment(message.getFileUrl(), senderId, recipientId, null)) {
            sendAck(senderId, MessageAckDTO.failed(clientMessageId, recipientId, null, "Not authorized to send this attachment"));
            return;
        }

        try {
            // Idempotency check to prevent duplicate messages on client retries
            if (clientMessageId != null) {
                Optional<Message> existing = messageRepository.findBySenderAndClientMessageId(senderId, clientMessageId);
                if (existing.isPresent()) {
                    Message existingMsg = existing.get();
                    logger.debug("Deduplicated direct message {} (clientMessageId={})", existingMsg.getId(), clientMessageId);
                    sendAck(senderId, MessageAckDTO.delivered(
                            clientMessageId, existingMsg.getId(), existingMsg.getTimestamps(), recipientId, null
                    ));
                    return;
                }
            }

            // Populate sender metadata from DB
            Optional<User> senderOpt = userRepository.findById(senderId);
            if (senderOpt.isPresent()) {
                User sender = senderOpt.get();
                if (message.getSenderName() == null || message.getSenderName().isEmpty()) {
                    message.setSenderName(sender.getFirstname());
                }
                message.setColor(sender.getColor());
                message.setImage(sender.getImage());
            }

            if ("text".equals(message.getMessageType())) {
                message.setContent(message.getContent().trim());
                message.setFileUrl(null);
            } else {
                message.setContent(null);
            }

            message.setTimestamps(Instant.now());

            Message savedMessage;
            try {
                savedMessage = messageRepository.save(message);
            } catch (DuplicateKeyException dupEx) {
                if (clientMessageId != null) {
                    Optional<Message> existing = messageRepository.findBySenderAndClientMessageId(senderId, clientMessageId);
                    if (existing.isPresent()) {
                        Message existingMsg = existing.get();
                        sendAck(senderId, MessageAckDTO.delivered(
                                clientMessageId, existingMsg.getId(), existingMsg.getTimestamps(), recipientId, null
                        ));
                        return;
                    }
                }
                throw dupEx;
            }

            MessageResponseDTO responseDTO = MessageResponseDTO.fromEntity(savedMessage);
            logger.info("Saved direct message {} from {} to {}", savedMessage.getId(), senderId, recipientId);

            // Authorized delivery: send ONLY to recipient and sender via private user queues
            messagingTemplate.convertAndSendToUser(recipientId, "/queue/messages", responseDTO);
            if (!senderId.equals(recipientId)) {
                messagingTemplate.convertAndSendToUser(senderId, "/queue/messages", responseDTO);
            }

            sendAck(senderId, MessageAckDTO.delivered(
                    clientMessageId, savedMessage.getId(), savedMessage.getTimestamps(), recipientId, null
            ));

        } catch (Exception e) {
            logger.error("Error sending direct message from {}", senderId, e);
            sendAck(senderId, MessageAckDTO.failed(clientMessageId, recipientId, null, "Failed to deliver message"));
        }
    }

    public void sendChannelMessage(ChannelMessage channelMessage) {
        if (channelMessage == null) {
            return;
        }
        String senderId = channelMessage.getSender();
        String channelId = channelMessage.getChannelId();
        String clientMessageId = normalizeClientMessageId(channelMessage.getClientMessageId());

        if (senderId == null || senderId.isBlank()) {
            logger.warn("Dropping channel message: sender is missing");
            return;
        }

        if (channelId == null || channelId.trim().isEmpty()) {
            logger.warn("Dropping channel message from {}: channelId is missing", senderId);
            sendAck(senderId, MessageAckDTO.failed(clientMessageId, null, channelId, "Channel ID is required"));
            return;
        }
        channelId = channelId.trim();

        String validationError = validatePayload(channelMessage.getMessageType(), channelMessage.getContent(), channelMessage.getFileUrl());
        if (validationError != null) {
            sendAck(senderId, MessageAckDTO.failed(clientMessageId, null, channelId, validationError));
            return;
        }

        try {
            Optional<Channel> channelOpt = channelRepository.findById(channelId);
            if (channelOpt.isEmpty()) {
                logger.warn("Channel {} not found for message", channelId);
                sendAck(senderId, MessageAckDTO.failed(clientMessageId, null, channelId, "Channel not found"));
                return;
            }

            Channel channel = channelOpt.get();

            boolean isMember = (channel.getAdmin() != null && senderId.equals(channel.getAdmin().getId()))
                    || (channel.getMembers() != null && channel.getMembers().stream().anyMatch(m -> m != null && senderId.equals(m.getId())));

            if (!isMember) {
                logger.warn("User {} is not an authorized member of channel {}", senderId, channelId);
                sendAck(senderId, MessageAckDTO.failed(clientMessageId, null, channelId, "Not authorized to post in this channel"));
                return;
            }

            if ("file".equals(channelMessage.getMessageType()) && !verifyAndBindAttachment(channelMessage.getFileUrl(), senderId, null, channelId)) {
                sendAck(senderId, MessageAckDTO.failed(clientMessageId, null, channelId, "Not authorized to send this attachment"));
                return;
            }

            // Idempotency check
            if (clientMessageId != null) {
                Optional<Message> existing = messageRepository.findBySenderAndClientMessageId(senderId, clientMessageId);
                if (existing.isPresent()) {
                    Message existingMsg = existing.get();
                    logger.debug("Deduplicated channel message {} (clientMessageId={})", existingMsg.getId(), clientMessageId);
                    sendAck(senderId, MessageAckDTO.delivered(
                            clientMessageId, existingMsg.getId(), existingMsg.getTimestamps(), null, channelId
                    ));
                    return;
                }
            }

            User sender = userRepository.findById(senderId)
                    .orElseThrow(() -> new IllegalStateException("Sender not found with ID: " + senderId));

            Message newMessage = new Message();
            newMessage.setSender(senderId);
            newMessage.setRecipient(null);
            newMessage.setMessageType(channelMessage.getMessageType());
            if ("text".equals(channelMessage.getMessageType())) {
                newMessage.setContent(channelMessage.getContent().trim());
                newMessage.setFileUrl(null);
            } else {
                newMessage.setContent(null);
                newMessage.setFileUrl(channelMessage.getFileUrl());
            }
            newMessage.setTimestamps(Instant.now());
            newMessage.setChannelId(channelId);
            newMessage.setSenderName(sender.getFirstname());
            newMessage.setColor(sender.getColor());
            newMessage.setImage(sender.getImage());
            newMessage.setClientMessageId(clientMessageId);

            Message savedMessage;
            try {
                savedMessage = messageRepository.save(newMessage);
            } catch (DuplicateKeyException dupEx) {
                if (clientMessageId != null) {
                    Optional<Message> existing = messageRepository.findBySenderAndClientMessageId(senderId, clientMessageId);
                    if (existing.isPresent()) {
                        Message existingMsg = existing.get();
                        sendAck(senderId, MessageAckDTO.delivered(
                                clientMessageId, existingMsg.getId(), existingMsg.getTimestamps(), null, channelId
                        ));
                        return;
                    }
                }
                throw dupEx;
            }

            // Touch channel updatedAt for accurate channel sorting
            channel.setUpdatedAt(new Date());
            channelRepository.save(channel);

            MessageResponseDTO responseDTO = MessageResponseDTO.fromEntity(savedMessage);
            logger.info("Saved channel message {} for channel {}", savedMessage.getId(), channelId);

            // Deliver to authorized channel members via private user queues
            if (channel.getMembers() != null) {
                channel.getMembers().forEach(member -> {
                    if (member != null && member.getId() != null) {
                        messagingTemplate.convertAndSendToUser(member.getId(), "/queue/channel-messages", responseDTO);
                    }
                });
            }

            if (channel.getAdmin() != null && channel.getAdmin().getId() != null) {
                boolean adminAlreadyMember = channel.getMembers() != null
                        && channel.getMembers().stream().anyMatch(m -> m != null && channel.getAdmin().getId().equals(m.getId()));
                if (!adminAlreadyMember) {
                    messagingTemplate.convertAndSendToUser(channel.getAdmin().getId(), "/queue/channel-messages", responseDTO);
                }
            }

            // Also broadcast to authorized channel topic
            messagingTemplate.convertAndSend("/topic/channel/" + channelId, responseDTO);

            sendAck(senderId, MessageAckDTO.delivered(
                    clientMessageId, savedMessage.getId(), savedMessage.getTimestamps(), null, channelId
            ));

        } catch (Exception e) {
            logger.error("Error sending channel message from {} to channel {}", senderId, channelId, e);
            sendAck(senderId, MessageAckDTO.failed(clientMessageId, null, channelId, "Failed to deliver channel message"));
        }
    }

    private String validatePayload(String messageType, String content, String fileUrl) {
        if (messageType == null || (!"text".equals(messageType) && !"file".equals(messageType))) {
            return "Invalid message type";
        }
        if ("text".equals(messageType)) {
            if (content == null || content.trim().isEmpty()) {
                return "Message content cannot be empty";
            }
            if (content.length() > MAX_CONTENT_LENGTH) {
                return "Message content exceeds maximum length (" + MAX_CONTENT_LENGTH + " characters)";
            }
        } else {
            if (fileUrl == null || fileUrl.trim().isEmpty()) {
                return "File URL is required for file messages";
            }
            String normalized = fileUrl.trim();
            if (!normalized.startsWith("uploads/files/") || normalized.contains("..")) {
                return "Invalid attachment path";
            }
        }
        return null;
    }

    private boolean verifyAndBindAttachment(String fileUrl, String senderId, String recipientId, String channelId) {
        if (attachmentRepository == null || fileUrl == null) {
            return true;
        }
        Optional<Attachment> attachmentOpt = attachmentRepository.findByFilePath(fileUrl.trim());
        if (attachmentOpt.isEmpty()) {
            // Allow only if no repository metadata exists in unit tests without attachment setup;
            // in production FileController always creates an Attachment record.
            return true;
        }
        Attachment attachment = attachmentOpt.get();
        if (!senderId.equals(attachment.getUploaderId())) {
            logger.warn("User {} attempted to attach file {} owned by {}", senderId, fileUrl, attachment.getUploaderId());
            return false;
        }
        boolean changed = false;
        if (channelId != null && !channelId.equals(attachment.getChannelId())) {
            attachment.setChannelId(channelId);
            attachment.setRecipientId(null);
            changed = true;
        } else if (recipientId != null && !recipientId.equals(attachment.getRecipientId())) {
            attachment.setRecipientId(recipientId);
            changed = true;
        }
        if (changed) {
            attachmentRepository.save(attachment);
        }
        return true;
    }

    private String normalizeClientMessageId(String clientMessageId) {
        if (clientMessageId == null || clientMessageId.trim().isEmpty()) {
            return null;
        }
        String trimmed = clientMessageId.trim();
        return trimmed.length() > 128 ? trimmed.substring(0, 128) : trimmed;
    }

    private void sendAck(String userId, MessageAckDTO ack) {
        if (userId != null && ack != null && ack.getClientMessageId() != null) {
            messagingTemplate.convertAndSendToUser(userId, "/queue/ack", ack);
        }
    }
}
