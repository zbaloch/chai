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
}
