package com.schoolproject.ChatAPP.repository;

import com.schoolproject.ChatAPP.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends MongoRepository<Message, String>, MessageRepositoryCustom {

    @Query("{ 'channelId': null, '$or': [ { '$and': [ { 'sender': ?0 }, { 'recipient': ?1 } ] }, { '$and': [ { 'sender': ?1 }, { 'recipient': ?0 } ] } ] }")
    List<Message> findMessagesBetweenUsers(String senderId, String recipientId);

    @Query("{ 'recipient': null, 'channelId': ?0 }")
    List<Message> findMessagesByChannelId(String channelId);

    List<Message> findByChannelIdOrderByTimestampsAsc(String channelId);

    Page<Message> findByChannelIdOrderByTimestampsAsc(String channelId, Pageable pageable);

    List<Message> findBySender(String senderId);

    List<Message> findByRecipient(String recipientId);

    List<Message> findBySenderAndRecipientOrRecipientAndSenderOrderByTimestamps(String sender1, String recipient1, String sender2, String recipient2);

    Page<Message> findBySenderAndRecipientOrRecipientAndSenderOrderByTimestamps(String sender1, String recipient1, String sender2, String recipient2, Pageable pageable);

    List<Message> findBySenderOrRecipientOrderByTimestampsDesc(String sender, String recipient);

    Optional<Message> findBySenderAndClientMessageId(String sender, String clientMessageId);

    List<Message> findByFileUrl(String fileUrl);
}
