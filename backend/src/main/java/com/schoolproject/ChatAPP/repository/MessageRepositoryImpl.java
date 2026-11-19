package com.schoolproject.ChatAPP.repository;

import com.schoolproject.ChatAPP.model.Message;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class MessageRepositoryImpl implements MessageRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Autowired
    public MessageRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public List<Message> findDirectMessagesPage(String user1, String user2, Instant beforeTimestamp, String beforeId, int limitPlusOne) {
        List<Criteria> andCriteria = new ArrayList<>();
        andCriteria.add(Criteria.where("channelId").is(null));
        andCriteria.add(new Criteria().orOperator(
                new Criteria().andOperator(Criteria.where("sender").is(user1), Criteria.where("recipient").is(user2)),
                new Criteria().andOperator(Criteria.where("sender").is(user2), Criteria.where("recipient").is(user1))
        ));

        addCursorCriteria(andCriteria, beforeTimestamp, beforeId);

        Query query = new Query(new Criteria().andOperator(andCriteria.toArray(new Criteria[0])))
                .with(Sort.by(Sort.Order.desc("timestamps"), Sort.Order.desc("_id")))
                .limit(limitPlusOne);

        return mongoTemplate.find(query, Message.class);
    }

    @Override
    public List<Message> findChannelMessagesPage(String channelId, Instant beforeTimestamp, String beforeId, int limitPlusOne) {
        List<Criteria> andCriteria = new ArrayList<>();
        andCriteria.add(Criteria.where("channelId").is(channelId));

        addCursorCriteria(andCriteria, beforeTimestamp, beforeId);

        Query query = new Query(new Criteria().andOperator(andCriteria.toArray(new Criteria[0])))
                .with(Sort.by(Sort.Order.desc("timestamps"), Sort.Order.desc("_id")))
                .limit(limitPlusOne);

        return mongoTemplate.find(query, Message.class);
    }

    @Override
    public List<DmContactSummary> findRecentDmContacts(String userId, int limit) {
        Criteria matchCriteria = new Criteria().andOperator(
                Criteria.where("channelId").is(null),
                new Criteria().orOperator(
                        Criteria.where("sender").is(userId),
                        Criteria.where("recipient").is(userId)
                )
        );

        ConditionalOperators.Cond contactIdExpr = ConditionalOperators
                .when(Criteria.where("sender").is(userId))
                .thenValueOf("$recipient")
                .otherwiseValueOf("$sender");

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(matchCriteria),
                Aggregation.sort(Sort.Direction.DESC, "timestamps"),
                Aggregation.project("timestamps").and(contactIdExpr).as("contactId"),
                Aggregation.match(Criteria.where("contactId").ne(null).andOperator(Criteria.where("contactId").ne(userId))),
                Aggregation.group("contactId").first("timestamps").as("lastMessageTime"),
                Aggregation.project("lastMessageTime").and("_id").as("contactId"),
                Aggregation.sort(Sort.Direction.DESC, "lastMessageTime"),
                Aggregation.limit(limit)
        );

        AggregationResults<DmContactSummary> results =
                mongoTemplate.aggregate(aggregation, "messages", DmContactSummary.class);
        return results.getMappedResults();
    }

    private void addCursorCriteria(List<Criteria> andCriteria, Instant beforeTimestamp, String beforeId) {
        if (beforeTimestamp == null) {
            return;
        }
        if (beforeId != null && !beforeId.isBlank()) {
            Object idValue = ObjectId.isValid(beforeId) ? new ObjectId(beforeId) : beforeId;
            andCriteria.add(new Criteria().orOperator(
                    Criteria.where("timestamps").lt(beforeTimestamp),
                    new Criteria().andOperator(
                            Criteria.where("timestamps").is(beforeTimestamp),
                            Criteria.where("_id").lt(idValue)
                    )
            ));
        } else {
            andCriteria.add(Criteria.where("timestamps").lt(beforeTimestamp));
        }
    }
}
