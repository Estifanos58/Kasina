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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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

        // Custom argument resolver to inject currentPrincipal into @AuthenticationPrincipal
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
        msg.setTimestamps(LocalDateTime.now());

        when(channelRepository.findById("chan-100")).thenReturn(Optional.of(channel));
        when(messageRepository.findByChannelIdOrderByTimestampsAsc("chan-100")).thenReturn(List.of(msg));

        mockMvc.perform(get("/api/channels/get-channel-messages/chan-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("msg-1"))
                .andExpect(jsonPath("$[0].content").value("Hello channel!"))
                .andExpect(jsonPath("$[0].channelId").value("chan-100"));
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

        // currentPrincipal is "user-1", who is NOT a member of chan-private
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
