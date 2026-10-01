package com.chaihq.webapp.repositories;

import com.chaihq.webapp.models.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// Find update delete save etc
public interface ChatRepository extends JpaRepository<Chat, Long> {
    // A chat shows its latest page of messages; earlier ones load a page at a time
    public List<Chat> findTop100ByRoomIdOrderByIdDesc(Long roomId);
    public List<Chat> findTop100ByRoomIdAndIdLessThanOrderByIdDesc(Long roomId, Long id);
    public boolean existsByRoomIdAndIdLessThan(Long roomId, Long id);
    public Optional<Chat> findFirstByRoomIdOrderByCreatedAtDescIdDesc(Long roomId);

    // A project's chat from before chat rooms existed joins the project's room
    @Modifying
    @Query("update chat_messages c set c.roomId = :roomId where c.projectId = :projectId and c.roomId is null")
    int attachToRoom(@Param("projectId") long projectId, @Param("roomId") Long roomId);

    @Query(value = "SELECT * FROM chat_messages WHERE room_id IN (:roomIds) AND LOWER(message) LIKE :pattern ESCAPE '!' "
            + "ORDER BY created_at DESC LIMIT 200", nativeQuery = true)
    List<Chat> search(@Param("roomIds") List<Long> roomIds, @Param("pattern") String pattern);
}
