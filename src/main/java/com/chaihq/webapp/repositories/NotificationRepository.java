package com.chaihq.webapp.repositories;

import com.chaihq.webapp.models.Notification;
import com.chaihq.webapp.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// Find update delete save etc
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    public List<Notification> findAllByForUserOrderByCreatedAtDesc(User user);
    public List<Notification> findAllByObjectId(Long objectId);
    public List<Notification> findAllByTypeAndForUserAndReadIsFalse(String type, User forUser);
    public List<Notification> findAllByTypeAndObjectId(String type, long objectId);
    public List<Notification> findAllByForUserAndReadIsFalse(User forUser);
    public List<Notification> findAllByObjectIdAndForUser(long objectId, User forUser);
    public List<Notification> findAllByTypeAndForUser(String type, User forUser);
}
