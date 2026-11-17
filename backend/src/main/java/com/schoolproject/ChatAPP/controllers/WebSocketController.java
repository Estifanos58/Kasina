package com.schoolproject.ChatAPP.controllers;

import com.schoolproject.ChatAPP.model.Message;
import com.schoolproject.ChatAPP.requests.ChannelMessage;
import com.schoolproject.ChatAPP.service.WebSocketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class WebSocketController {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketController.class);

    @Autowired
    private WebSocketService webSocketService;

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload Message message, Principal principal) {
        if (principal == null || principal.getName() == null) {
            logger.warn("Dropping unauthenticated direct message");
            return;
        }

        // Never trust client-supplied sender ID; derive identity from authenticated user
        String authenticatedUserId = principal.getName();
        message.setSender(authenticatedUserId);

        logger.info("Processing direct message from {} to {}", authenticatedUserId, message.getRecipient());
        webSocketService.sendMessage(message);
    }

    @MessageMapping("/chat.sendChannelMessage")
    public void sendChannelMessage(@Payload ChannelMessage channelMessage, Principal principal) {
        if (principal == null || principal.getName() == null) {
            logger.warn("Dropping unauthenticated channel message");
            return;
        }

        // Never trust client-supplied sender ID; derive identity from authenticated user
        String authenticatedUserId = principal.getName();
        channelMessage.setSender(authenticatedUserId);

        logger.info("Processing channel message from {} for channel {}", authenticatedUserId, channelMessage.getChannelId());
        webSocketService.sendChannelMessage(channelMessage);
    }

    @MessageMapping("/chat.addUser")
    public void addUser(@Payload Object payload, Principal principal) {
        if (principal != null) {
            logger.info("User {} joined chat via STOMP", principal.getName());
        }
    }
}
