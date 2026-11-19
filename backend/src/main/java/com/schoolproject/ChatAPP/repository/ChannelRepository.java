package com.schoolproject.ChatAPP.repository;

import com.schoolproject.ChatAPP.model.Channel;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChannelRepository extends MongoRepository<Channel, String> {

    List<Channel> findByAdmin_Id(String adminId);

    Optional<Channel> findById(String channelId);

    List<Channel> findByMembers_Id(String memberId);

    List<Channel> findByAdmin_IdOrMembers_IdOrderByUpdatedAtDesc(String adminId, String memberId);

    List<Channel> findByNameRegex(String name);
}
