package com.schoolproject.ChatAPP.repository;

import com.schoolproject.ChatAPP.model.Attachment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AttachmentRepository extends MongoRepository<Attachment, String> {
    Optional<Attachment> findByFilePath(String filePath);
}
