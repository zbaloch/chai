package com.chaihq.webapp.controllers;

import com.chaihq.webapp.models.*;
import com.chaihq.webapp.repositories.*;
import com.chaihq.webapp.utilities.Constants;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
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


    @GetMapping("/hey")
    public String index(Model model, HttpSession httpSession) {
        Project project = projectRepository.getReferenceById( (long) 1); // TODO make show this belongs to the user
        User user = (User) httpSession.getAttribute(Constants.CURRENT_USER);

        // List<Notification> notifications =  notificationRepository.findAll();
        List<Notification> notifications = notificationRepository.findAllByForUserOrderByCreatedAtDesc(user);

        for(int i=0; i<notifications.size(); i++) {
            Notification notification = notifications.get(i);
            String notificationType = notification.getType();
            String notificationMessage = notification.getMessage();
            long objectId = notification.getObjectId();
            if(notificationType.equals(Constants.NOTIFICATION_TYPE_MESSAGE)) {
                Message message = messageRepository.getReferenceById(objectId); // TODO: Need to remove this
                message.setContentToDisplay(html2text(message.getContent()));
                message.setProject( projectRepository.getReferenceById(message.getProjectId()) ); // TODO: Need to remove this
                notification.setMessageObject(message);

                // Its a message
            }
            if(notificationType.equals(Constants.NOTIFICATION_TYPE_TODO)) {
                notification.setTodo(todoRepository.getReferenceById(objectId));
                // Its a todo
            }
            if(notificationType.equals(Constants.NOTIFICATION_TYPE_MESSAGE_COMMENT)) {
                // Its a message comment
                Comment comment = commentRepository.getReferenceById(objectId);
                comment.setProject(projectRepository.getReferenceById(objectId));
                comment.setTextToDisplay(html2text(comment.getText()));
                notification.setComment( comment );
            }
            if(notificationType.equals(Constants.NOTIFICATION_TYPE_TODO_COMMENT)) {
                Comment comment = commentRepository.getReferenceById(objectId);
                comment.setProject(projectRepository.getReferenceById(objectId));
                comment.setTextToDisplay(html2text(comment.getText()));
                notification.setComment( comment );

                // Its a todo comment
            }
            if(notificationType.equals(Constants.NOTIFICATION_TYPE_FILE)) {
                // Its a file
                ActiveStorageFile file  = activeStorageFileRepository.getReferenceById(objectId); // TODO: Need to remove this
                file.setProject(projectRepository.getReferenceById(file.getProjectId())); // TODO: Need to remove this
                notification.setActiveStorageFile(file);
            }

        }

        List<Message> messages = messageRepository.findAllByProjectIdOrderByCreatedAtDesc(project.getId());

        for (Message message: messages) {
            message.setContentToDisplay(html2text(message.getContent()));
        }
        model.addAttribute("project", project);
        model.addAttribute("messages", messages);
        model.addAttribute("notifications", notifications);

        return "hey/index";
    }


    @RequestMapping(method = RequestMethod.GET, value="/hasUnreadNotifications",
            produces = "application/json")
    @ResponseBody
    public boolean hasUnreadNotifications (HttpSession httpSession) {
        // TODO: Make sure the user has the access

        User currentUser = (User) httpSession.getAttribute(Constants.CURRENT_USER);

        if(currentUser != null) {
            List<Notification> notifications = notificationRepository.findAllByForUserAndReadIsFalse(currentUser);

            System.out.println("Notifications size: " + notifications.size());

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
