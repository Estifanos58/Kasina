package com.schoolproject.ChatAPP.service;

import com.schoolproject.ChatAPP.model.Channel;
import com.schoolproject.ChatAPP.model.Message;
import com.schoolproject.ChatAPP.model.User;
import com.schoolproject.ChatAPP.repository.ChannelRepository;
import com.schoolproject.ChatAPP.repository.MessageRepository;
import com.schoolproject.ChatAPP.repository.UserRepository;
import com.schoolproject.ChatAPP.requests.ChannelMessage;
import com.schoolproject.ChatAPP.responses.MessageResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class WebSocketService {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketService.class);

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private UserRepository userRepository;

    public void sendMessage(Message message) {
        try {
            String senderId = message.getSender();
            String recipientId = message.getRecipient();

            if (senderId == null || recipientId == null || recipientId.trim().isEmpty()) {
                logger.warn("Dropping direct message: sender or recipient is missing");
                return;
            }

            // Populate sender info if missing
            Optional<User> senderOpt = userRepository.findById(senderId);
            if (senderOpt.isPresent()) {
                User sender = senderOpt.get();
                if (message.getSenderName() == null || message.getSenderName().isEmpty()) {
                    message.setSenderName(sender.getFirstname());
                }
                message.setColor(sender.getColor());
                message.setImage(sender.getImage());
            }

            message.setTimestamps(LocalDateTime.now());
            Message savedMessage = messageRepository.save(message);
            MessageResponseDTO responseDTO = MessageResponseDTO.fromEntity(savedMessage);

            logger.info("Saved direct message {} from {} to {}", savedMessage.getId(), senderId, recipientId);

            // Authorized delivery: send ONLY to recipient and sender via user destination
            messagingTemplate.convertAndSendToUser(recipientId, "/queue/messages", responseDTO);
            if (!senderId.equals(recipientId)) {
                messagingTemplate.convertAndSendToUser(senderId, "/queue/messages", responseDTO);
            }

        } catch (Exception e) {
            logger.error("Error sending direct message", e);
        }
    }

    public void sendChannelMessage(ChannelMessage channelMessage) {
        try {
            String senderId = channelMessage.getSender();
            String channelId = channelMessage.getChannelId();

            if (senderId == null || channelId == null || channelId.trim().isEmpty()) {
                logger.warn("Dropping channel message: sender or channelId is missing");
                return;
            }

            // Verify channel existence
            Optional<Channel> channelOpt = channelRepository.findById(channelId);
            if (channelOpt.isEmpty()) {
                logger.warn("Channel {} not found for message", channelId);
                return;
            }

            Channel channel = channelOpt.get();

            // Verify channel membership before sending
            boolean isMember = (channel.getAdmin() != null && channel.getAdmin().getId().equals(senderId)) ||
                    (channel.getMembers() != null && channel.getMembers().stream().anyMatch(m -> m != null && m.getId().equals(senderId)));

            if (!isMember) {
                logger.warn("User {} is not an authorized member of channel {}", senderId, channelId);
                return;
            }

            User sender = userRepository.findById(senderId)
                    .orElseThrow(() -> new RuntimeException("Sender not found with ID: " + senderId));

            // Create and persist new message in messages collection
            Message newMessage = new Message();
            newMessage.setSender(senderId);
            newMessage.setRecipient(null);
            newMessage.setContent(channelMessage.getContent());
            newMessage.setMessageType(channelMessage.getMessageType());
            newMessage.setTimestamps(LocalDateTime.now());
            newMessage.setFileUrl(channelMessage.getFileUrl());
            newMessage.setChannelId(channelId);
            newMessage.setSenderName(sender.getFirstname());
            newMessage.setColor(sender.getColor());
            newMessage.setImage(sender.getImage());

            Message savedMessage = messageRepository.save(newMessage);
            MessageResponseDTO responseDTO = MessageResponseDTO.fromEntity(savedMessage);

            logger.info("Saved channel message {} for channel {}", savedMessage.getId(), channelId);

            // Authorized delivery: deliver to authorized channel members only
            if (channel.getMembers() != null) {
                channel.getMembers().forEach(member -> {
                    if (member != null && member.getId() != null) {
                        messagingTemplate.convertAndSendToUser(member.getId(), "/queue/channel-messages", responseDTO);
                    }
                });
            }

            if (channel.getAdmin() != null && channel.getAdmin().getId() != null) {
                boolean adminAlreadyMember = channel.getMembers() != null &&
                        channel.getMembers().stream().anyMatch(m -> m != null && m.getId().equals(channel.getAdmin().getId()));
                if (!adminAlreadyMember) {
                    messagingTemplate.convertAndSendToUser(channel.getAdmin().getId(), "/queue/channel-messages", responseDTO);
                }
            }

            // Also broadcast to the channel-specific topic (subscriptions to which are authorized in WebSocketConfig)
            messagingTemplate.convertAndSend("/topic/channel/" + channelId, responseDTO);

        } catch (Exception e) {
            logger.error("Error sending channel message", e);
        }
    }
}
