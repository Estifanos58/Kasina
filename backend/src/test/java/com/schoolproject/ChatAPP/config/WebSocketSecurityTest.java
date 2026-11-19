package com.schoolproject.ChatAPP.config;

import com.schoolproject.ChatAPP.model.Channel;
import com.schoolproject.ChatAPP.model.User;
import com.schoolproject.ChatAPP.repository.ChannelRepository;
import com.schoolproject.ChatAPP.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketSecurityTest {

    private JwtUtil jwtUtil;
    private JwtHandshakeInterceptor handshakeInterceptor;
    private StompAuthChannelInterceptor channelInterceptor;

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private MessageChannel messageChannel;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "configuredSecret", "websocket-security-test-secret-key-32-chars-min!");
        ReflectionTestUtils.setField(jwtUtil, "expirationTimeMs", 3600000L);
        jwtUtil.init();

        handshakeInterceptor = new JwtHandshakeInterceptor(jwtUtil);
        channelInterceptor = new StompAuthChannelInterceptor(jwtUtil, channelRepository);
    }

    @Test
    void testUsernameHeaderBypassIsCompletelyRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("username", "victim-user-id");
        accessor.setLeaveMutable(true);

        Message<byte[]> connectFrame = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(AccessDeniedException.class, () ->
                channelInterceptor.preSend(connectFrame, messageChannel));
    }

    @Test
    void testConnectWithInvalidJwtRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer forged.invalid.jwt");
        accessor.setLeaveMutable(true);

        Message<byte[]> connectFrame = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(AccessDeniedException.class, () ->
                channelInterceptor.preSend(connectFrame, messageChannel));
    }

    @Test
    void testConnectWithValidJwtBearerHeaderAuthenticatesPrincipal() {
        String validToken = jwtUtil.generateToken("alice@example.com", "alice-1");

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + validToken);
        accessor.setLeaveMutable(true);

        Message<byte[]> connectFrame = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        channelInterceptor.preSend(connectFrame, messageChannel);

        assertNotNull(accessor.getUser());
        assertEquals("alice-1", accessor.getUser().getName());
    }

    @Test
    void testHandshakeValidatesHttpOnlyJwtCookieAndAuthenticatesStompConnect() {
        String validToken = jwtUtil.generateToken("bob@example.com", "bob-2");
        MockHttpServletRequest servletReq = new MockHttpServletRequest();
        servletReq.setCookies(new Cookie("jwt", validToken));
        MockHttpServletResponse servletRes = new MockHttpServletResponse();

        Map<String, Object> attributes = new HashMap<>();
        boolean allowed = handshakeInterceptor.beforeHandshake(
                new ServletServerHttpRequest(servletReq),
                new ServletServerHttpResponse(servletRes),
                mock(WebSocketHandler.class),
                attributes
        );

        assertTrue(allowed);
        assertTrue(attributes.containsKey(JwtHandshakeInterceptor.VERIFIED_PRINCIPAL_ATTR));

        // STOMP CONNECT without native token should authenticate via the cryptographically verified handshake principal
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionAttributes(attributes);
        accessor.setLeaveMutable(true);
        Message<byte[]> connectFrame = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        channelInterceptor.preSend(connectFrame, messageChannel);
        assertNotNull(accessor.getUser());
        assertEquals("bob-2", accessor.getUser().getName());
    }

    @Test
    void testHandshakeRejectsInvalidJwtCookieWith401() {
        MockHttpServletRequest servletReq = new MockHttpServletRequest();
        servletReq.setCookies(new Cookie("jwt", "invalid.cookie.token"));
        MockHttpServletResponse servletRes = new MockHttpServletResponse();

        boolean allowed = handshakeInterceptor.beforeHandshake(
                new ServletServerHttpRequest(servletReq),
                new ServletServerHttpResponse(servletRes),
                mock(WebSocketHandler.class),
                new HashMap<>()
        );

        assertFalse(allowed);
        assertEquals(401, servletRes.getStatus());
    }

    @Test
    void testSubscribePublicTopicAndCrossUserQueueRejected() {
        UserPrincipal alice = new UserPrincipal("alice-1", "alice@example.com");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(alice, null, alice.getAuthorities());

        // /topic/public must be blocked
        StompHeaderAccessor publicSub = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        publicSub.setUser(auth);
        publicSub.setDestination("/topic/public");
        assertThrows(AccessDeniedException.class, () ->
                channelInterceptor.preSend(MessageBuilder.createMessage(new byte[0], publicSub.getMessageHeaders()), messageChannel));

        // Cross-user subscription must be blocked
        StompHeaderAccessor crossUserSub = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        crossUserSub.setUser(auth);
        crossUserSub.setDestination("/user/bob-2/queue/messages");
        assertThrows(AccessDeniedException.class, () ->
                channelInterceptor.preSend(MessageBuilder.createMessage(new byte[0], crossUserSub.getMessageHeaders()), messageChannel));

        // Own /user/queue/messages and /user/queue/ack must be allowed
        StompHeaderAccessor ownSub = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        ownSub.setUser(auth);
        ownSub.setDestination("/user/queue/ack");
        assertDoesNotThrow(() ->
                channelInterceptor.preSend(MessageBuilder.createMessage(new byte[0], ownSub.getMessageHeaders()), messageChannel));
    }

    @Test
    void testSubscribeChannelVerifiesMembership() {
        UserPrincipal alice = new UserPrincipal("alice-1", "alice@example.com");
        UserPrincipal eve = new UserPrincipal("eve-9", "eve@example.com");

        Channel channel = new Channel();
        channel.setId("chan-99");
        channel.setAdmin(new User("admin-1"));
        channel.setMembers(List.of(new User("alice-1")));

        when(channelRepository.findById("chan-99")).thenReturn(Optional.of(channel));

        // Member allowed
        StompHeaderAccessor memberSub = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        memberSub.setUser(new UsernamePasswordAuthenticationToken(alice, null, alice.getAuthorities()));
        memberSub.setDestination("/topic/channel/chan-99");
        assertDoesNotThrow(() ->
                channelInterceptor.preSend(MessageBuilder.createMessage(new byte[0], memberSub.getMessageHeaders()), messageChannel));

        // Non-member rejected
        StompHeaderAccessor eveSub = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        eveSub.setUser(new UsernamePasswordAuthenticationToken(eve, null, eve.getAuthorities()));
        eveSub.setDestination("/topic/channel/chan-99");
        assertThrows(AccessDeniedException.class, () ->
                channelInterceptor.preSend(MessageBuilder.createMessage(new byte[0], eveSub.getMessageHeaders()), messageChannel));
    }

    @Test
    void testSessionManagerTracksMultiSessionConnectAndDisconnectEvents() {
        WebSocketSessionManager manager = new WebSocketSessionManager();
        WebSocketEventListener listener = new WebSocketEventListener(manager);

        UserPrincipal alice = new UserPrincipal("alice-1", "alice@example.com");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(alice, null, alice.getAuthorities());

        StompHeaderAccessor s1 = StompHeaderAccessor.create(StompCommand.CONNECTED);
        s1.setSessionId("sess-1");
        s1.setUser(auth);
        listener.handleWebSocketConnectedListener(new SessionConnectedEvent(this, MessageBuilder.createMessage(new byte[0], s1.getMessageHeaders()), auth));

        StompHeaderAccessor s2 = StompHeaderAccessor.create(StompCommand.CONNECTED);
        s2.setSessionId("sess-2");
        s2.setUser(auth);
        listener.handleWebSocketConnectedListener(new SessionConnectedEvent(this, MessageBuilder.createMessage(new byte[0], s2.getMessageHeaders()), auth));

        assertTrue(manager.isUserOnline("alice-1"));
        assertEquals(2, manager.getSessions("alice-1").size());
        assertEquals(2, manager.getActiveSessionCount());

        // Disconnect first session -> user still online on second tab
        StompHeaderAccessor d1 = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        d1.setSessionId("sess-1");
        d1.setUser(auth);
        listener.handleWebSocketDisconnectListener(new SessionDisconnectEvent(this, MessageBuilder.createMessage(new byte[0], d1.getMessageHeaders()), "sess-1", CloseStatus.NORMAL, auth));

        assertTrue(manager.isUserOnline("alice-1"));
        assertEquals(1, manager.getActiveSessionCount());

        // Disconnect second session -> user offline
        StompHeaderAccessor d2 = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        d2.setSessionId("sess-2");
        d2.setUser(auth);
        listener.handleWebSocketDisconnectListener(new SessionDisconnectEvent(this, MessageBuilder.createMessage(new byte[0], d2.getMessageHeaders()), "sess-2", CloseStatus.NORMAL, auth));

        assertFalse(manager.isUserOnline("alice-1"));
        assertEquals(0, manager.getActiveSessionCount());
        assertEquals(0, manager.getOnlineUserCount());
    }
}
