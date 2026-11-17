package com.schoolproject.ChatAPP.controllers;

import com.schoolproject.ChatAPP.config.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FileControllerTest {

    private MockMvc mockMvc;
    private FileController fileController;

    @TempDir
    Path tempUploadDir;

    private UserPrincipal currentPrincipal;

    @BeforeEach
    void setUp() {
        fileController = new FileController();
        ReflectionTestUtils.setField(fileController, "uploadDir", tempUploadDir.toString());

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
    void testUploadValidFileSuccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-image.png",
                MediaType.IMAGE_PNG_VALUE,
                "fake image content bytes".getBytes()
        );

        mockMvc.perform(multipart("/api/messages/upload-file").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("File uploaded successfully"))
                .andExpect(jsonPath("$.filePath").exists());
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
    void testPathTraversalRejected() throws Exception {
        mockMvc.perform(get("/uploads/files/..%2F..%2Fetc/passwd"))
                .andExpect(status().isBadRequest());
    }
}
