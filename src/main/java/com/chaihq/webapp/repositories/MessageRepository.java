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

    // Search: pattern is lower-case "%term%" with LIKE wildcards escaped by '!'
    @Query(value = "SELECT * FROM messages WHERE project_id IN (:projectIds) AND (status IS NULL OR status <> 'deleted') "
            + "AND (LOWER(title) LIKE :pattern ESCAPE '!' OR LOWER(content) LIKE :pattern ESCAPE '!') "
            + "ORDER BY created_at DESC LIMIT 200", nativeQuery = true)
    List<Message> search(@Param("projectIds") List<Long> projectIds, @Param("pattern") String pattern);
}
