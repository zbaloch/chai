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

    @Query(value = "SELECT * FROM todos WHERE project_id IN (:projectIds) "
            + "AND (LOWER(description) LIKE :pattern ESCAPE '!' OR LOWER(notes) LIKE :pattern ESCAPE '!') "
            + "ORDER BY created_at DESC LIMIT 200", nativeQuery = true)
    List<Todo> search(@Param("projectIds") List<Long> projectIds, @Param("pattern") String pattern);
}
