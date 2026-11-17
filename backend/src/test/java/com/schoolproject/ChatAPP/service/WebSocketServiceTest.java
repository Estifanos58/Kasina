package com.schoolproject.ChatAPP.service;

import com.schoolproject.ChatAPP.model.Channel;
import com.schoolproject.ChatAPP.model.Message;
import com.schoolproject.ChatAPP.model.User;
import com.schoolproject.ChatAPP.repository.ChannelRepository;
import com.schoolproject.ChatAPP.repository.MessageRepository;
import com.schoolproject.ChatAPP.repository.UserRepository;
import com.schoolproject.ChatAPP.requests.ChannelMessage;
import com.schoolproject.ChatAPP.responses.MessageResponseDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

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
    void testDirectMessageDeliveredOnlyToAuthorizedParties() {
        Message message = new Message("alice-id", "bob-id", "text", "Secret private message", null);
        message.setId("msg-123");

        User alice = new User("alice-id");
        alice.setFirstname("Alice");
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(alice));
        when(messageRepository.save(any(Message.class))).thenReturn(message);

        webSocketService.sendMessage(message);

        // Verify sent to Bob's private user queue
        verify(messagingTemplate).convertAndSendToUser(eq("bob-id"), eq("/queue/messages"), any(MessageResponseDTO.class));
        // Verify sent to Alice's private user queue (for multi-session sync)
        verify(messagingTemplate).convertAndSendToUser(eq("alice-id"), eq("/queue/messages"), any(MessageResponseDTO.class));
        // Verify NEVER broadcasted to public topic!
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/public"), any(Object.class));
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
        webSocketService.sendChannelMessage(request);

        // Delivered to member user queue
        verify(messagingTemplate).convertAndSendToUser(eq("member-1"), eq("/queue/channel-messages"), any(MessageResponseDTO.class));
        // Delivered to admin user queue
        verify(messagingTemplate).convertAndSendToUser(eq("admin-1"), eq("/queue/channel-messages"), any(MessageResponseDTO.class));
        // Broadcast to specific channel topic
        verify(messagingTemplate).convertAndSend(eq("/topic/channel/chan-1"), any(MessageResponseDTO.class));
    }

    @Test
    void testChannelMessageUnauthorizedSenderRejected() {
        User admin = new User("admin-1");
        User member = new User("member-1");

        Channel channel = new Channel();
        channel.setId("chan-1");
        channel.setAdmin(admin);
        channel.setMembers(List.of(member));

        when(channelRepository.findById("chan-1")).thenReturn(Optional.of(channel));

        // eve is NOT a member of chan-1
        ChannelMessage request = new ChannelMessage("eve-attacker", "Eve", "chan-1", "I hacked in", "text", null);
        webSocketService.sendChannelMessage(request);

        verify(messageRepository, never()).save(any(Message.class));
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any(Object.class));
    }
}
