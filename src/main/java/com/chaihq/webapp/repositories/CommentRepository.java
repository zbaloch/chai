package com.chaihq.webapp.repositories;

import com.chaihq.webapp.models.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

// Find update delete save etc
public interface CommentRepository extends JpaRepository<Comment, Long> {
    public List<Comment> findAllByMessageIdOrderByCreatedAtAsc(long messageId);
    public List<Comment> findAllByTodoIdOrderByCreatedAtAsc(long todoId);
    @Query(value = "SELECT * FROM comments WHERE text LIKE :pattern", nativeQuery = true)
    List<Comment> findByTextLike(@Param("pattern") String pattern);

    @Query(value = "SELECT * FROM comments WHERE project_id IN (:projectIds) AND LOWER(text) LIKE :pattern ESCAPE '!' "
            + "ORDER BY created_at DESC LIMIT 200", nativeQuery = true)
    List<Comment> search(@Param("projectIds") List<Long> projectIds, @Param("pattern") String pattern);
}
