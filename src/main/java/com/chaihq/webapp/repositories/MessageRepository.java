package com.chaihq.webapp.repositories;

import com.chaihq.webapp.models.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    public List<Message> findAllByProjectIdOrderByCreatedAtDesc(Long projectId);
    // pattern like "%abc%"; used to find which post uses an upload
    @Query(value = "SELECT * FROM messages WHERE content LIKE :pattern", nativeQuery = true)
    List<Message> findByContentLike(@Param("pattern") String pattern);
}
