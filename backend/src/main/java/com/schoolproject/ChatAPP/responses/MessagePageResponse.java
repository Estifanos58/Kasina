package com.schoolproject.ChatAPP.responses;

import java.util.List;

public class MessagePageResponse {
    private List<MessageResponseDTO> messages;
    private String nextCursor;
    private boolean hasMore;

    public MessagePageResponse() {}

    public MessagePageResponse(List<MessageResponseDTO> messages, String nextCursor, boolean hasMore) {
        this.messages = messages;
        this.nextCursor = nextCursor;
        this.hasMore = hasMore;
    }

    public List<MessageResponseDTO> getMessages() {
        return messages;
    }

    public void setMessages(List<MessageResponseDTO> messages) {
        this.messages = messages;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }
}
