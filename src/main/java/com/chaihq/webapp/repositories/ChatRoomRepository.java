package com.chaihq.webapp.repositories;

import com.chaihq.webapp.models.Account;
import com.chaihq.webapp.models.ChatRoom;
import com.chaihq.webapp.models.Project;
import com.chaihq.webapp.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    Optional<ChatRoom> findFirstByProjectOrderByIdAsc(Project project);
    List<ChatRoom> findByProjectIn(Collection<Project> projects);
    List<ChatRoom> findByAccountAndProjectIsNullAndMembersContaining(Account account, User user);
}
