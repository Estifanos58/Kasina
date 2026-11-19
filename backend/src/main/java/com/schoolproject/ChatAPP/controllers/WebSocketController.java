package com.schoolproject.ChatAPP.controllers;

import com.schoolproject.ChatAPP.requests.ChannelMessage;
import com.schoolproject.ChatAPP.requests.DirectMessageRequest;
import com.schoolproject.ChatAPP.responses.ErrorResponse;
import com.schoolproject.ChatAPP.service.WebSocketService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class WebSocketController {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketController.class);

    @Autowired
    private WebSocketService webSocketService;

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Valid @Payload DirectMessageRequest message, Principal principal) {
        if (principal == null || principal.getName() == null) {
            logger.warn("Dropping unauthenticated direct message");
            return;
        }

        // Always derive sender identity from the authenticated security principal
        String authenticatedUserId = principal.getName();
        message.setSender(authenticatedUserId);

        logger.debug("Processing direct message from {} to {}", authenticatedUserId, message.getRecipient());
        webSocketService.sendMessage(message);
    }

    @MessageMapping("/chat.sendChannelMessage")
    public void sendChannelMessage(@Valid @Payload ChannelMessage channelMessage, Principal principal) {
        if (principal == null || principal.getName() == null) {
            logger.warn("Dropping unauthenticated channel message");
            return;
        }

        // Always derive sender identity from the authenticated security principal
        String authenticatedUserId = principal.getName();
        channelMessage.setSender(authenticatedUserId);

        logger.debug("Processing channel message from {} for channel {}", authenticatedUserId, channelMessage.getChannelId());
        webSocketService.sendChannelMessage(channelMessage);
    }

    @MessageMapping("/chat.addUser")
    public void addUser(@Payload(required = false) Object payload, Principal principal) {
        if (principal != null) {
            logger.debug("User {} joined chat via STOMP", principal.getName());
        }
    }

    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    @SendToUser("/queue/errors")
    public ErrorResponse handleValidationError(MethodArgumentNotValidException ex) {
        logger.warn("STOMP message validation error: {}", ex.getMessage());
        return new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Invalid message payload");
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/errors")
    public ErrorResponse handleGeneralError(Exception ex) {
        logger.error("STOMP message processing error", ex);
        return new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Unable to process message");
    }
}
