package com.schoolproject.ChatAPP;

import com.schoolproject.ChatAPP.repository.ChannelRepository;
import com.schoolproject.ChatAPP.repository.MessageRepository;
import com.schoolproject.ChatAPP.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.mongodb.core.MongoTemplate;

@SpringBootTest(properties = {
        "spring.data.mongodb.uri=mongodb://localhost:27017/test_chat_app",
        "jwt.secret=test-secret-key-that-is-at-least-32-characters-long"
})
class ChatAppApplicationTests {

    @MockBean
    private MongoTemplate mongoTemplate;

    @MockBean
    private org.springframework.data.mongodb.gridfs.GridFsTemplate gridFsTemplate;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private MessageRepository messageRepository;

    @MockBean
    private ChannelRepository channelRepository;

    @Test
    void contextLoads() {
    }

}
