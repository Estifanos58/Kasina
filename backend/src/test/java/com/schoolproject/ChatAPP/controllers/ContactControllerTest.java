package com.schoolproject.ChatAPP.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolproject.ChatAPP.config.UserPrincipal;
import com.schoolproject.ChatAPP.model.Message;
import com.schoolproject.ChatAPP.model.User;
import com.schoolproject.ChatAPP.repository.MessageRepository;
import com.schoolproject.ChatAPP.repository.UserRepository;
import com.schoolproject.ChatAPP.requests.SearchRequest;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ContactControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MessageRepository messageRepository;

    @InjectMocks
    private ContactController contactController;

    private UserPrincipal currentPrincipal;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        currentPrincipal = new UserPrincipal("me-1", "me@example.com");

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

        mockMvc = MockMvcBuilders.standaloneSetup(contactController)
                .setCustomArgumentResolvers(authPrincipalResolver)
                .build();
    }

    @Test
    void testDmListFullTimestampOrdering() throws Exception {
        // me-1 communicated with user-A (today at 08:00 AM) and user-B (yesterday at 23:00 PM).
        // With previous bug (.toLocalTime()), 23:00 would sort BEFORE 08:00 despite being yesterday!
        // With our fix, today at 08:00 MUST sort BEFORE yesterday at 23:00!
        LocalDateTime todayMorning = LocalDateTime.of(2026, 11, 17, 8, 0, 0);
        LocalDateTime yesterdayNight = LocalDateTime.of(2026, 11, 16, 23, 0, 0);

        Message msgA = new Message("me-1", "user-A", "text", "Good morning", null);
        msgA.setTimestamps(todayMorning);

        Message msgB = new Message("me-1", "user-B", "text", "Good night", null);
        msgB.setTimestamps(yesterdayNight);

        when(messageRepository.findBySenderOrRecipientOrderByTimestampsDesc("me-1", "me-1"))
                .thenReturn(List.of(msgA, msgB));

        User userA = new User("user-A");
        userA.setEmail("a@example.com");
        userA.setFirstname("Alice");

        User userB = new User("user-B");
        userB.setEmail("b@example.com");
        userB.setFirstname("Bob");

        when(userRepository.findAllById(anyList())).thenReturn(List.of(userA, userB));

        mockMvc.perform(get("/api/contacts/dm-list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("user-A"))
                .andExpect(jsonPath("$[0].firstName").value("Alice"))
                .andExpect(jsonPath("$[1].id").value("user-B"))
                .andExpect(jsonPath("$[1].firstName").value("Bob"));
    }

    @Test
    void testSearchContactsExcludesPassword() throws Exception {
        SearchRequest request = new SearchRequest();
        request.setSearchTerm("John");

        User john = new User("john-id");
        john.setEmail("john@test.com");
        john.setPassword("superSecretHash");
        john.setFirstname("John");
        john.setLastname("Doe");

        when(userRepository.findByIdNotAndFirstnameRegexIgnoreCaseOrIdNotAndLastnameRegexIgnoreCaseOrIdNotAndEmailRegexIgnoreCase(
                eq("me-1"), any(), eq("me-1"), any(), eq("me-1"), any()
        )).thenReturn(List.of(john));

        mockMvc.perform(post("/api/contacts/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("john-id"))
                .andExpect(jsonPath("$[0].firstname").value("John"))
                .andExpect(jsonPath("$[0].firstName").value("John"))
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }
}
