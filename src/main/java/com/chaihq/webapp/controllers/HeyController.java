package com.chaihq.webapp.controllers;

import com.chaihq.webapp.models.*;
import com.chaihq.webapp.repositories.*;
import com.chaihq.webapp.services.ProjectAccess;
import com.chaihq.webapp.utilities.Constants;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class HeyController {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private TodoRepository todoRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private ActiveStorageFileRepository activeStorageFileRepository;

    @Autowired
    private ProjectAccess projectAccess;


    @GetMapping("/hey")
    public String index(Model model) {
        User user = projectAccess.currentUser();

        List<Notification> visible = new ArrayList<>();
        for (Notification notification : notificationRepository.findAllByForUserOrderByCreatedAtDesc(user)) {
            if (attach(notification, user)) {
                visible.add(notification);
            }
        }

        model.addAttribute("notifications", visible);
        return "hey/index";
    }

    // Loads what a notification points at. Returns false (and the notification is hidden) when it
    // no longer exists or is in a project the user isn't part of any more.
    private boolean attach(Notification notification, User user) {
        String type = notification.getType();
        long objectId = notification.getObjectId();

        if (Constants.NOTIFICATION_TYPE_MESSAGE.equals(type)) {
            Message message = messageRepository.findById(objectId).orElse(null);
            Project project = message == null ? null : projectRepository.findById(message.getProjectId()).orElse(null);
            if (!projectAccess.isMember(project, user) || Constants.DELETED.equals(message.getStatus())) return false;
            message.setContentToDisplay(html2text(message.getContent()));
            message.setProject(project);
            notification.setMessageObject(message);
        } else if (Constants.NOTIFICATION_TYPE_TODO.equals(type)) {
            Todo todo = todoRepository.findById(objectId).orElse(null);
            if (todo == null || !projectAccess.isMember(todo.getProject(), user)) return false;
            notification.setTodo(todo);
        } else if (Constants.NOTIFICATION_TYPE_MESSAGE_COMMENT.equals(type) || Constants.NOTIFICATION_TYPE_TODO_COMMENT.equals(type)) {
            Comment comment = commentRepository.findById(objectId).orElse(null);
            Project project = comment == null ? null : projectRepository.findById(comment.getProjectId()).orElse(null);
            if (!projectAccess.isMember(project, user)) return false;
            comment.setProject(project);
            comment.setTextToDisplay(html2text(comment.getText()));
            notification.setComment(comment);
        } else if (Constants.NOTIFICATION_TYPE_FILE.equals(type)) {
            ActiveStorageFile file = activeStorageFileRepository.findById(objectId).orElse(null);
            Project project = file == null || file.getProjectId() == null ? null : projectRepository.findById(file.getProjectId()).orElse(null);
            if (!projectAccess.isMember(project, user)) return false;
            file.setProject(project);
            notification.setActiveStorageFile(file);
        } else {
            return false;
        }
        return true;
    }

    @RequestMapping(method = RequestMethod.GET, value="/hasUnreadNotifications",
            produces = "application/json")
    @ResponseBody
    public boolean hasUnreadNotifications (HttpSession httpSession) {
        // TODO: Make sure the user has the access

        User currentUser = (User) httpSession.getAttribute(Constants.CURRENT_USER);

        if(currentUser != null) {
            List<Notification> notifications = notificationRepository.findAllByForUserAndReadIsFalse(currentUser);

            if(notifications.size() > 0) {
                return true;
            } else {
                return false;
            }

        } else {
            return false;
        }


    }

    public String html2text(String html) {
        return Jsoup.parse(html).text();
    }

}
