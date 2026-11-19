package com.schoolproject.ChatAPP.repository;

import com.schoolproject.ChatAPP.model.Message;

import java.time.Instant;
import java.util.List;

public interface MessageRepositoryCustom {

    List<Message> findDirectMessagesPage(String user1, String user2, Instant beforeTimestamp, String beforeId, int limitPlusOne);

    List<Message> findChannelMessagesPage(String channelId, Instant beforeTimestamp, String beforeId, int limitPlusOne);

    List<DmContactSummary> findRecentDmContacts(String userId, int limit);
}
