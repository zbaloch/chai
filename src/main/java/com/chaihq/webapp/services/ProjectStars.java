package com.chaihq.webapp.services;

import com.chaihq.webapp.models.Project;
import com.chaihq.webapp.models.User;
import com.chaihq.webapp.repositories.ProjectStarRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Projects someone starred to keep at the top of their lists. Personal: nobody else sees them. */
@Service
public class ProjectStars {

    private final ProjectStarRepository projectStarRepository;

    public ProjectStars(ProjectStarRepository projectStarRepository) {
        this.projectStarRepository = projectStarRepository;
    }

    public Set<Long> projectIds(User user) {
        return projectStarRepository.findByUser(user).stream()
                .map(star -> star.getProject().getId()).collect(Collectors.toSet());
    }

    /** The same projects with the starred ones first; otherwise keeps their order. */
    public List<Project> starredFirst(List<Project> projects, Set<Long> starredProjectIds) {
        return projects.stream()
                .sorted(Comparator.comparing(project -> !starredProjectIds.contains(project.getId())))
                .toList();
    }
}
