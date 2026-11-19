package com.schoolproject.ChatAPP.controllers;

import com.schoolproject.ChatAPP.config.UserPrincipal;
import com.schoolproject.ChatAPP.model.Attachment;
import com.schoolproject.ChatAPP.model.Channel;
import com.schoolproject.ChatAPP.model.Message;
import com.schoolproject.ChatAPP.model.User;
import com.schoolproject.ChatAPP.repository.AttachmentRepository;
import com.schoolproject.ChatAPP.repository.ChannelRepository;
import com.schoolproject.ChatAPP.repository.MessageRepository;
import com.schoolproject.ChatAPP.service.FileValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class FileControllerTest {

    private static final byte[] VALID_PNG_BYTES = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
    };

    private static final byte[] VALID_PDF_BYTES = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".getBytes();

    private MockMvc mockMvc;
    private FileController fileController;

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ChannelRepository channelRepository;

    @TempDir
    Path tempUploadDir;

    private UserPrincipal currentPrincipal;

    @BeforeEach
    void setUp() {
        fileController = new FileController();
        ReflectionTestUtils.setField(fileController, "uploadDir", tempUploadDir.toString());
        ReflectionTestUtils.setField(fileController, "fileValidationService", new FileValidationService());
        ReflectionTestUtils.setField(fileController, "attachmentRepository", attachmentRepository);
        ReflectionTestUtils.setField(fileController, "messageRepository", messageRepository);
        ReflectionTestUtils.setField(fileController, "channelRepository", channelRepository);

        currentPrincipal = new UserPrincipal("uploader-1", "uploader@example.com");

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

        mockMvc = MockMvcBuilders.standaloneSetup(fileController)
                .setCustomArgumentResolvers(authPrincipalResolver)
                .build();
    }

    @Test
    void testUploadValidPngWithRealSignatureSucceeds() throws Exception {
        when(attachmentRepository.save(any(Attachment.class))).thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-image.png",
                MediaType.IMAGE_PNG_VALUE,
                VALID_PNG_BYTES
        );

        mockMvc.perform(multipart("/api/messages/upload-file").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("File uploaded successfully"))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.filePath").exists());
    }

    @Test
    void testUploadSpoofedPngContainingExecutableSignatureFails() throws Exception {
        byte[] mzExecutable = new byte[]{0x4D, 0x5A, (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "innocent-photo.png",
                MediaType.IMAGE_PNG_VALUE,
                mzExecutable
        );

        mockMvc.perform(multipart("/api/messages/upload-file").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Executable file content is not permitted"));
    }

    @Test
    void testUploadTextBytesRenamedAsPngFailsSignatureCheck() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fake.png",
                MediaType.IMAGE_PNG_VALUE,
                "not a real png file".getBytes()
        );

        mockMvc.perform(multipart("/api/messages/upload-file").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("File content does not match the declared file type"));
    }

    @Test
    void testUploadSvgRejectedToPreventStoredXss() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "vector.svg",
                "image/svg+xml",
                "<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>".getBytes()
        );

        mockMvc.perform(multipart("/api/messages/upload-file").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("File type not permitted for upload"));
    }

    @Test
    void testUploadHtmlDisguisedAsTxtRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "<!DOCTYPE html><html><script>alert(1)</script></html>".getBytes()
        );

        mockMvc.perform(multipart("/api/messages/upload-file").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("File content does not match the declared file type"));
    }

    @Test
    void testUploadDisallowedExtensionFails() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "malicious.exe",
                "application/x-msdownload",
                "binary payload".getBytes()
        );

        mockMvc.perform(multipart("/api/messages/upload-file").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("File type not permitted for upload"));
    }

    @Test
    void testUploadEmptyFileFails() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.txt",
                "text/plain",
                new byte[0]
        );

        mockMvc.perform(multipart("/api/messages/upload-file").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("File is required and cannot be empty"));
    }

    @Test
    void testPrivateDmAttachmentAuthorizedForParticipantAndDeniedForOutsider() throws Exception {
        Path dir = tempUploadDir.resolve("1700000000");
        Files.createDirectories(dir);
        Path stored = dir.resolve("uuid_report.pdf");
        Files.write(stored, VALID_PDF_BYTES);

        String relPath = "uploads/files/1700000000/uuid_report.pdf";
        Attachment attachment = new Attachment(relPath, "report.pdf", "uuid_report.pdf", "application/pdf", VALID_PDF_BYTES.length, "alice", "bob", null);
        Message dmMessage = new Message("alice", "bob", "file", null, relPath);

        when(attachmentRepository.findByFilePath(relPath)).thenReturn(Optional.of(attachment));
        when(messageRepository.findByFileUrl(relPath)).thenReturn(List.of(dmMessage));

        // Recipient (bob) is authorized
        currentPrincipal = new UserPrincipal("bob", "bob@example.com");
        mockMvc.perform(get("/uploads/files/1700000000/uuid_report.pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        // Outsider (eve) is denied with 403 Forbidden
        currentPrincipal = new UserPrincipal("eve", "eve@example.com");
        mockMvc.perform(get("/uploads/files/1700000000/uuid_report.pdf"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testPrivateChannelAttachmentAuthorizedOnlyForChannelMember() throws Exception {
        Path dir = tempUploadDir.resolve("1700000001");
        Files.createDirectories(dir);
        Path stored = dir.resolve("uuid_chart.png");
        Files.write(stored, VALID_PNG_BYTES);

        String relPath = "uploads/files/1700000001/uuid_chart.png";
        Attachment attachment = new Attachment(relPath, "chart.png", "uuid_chart.png", "image/png", VALID_PNG_BYTES.length, "alice", null, "chan-42");
        Message channelMsg = new Message("alice", null, "file", null, relPath);
        channelMsg.setChannelId("chan-42");

        Channel channel = new Channel();
        channel.setId("chan-42");
        channel.setAdmin(new User("alice"));
        channel.setMembers(List.of(new User("alice"), new User("member-2")));

        when(attachmentRepository.findByFilePath(relPath)).thenReturn(Optional.of(attachment));
        when(messageRepository.findByFileUrl(relPath)).thenReturn(List.of(channelMsg));
        when(channelRepository.findById("chan-42")).thenReturn(Optional.of(channel));

        // Channel member can view inline raster image
        currentPrincipal = new UserPrincipal("member-2", "m2@example.com");
        mockMvc.perform(get("/uploads/files/1700000001/uuid_chart.png"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("inline")))
                .andExpect(content().contentType(MediaType.IMAGE_PNG));

        // Non-member receives 403 Forbidden
        currentPrincipal = new UserPrincipal("outsider", "out@example.com");
        mockMvc.perform(get("/uploads/files/1700000001/uuid_chart.png"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testPathTraversalRejected() throws Exception {
        mockMvc.perform(get("/uploads/files/..%2F..%2Fetc/passwd"))
                .andExpect(status().isBadRequest());
    }
}
