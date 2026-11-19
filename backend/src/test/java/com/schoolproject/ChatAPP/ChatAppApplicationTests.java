package com.schoolproject.ChatAPP;

import com.schoolproject.ChatAPP.repository.AttachmentRepository;
import com.schoolproject.ChatAPP.repository.ChannelRepository;
import com.schoolproject.ChatAPP.repository.MessageRepository;
import com.schoolproject.ChatAPP.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "spring.data.mongodb.uri=mongodb://localhost:27017/test_chat_app",
        "spring.data.mongodb.auto-index-creation=false",
        "jwt.secret=test-secret-key-for-spring-context-at-least-32-chars!"
})
class ChatAppApplicationTests {

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
    void contextLoads() {
    }
}
