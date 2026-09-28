package com.chaihq.webapp.services;

import com.chaihq.webapp.models.Notification;
import com.chaihq.webapp.models.Project;
import com.chaihq.webapp.models.User;
import com.chaihq.webapp.repositories.NotificationRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ProjectNotifications {

    private final NotificationRepository notificationRepository;

    public ProjectNotifications(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /** Notify the people on the project about something new, except whoever did it. */
    public void notifyProject(Project project, User from, String type, long objectId, String text) {
        List<User> recipients = new ArrayList<>(project.getUsers());
        Set<Long> notified = new HashSet<>();
        for (User user : recipients) {
            if (user == null || user.getId() == from.getId() || !notified.add(user.getId())) {
                continue;
            }
            Notification notification = new Notification();
            notification.setType(type);
            notification.setObjectId(objectId);
            notification.setMessage(text);
            notification.setCreatedAt(Calendar.getInstance());
            notification.setFromUser(from);
            notification.setForUser(user);
            notificationRepository.save(notification);
        }
    }

    public void markRead(long objectId, User user) {
        for (Notification notification : notificationRepository.findAllByObjectIdAndForUser(objectId, user)) {
            notification.setRead(true);
            notification.setReadAt(Calendar.getInstance());
            notificationRepository.save(notification);
        }
    }
}
