package com.schoolproject.ChatAPP.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolproject.ChatAPP.config.UserPrincipal;
import com.schoolproject.ChatAPP.model.Channel;
import com.schoolproject.ChatAPP.model.Message;
import com.schoolproject.ChatAPP.model.User;
import com.schoolproject.ChatAPP.repository.ChannelRepository;
import com.schoolproject.ChatAPP.repository.MessageRepository;
import com.schoolproject.ChatAPP.repository.UserRepository;
import com.schoolproject.ChatAPP.requests.ChannelRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ChannelMembershipTest {

    private MockMvc mockMvc;

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MessageRepository messageRepository;

    @InjectMocks
    private ChannelController channelController;

    private UserPrincipal currentPrincipal;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        currentPrincipal = new UserPrincipal("user-1", "user1@example.com");

        HandlerMethodArgumentResolver authPrincipalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return currentPrincipal;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(channelController)
                .setCustomArgumentResolvers(authPrincipalResolver)
                .build();
    }

    @Test
    void testGetChannelMessagesAuthorizedMember() throws Exception {
        User admin = new User("admin-id");
        admin.setEmail("admin@test.com");

        User member = new User("user-1");
        member.setEmail("user1@example.com");

        Channel channel = new Channel();
        channel.setId("chan-100");
        channel.setName("General");
        channel.setAdmin(admin);
        channel.setMembers(List.of(member));

        Message msg = new Message("user-1", null, "text", "Hello channel!", null);
        msg.setId("msg-1");
        msg.setChannelId("chan-100");
        msg.setTimestamps(Instant.parse("2026-11-18T10:00:00Z"));

        when(channelRepository.findById("chan-100")).thenReturn(Optional.of(channel));
        when(messageRepository.findChannelMessagesPage(eq("chan-100"), isNull(), isNull(), eq(51)))
                .thenReturn(List.of(msg));

        mockMvc.perform(get("/api/channels/get-channel-messages/chan-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("msg-1"))
                .andExpect(jsonPath("$[0].content").value("Hello channel!"))
                .andExpect(jsonPath("$[0].channelId").value("chan-100"));
    }

    @Test
    void testGetChannelMessagesCursorPagination() throws Exception {
        User member = new User("user-1");
        Channel channel = new Channel();
        channel.setId("chan-100");
        channel.setName("General");
        channel.setAdmin(member);
        channel.setMembers(List.of(member));

        // Returned in descending order from DB (limit + 1 = 3)
        Message newest = new Message("user-1", null, "text", "Third", null);
        newest.setId("msg-3");
        newest.setChannelId("chan-100");
        newest.setTimestamps(Instant.parse("2026-11-18T10:03:00Z"));

        Message middle = new Message("user-1", null, "text", "Second", null);
        middle.setId("msg-2");
        middle.setChannelId("chan-100");
        middle.setTimestamps(Instant.parse("2026-11-18T10:02:00Z"));

        Message oldest = new Message("user-1", null, "text", "First", null);
        oldest.setId("msg-1");
        oldest.setChannelId("chan-100");
        oldest.setTimestamps(Instant.parse("2026-11-18T10:01:00Z"));

        when(channelRepository.findById("chan-100")).thenReturn(Optional.of(channel));
        when(messageRepository.findChannelMessagesPage(eq("chan-100"), isNull(), isNull(), eq(3)))
                .thenReturn(List.of(newest, middle, oldest));

        mockMvc.perform(get("/api/channels/get-channel-messages/chan-100")
                        .param("limit", "2")
                        .param("paginated", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].id").value("msg-2"))
                .andExpect(jsonPath("$.messages[1].id").value("msg-3"));
    }

    @Test
    void testGetUserChannelsUsesIndexedRepositoryQueryNotFindAll() throws Exception {
        User user1 = new User("user-1");
        Channel ch = new Channel();
        ch.setId("chan-1");
        ch.setName("Engineering");
        ch.setAdmin(user1);
        ch.setMembers(List.of(user1));

        when(channelRepository.findByAdmin_IdOrMembers_IdOrderByUpdatedAtDesc("user-1", "user-1"))
                .thenReturn(List.of(ch));

        mockMvc.perform(get("/api/channels/user-channels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("chan-1"))
                .andExpect(jsonPath("$[0].name").value("Engineering"));

        verify(channelRepository, never()).findAll();
    }

    @Test
    void testGetChannelMessagesUnauthorizedUserReturnsForbidden() throws Exception {
        User admin = new User("admin-id");
        admin.setEmail("admin@test.com");

        User otherMember = new User("other-user");
        otherMember.setEmail("other@test.com");

        Channel channel = new Channel();
        channel.setId("chan-private");
        channel.setName("Secret Room");
        channel.setAdmin(admin);
        channel.setMembers(List.of(otherMember));

        when(channelRepository.findById("chan-private")).thenReturn(Optional.of(channel));

        mockMvc.perform(get("/api/channels/get-channel-messages/chan-private"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You are not a member of this channel"));
    }

    @Test
    void testGetChannelMessagesNotFound() throws Exception {
        when(channelRepository.findById("chan-missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/channels/get-channel-messages/chan-missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Channel not found"));
    }

    @Test
    void testCreateChannelSuccess() throws Exception {
        ChannelRequest request = new ChannelRequest("Tech Talk", List.of("user-2"));

        User admin = new User("user-1");
        admin.setEmail("user1@example.com");

        User member2 = new User("user-2");
        member2.setEmail("user2@example.com");

        when(userRepository.findById("user-1")).thenReturn(Optional.of(admin));
        when(userRepository.findAllById(List.of("user-2"))).thenReturn(List.of(member2));

        Channel savedChannel = new Channel();
        savedChannel.setId("chan-new");
        savedChannel.setName("Tech Talk");
        savedChannel.setAdmin(admin);
        savedChannel.setMembers(List.of(member2, admin));

        when(channelRepository.save(any(Channel.class))).thenReturn(savedChannel);

        mockMvc.perform(post("/api/channels/create-channel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("chan-new"))
                .andExpect(jsonPath("$.name").value("Tech Talk"))
                .andExpect(jsonPath("$.admin.email").value("user1@example.com"))
                .andExpect(jsonPath("$.admin.password").doesNotExist())
                .andExpect(jsonPath("$.members[0].password").doesNotExist());
    }
}
