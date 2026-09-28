package com.chaihq.webapp.repositories;

import com.chaihq.webapp.models.Account;
import com.chaihq.webapp.models.Project;
import com.chaihq.webapp.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// Find update delete save etc
public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByAccountOrderByNameAsc(Account account);
    List<Project> findDistinctByAccountAndUsersContainingOrderByNameAsc(Account account, User user);
}
