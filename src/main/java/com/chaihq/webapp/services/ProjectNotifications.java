package com.chaihq.webapp.services;

import com.chaihq.webapp.models.Notification;
import com.chaihq.webapp.models.Project;
import com.chaihq.webapp.models.User;
import com.chaihq.webapp.repositories.NotificationRepository;
import com.chaihq.webapp.utilities.Constants;
import org.jsoup.Jsoup;
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
        notifyProject(project, from, type, objectId, text, Set.of(), null);
    }

    /**
     * Notify the people on the project about something new, except whoever did it. People in
     * `mentioned` are told they were @mentioned (mentionText) instead of the general notice.
     */
    public void notifyProject(Project project, User from, String type, long objectId, String text,
                              Set<Long> mentioned, String mentionText) {
        List<User> recipients = new ArrayList<>(project.getUsers());
        Set<Long> notified = new HashSet<>();
        for (User user : recipients) {
            if (user == null || user.getId() == from.getId() || !notified.add(user.getId())) {
                continue;
            }
            save(user, from, type, objectId, mentioned.contains(user.getId()) ? mentionText : text);
        }
    }

    /** After an edit: tell people who are @mentioned now but weren't before. */
    public void notifyNewlyMentioned(Project project, User from, String type, long objectId, String mentionText,
                                     Set<Long> before, Set<Long> after) {
        for (User user : project.getUsers()) {
            if (user != null && user.getId() != from.getId() && after.contains(user.getId()) && !before.contains(user.getId())) {
                save(user, from, type, objectId, mentionText);
            }
        }
    }

    /** Who rich text (a comment) @mentions, among the project's people. */
    public static Set<Long> mentionedIn(String html, Project project) {
        String text = html == null ? "" : Jsoup.parse(html).text();
        return Chats.mentionedIds(text, new ArrayList<>(project.getUsers()));
    }

    public void markRead(long objectId, User user) {
        for (Notification notification : notificationRepository.findAllByObjectIdAndForUser(objectId, user)) {
            // Chat mentions point at chat messages, whose ids can match a comment's or a message's
            if (Constants.NOTIFICATION_TYPE_CHAT_MENTION.equals(notification.getType())) {
                continue;
            }
            notification.setRead(true);
            notification.setReadAt(Calendar.getInstance());
            notificationRepository.save(notification);
        }
    }

    private void save(User to, User from, String type, long objectId, String text) {
        Notification notification = new Notification();
        notification.setType(type);
        notification.setObjectId(objectId);
        notification.setMessage(text);
        notification.setCreatedAt(Calendar.getInstance());
        notification.setFromUser(from);
        notification.setForUser(to);
        notificationRepository.save(notification);
    }
}
