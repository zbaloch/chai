package com.chaihq.webapp.repositories;

import com.chaihq.webapp.models.Project;
import com.chaihq.webapp.models.Todo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TodoRepository extends JpaRepository<Todo, Long> {
    public List<Todo> findAllByProjectOrderByDueDateAsc(Project project);
    public List<Todo> findAllByProjectAndAndDoneOrderByDueDateAsc(Project project, boolean done);
    public List<Todo> findAllByProjectAndDoneOrderByPositionAscDueDateAsc(Project project, boolean done);
    public Long countByProjectAndDone(Project project, boolean done);
    @Query(value = "SELECT * FROM todos WHERE notes LIKE :pattern", nativeQuery = true)
    List<Todo> findByNotesLike(@Param("pattern") String pattern);
}
