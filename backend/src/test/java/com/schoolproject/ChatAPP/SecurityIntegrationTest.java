package com.schoolproject.ChatAPP;

import com.schoolproject.ChatAPP.model.Attachment;
import com.schoolproject.ChatAPP.model.Message;
import com.schoolproject.ChatAPP.model.User;
import com.schoolproject.ChatAPP.repository.AttachmentRepository;
import com.schoolproject.ChatAPP.repository.ChannelRepository;
import com.schoolproject.ChatAPP.repository.MessageRepository;
import com.schoolproject.ChatAPP.repository.UserRepository;
import com.schoolproject.ChatAPP.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.data.mongodb.uri=mongodb://localhost:27017/test_chat_app",
        "spring.data.mongodb.auto-index-creation=false",
        "jwt.secret=integration-test-jwt-secret-key-at-least-32-characters!"
})
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @TempDir
    static Path tempUploadDir;

    @DynamicPropertySource
    static void registerUploadDir(DynamicPropertyRegistry registry) {
        registry.add("file.upload-dir", () -> tempUploadDir.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private MongoTemplate mongoTemplate;

    @MockitoBean
    private org.springframework.data.mongodb.gridfs.GridFsTemplate gridFsTemplate;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private MessageRepository messageRepository;

    @MockitoBean
    private ChannelRepository channelRepository;

    @MockitoBean
    private AttachmentRepository attachmentRepository;

    @Test
    void testUnauthenticatedAccessToProtectedEndpointsAndAttachmentsDenied() throws Exception {
        mockMvc.perform(get("/api/user/info"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/uploads/files/1700000000/secret.png"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testCsrfProtectionEnforcedOnCookieAuthenticatedMutations() throws Exception {
        String token = jwtUtil.generateToken("alice@example.com", "alice-1");

        // 1. Cookie-authenticated POST WITHOUT CSRF token -> 403 Forbidden
        mockMvc.perform(post("/api/messages/get-messages")
                        .cookie(new Cookie("jwt", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"bob-2\"}"))
                .andExpect(status().isForbidden());

        // 2. Fetch CSRF token from /api/auth/csrf
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-XSRF-TOKEN"))
                .andReturn();

        Cookie xsrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        String xsrfHeader = csrfResult.getResponse().getHeader("X-XSRF-TOKEN");
        assertNotNull(xsrfCookie);
        assertNotNull(xsrfHeader);

        when(messageRepository.findDirectMessagesPage("alice-1", "bob-2", null, null, 51))
                .thenReturn(List.of());

        // 3. Cookie-authenticated POST WITH matching XSRF-TOKEN cookie and X-XSRF-TOKEN header -> 200 OK
        mockMvc.perform(post("/api/messages/get-messages")
                        .cookie(new Cookie("jwt", token), xsrfCookie)
                        .header("X-XSRF-TOKEN", xsrfHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"bob-2\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void testBearerAuthenticatedRequestWorksWithoutCsrfCookie() throws Exception {
        String token = jwtUtil.generateToken("alice@example.com", "alice-1");
        when(messageRepository.findDirectMessagesPage("alice-1", "bob-2", null, null, 51))
                .thenReturn(List.of());

        mockMvc.perform(post("/api/messages/get-messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"bob-2\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void testPrivateAttachmentDownloadThroughFullSecurityChain() throws Exception {
        Path dir = tempUploadDir.resolve("1700000999");
        Files.createDirectories(dir);
        byte[] pngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        Files.write(dir.resolve("private_doc.png"), pngBytes);

        String relPath = "uploads/files/1700000999/private_doc.png";
        Attachment attachment = new Attachment(relPath, "doc.png", "private_doc.png", "image/png", pngBytes.length, "alice-1", "bob-2", null);
        Message msg = new Message("alice-1", "bob-2", "file", null, relPath);

        when(attachmentRepository.findByFilePath(relPath)).thenReturn(Optional.of(attachment));
        when(messageRepository.findByFileUrl(relPath)).thenReturn(List.of(msg));

        String bobJwt = jwtUtil.generateToken("bob@example.com", "bob-2");
        String eveJwt = jwtUtil.generateToken("eve@example.com", "eve-9");

        // Participant succeeds
        mockMvc.perform(get("/uploads/files/1700000999/private_doc.png")
                        .cookie(new Cookie("jwt", bobJwt)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));

        // Non-participant denied with 403
        mockMvc.perform(get("/uploads/files/1700000999/private_doc.png")
                        .cookie(new Cookie("jwt", eveJwt)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testHealthLivenessAndReadinessEndpoints() throws Exception {
        mockMvc.perform(get("/api/health/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        when(mongoTemplate.executeCommand(any(Document.class)))
                .thenReturn(new Document("ok", 1.0));

        mockMvc.perform(get("/api/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.checks.mongodb").value("UP"));

        when(mongoTemplate.executeCommand(any(Document.class)))
                .thenThrow(new RuntimeException("Mongo unreachable"));

        mockMvc.perform(get("/api/health/ready"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.checks.mongodb").value("DOWN"));
    }
}
