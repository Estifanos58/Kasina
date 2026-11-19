package com.schoolproject.ChatAPP.service;

import com.schoolproject.ChatAPP.model.Channel;
import com.schoolproject.ChatAPP.model.Message;
import com.schoolproject.ChatAPP.model.User;
import com.schoolproject.ChatAPP.repository.ChannelRepository;
import com.schoolproject.ChatAPP.repository.MessageRepository;
import com.schoolproject.ChatAPP.repository.UserRepository;
import com.schoolproject.ChatAPP.requests.ChannelMessage;
import com.schoolproject.ChatAPP.responses.MessageAckDTO;
import com.schoolproject.ChatAPP.responses.MessageResponseDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private WebSocketService webSocketService;

    @Test
    void testDirectMessageDeliveredOnlyToAuthorizedPartiesAndAcknowledged() {
        Message message = new Message("alice-id", "bob-id", "text", "Secret private message", null);
        message.setId("msg-123");
        message.setClientMessageId("client-uuid-1");

        User alice = new User("alice-id");
        alice.setFirstname("Alice");
        when(messageRepository.findBySenderAndClientMessageId("alice-id", "client-uuid-1")).thenReturn(Optional.empty());
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(alice));
        when(messageRepository.save(any(Message.class))).thenReturn(message);

        webSocketService.sendMessage(message);

        verify(messagingTemplate).convertAndSendToUser(eq("bob-id"), eq("/queue/messages"), any(MessageResponseDTO.class));
        verify(messagingTemplate).convertAndSendToUser(eq("alice-id"), eq("/queue/messages"), any(MessageResponseDTO.class));
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/public"), any(Object.class));

        ArgumentCaptor<MessageAckDTO> ackCaptor = ArgumentCaptor.forClass(MessageAckDTO.class);
        verify(messagingTemplate).convertAndSendToUser(eq("alice-id"), eq("/queue/ack"), ackCaptor.capture());
        assertEquals("DELIVERED", ackCaptor.getValue().getStatus());
        assertEquals("client-uuid-1", ackCaptor.getValue().getClientMessageId());
        assertEquals("msg-123", ackCaptor.getValue().getMessageId());
    }

    @Test
    void testDuplicateClientMessageIdIsDeduplicatedAndReAcknowledged() {
        Message existing = new Message("alice-id", "bob-id", "text", "Hello", null);
        existing.setId("msg-existing");
        existing.setClientMessageId("client-dup-1");
        existing.setTimestamps(Instant.parse("2026-11-18T12:00:00Z"));

        when(messageRepository.findBySenderAndClientMessageId("alice-id", "client-dup-1"))
                .thenReturn(Optional.of(existing));

        Message retry = new Message("alice-id", "bob-id", "text", "Hello", null);
        retry.setClientMessageId("client-dup-1");

        webSocketService.sendMessage(retry);

        // Must NOT save a duplicate message or rebroadcast
        verify(messageRepository, never()).save(any(Message.class));
        verify(messagingTemplate, never()).convertAndSendToUser(eq("bob-id"), eq("/queue/messages"), any());

        // Must re-acknowledge sender so client clears pending state
        ArgumentCaptor<MessageAckDTO> ackCaptor = ArgumentCaptor.forClass(MessageAckDTO.class);
        verify(messagingTemplate).convertAndSendToUser(eq("alice-id"), eq("/queue/ack"), ackCaptor.capture());
        assertEquals("DELIVERED", ackCaptor.getValue().getStatus());
        assertEquals("msg-existing", ackCaptor.getValue().getMessageId());
    }

    @Test
    void testChannelMessageAuthorizedDelivery() {
        User admin = new User("admin-1");
        User member = new User("member-1");

        Channel channel = new Channel();
        channel.setId("chan-1");
        channel.setAdmin(admin);
        channel.setMembers(List.of(member));

        when(channelRepository.findById("chan-1")).thenReturn(Optional.of(channel));
        when(userRepository.findById("member-1")).thenReturn(Optional.of(member));

        Message savedMsg = new Message("member-1", null, "text", "Channel hello", null);
        savedMsg.setId("cmsg-1");
        savedMsg.setChannelId("chan-1");
        when(messageRepository.save(any(Message.class))).thenReturn(savedMsg);

        ChannelMessage request = new ChannelMessage("member-1", "Member", "chan-1", "Channel hello", "text", null);
        request.setClientMessageId("chan-client-1");
        when(messageRepository.findBySenderAndClientMessageId("member-1", "chan-client-1")).thenReturn(Optional.empty());

        webSocketService.sendChannelMessage(request);

        verify(messagingTemplate).convertAndSendToUser(eq("member-1"), eq("/queue/channel-messages"), any(MessageResponseDTO.class));
        verify(messagingTemplate).convertAndSendToUser(eq("admin-1"), eq("/queue/channel-messages"), any(MessageResponseDTO.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/channel/chan-1"), any(MessageResponseDTO.class));
        verify(messagingTemplate).convertAndSendToUser(eq("member-1"), eq("/queue/ack"), any(MessageAckDTO.class));
    }

    @Test
    void testChannelMessageUnauthorizedSenderRejectedAndNacked() {
        User admin = new User("admin-1");
        User member = new User("member-1");

        Channel channel = new Channel();
        channel.setId("chan-1");
        channel.setAdmin(admin);
        channel.setMembers(List.of(member));

        when(channelRepository.findById("chan-1")).thenReturn(Optional.of(channel));

        ChannelMessage request = new ChannelMessage("eve-attacker", "Eve", "chan-1", "I hacked in", "text", null);
        request.setClientMessageId("eve-msg-1");
        webSocketService.sendChannelMessage(request);

        verify(messageRepository, never()).save(any(Message.class));
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), eq("/queue/channel-messages"), any(Object.class));

        ArgumentCaptor<MessageAckDTO> ackCaptor = ArgumentCaptor.forClass(MessageAckDTO.class);
        verify(messagingTemplate).convertAndSendToUser(eq("eve-attacker"), eq("/queue/ack"), ackCaptor.capture());
        assertEquals("FAILED", ackCaptor.getValue().getStatus());
    }
}
