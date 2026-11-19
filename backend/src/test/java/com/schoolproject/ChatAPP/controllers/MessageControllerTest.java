package com.schoolproject.ChatAPP.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolproject.ChatAPP.config.GlobalExceptionHandler;
import com.schoolproject.ChatAPP.config.UserPrincipal;
import com.schoolproject.ChatAPP.model.Message;
import com.schoolproject.ChatAPP.repository.MessageRepository;
import com.schoolproject.ChatAPP.util.MessageCursor;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class MessageControllerTest {

    private MockMvc mockMvc;

    @Mock
    private MessageRepository messageRepository;

    @InjectMocks
    private MessageController messageController;

    private UserPrincipal currentPrincipal;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        currentPrincipal = new UserPrincipal("alice-1", "alice@example.com");

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

        mockMvc = MockMvcBuilders.standaloneSetup(messageController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(authPrincipalResolver)
                .build();
    }

    @Test
    void testDirectMessagesCursorPaginationReturnsChronologicalOrderAndCursor() throws Exception {
        Message m3 = new Message("alice-1", "bob-2", "text", "Msg 3", null);
        m3.setId("id-3");
        m3.setTimestamps(Instant.parse("2026-11-18T10:03:00Z"));

        Message m2 = new Message("bob-2", "alice-1", "text", "Msg 2", null);
        m2.setId("id-2");
        m2.setTimestamps(Instant.parse("2026-11-18T10:02:00Z"));

        Message m1 = new Message("alice-1", "bob-2", "text", "Msg 1", null);
        m1.setId("id-1");
        m1.setTimestamps(Instant.parse("2026-11-18T10:01:00Z"));

        when(messageRepository.findDirectMessagesPage(eq("alice-1"), eq("bob-2"), isNull(), isNull(), eq(3)))
                .thenReturn(List.of(m3, m2, m1));

        String expectedCursor = MessageCursor.encode(m2.getTimestamps(), m2.getId());

        mockMvc.perform(post("/api/messages/get-messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", "bob-2",
                                "limit", 2,
                                "paginated", true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.nextCursor").value(expectedCursor))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].id").value("id-2"))
                .andExpect(jsonPath("$.messages[1].id").value("id-3"));
    }

    @Test
    void testInvalidCursorReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/messages/get-messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", "bob-2",
                                "cursor", "%%%not-valid-base64%%%",
                                "paginated", true
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid pagination cursor"));
    }
}
