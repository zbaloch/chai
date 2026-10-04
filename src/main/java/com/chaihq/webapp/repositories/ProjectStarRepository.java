package com.chaihq.webapp.repositories;

import com.chaihq.webapp.models.Project;
import com.chaihq.webapp.models.ProjectStar;
import com.chaihq.webapp.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface ProjectStarRepository extends JpaRepository<ProjectStar, Long> {
    Optional<ProjectStar> findFirstByProjectAndUser(Project project, User user);
    List<ProjectStar> findByUser(User user);

    @Transactional
    long deleteByProjectAndUser(Project project, User user);
}
