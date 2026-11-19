package com.schoolproject.ChatAPP.repository;

import com.schoolproject.ChatAPP.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByEmail(String email);

    List<User> findByIdNotAndFirstnameRegexIgnoreCaseOrIdNotAndLastnameRegexIgnoreCaseOrIdNotAndEmailRegexIgnoreCase(
            String id1, String firstname, String id2, String lastname, String id3, String email, Pageable pageable
    );

    List<User> findByIdNot(String id, Pageable pageable);

    List<User> findByIdNot(String id);

    Optional<User> findById(String id);
}
