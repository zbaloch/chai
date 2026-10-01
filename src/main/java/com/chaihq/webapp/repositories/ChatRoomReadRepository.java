package com.chaihq.webapp.repositories;

import com.chaihq.webapp.models.ChatRoom;
import com.chaihq.webapp.models.ChatRoomRead;
import com.chaihq.webapp.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChatRoomReadRepository extends JpaRepository<ChatRoomRead, Long> {
    Optional<ChatRoomRead> findFirstByRoomAndUser(ChatRoom room, User user);
    List<ChatRoomRead> findByUserAndRoomIn(User user, Collection<ChatRoom> rooms);
    List<ChatRoomRead> findByRoom(ChatRoom room);
}
