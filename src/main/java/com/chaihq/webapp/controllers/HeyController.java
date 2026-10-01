package com.chaihq.webapp.controllers;

import com.chaihq.webapp.models.*;
import com.chaihq.webapp.repositories.*;
import com.chaihq.webapp.services.Accounts;
import com.chaihq.webapp.services.Chats;
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

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private Chats chats;

    @Autowired
    private Accounts accounts;


    // Notifications belong to an account: you see the ones from the account you're working in
    @GetMapping("/hey")
    public String index(Model model, HttpSession session) {
        User user = projectAccess.currentUser();
        Account account = accounts.current(session, user);

        List<Notification> visible = new ArrayList<>();
        if (account != null) {
            for (Notification notification : notificationRepository.findAllByForUserOrderByCreatedAtDesc(user)) {
                if (attach(notification, user, account)) {
                    visible.add(notification);
                }
            }
        }

        model.addAttribute("notifications", visible);
        return "hey/index";
    }

    // Loads what a notification points at. Returns false (and the notification is hidden) when it
    // no longer exists, is in a project the user isn't part of any more, or is in another account.
    private boolean attach(Notification notification, User user, Account account) {
        String type = notification.getType();
        long objectId = notification.getObjectId();

        if (Constants.NOTIFICATION_TYPE_MESSAGE.equals(type)) {
            Message message = messageRepository.findById(objectId).orElse(null);
            Project project = message == null ? null : projectRepository.findById(message.getProjectId()).orElse(null);
            if (!inAccount(project, user, account) || Constants.DELETED.equals(message.getStatus())) return false;
            message.setContentToDisplay(html2text(message.getContent()));
            message.setProject(project);
            notification.setMessageObject(message);
        } else if (Constants.NOTIFICATION_TYPE_TODO.equals(type)) {
            Todo todo = todoRepository.findById(objectId).orElse(null);
            if (todo == null || !inAccount(todo.getProject(), user, account)) return false;
            notification.setTodo(todo);
        } else if (Constants.NOTIFICATION_TYPE_MESSAGE_COMMENT.equals(type) || Constants.NOTIFICATION_TYPE_TODO_COMMENT.equals(type)) {
            Comment comment = commentRepository.findById(objectId).orElse(null);
            Project project = comment == null ? null : projectRepository.findById(comment.getProjectId()).orElse(null);
            if (!inAccount(project, user, account)) return false;
            comment.setProject(project);
            comment.setTextToDisplay(html2text(comment.getText()));
            notification.setComment(comment);
        } else if (Constants.NOTIFICATION_TYPE_FILE.equals(type)) {
            ActiveStorageFile file = activeStorageFileRepository.findById(objectId).orElse(null);
            Project project = file == null || file.getProjectId() == null ? null : projectRepository.findById(file.getProjectId()).orElse(null);
            if (!inAccount(project, user, account)) return false;
            file.setProject(project);
            notification.setActiveStorageFile(file);
        } else if (Constants.NOTIFICATION_TYPE_CHAT_MENTION.equals(type)) {
            Chat chat = chatRepository.findById(objectId).orElse(null);
            ChatRoom room = chat == null || chat.getRoomId() == null ? null : chatRoomRepository.findById(chat.getRoomId()).orElse(null);
            if (!chats.canAccess(room, user) || room.getAccount() == null || room.getAccount().getId() != account.getId()) return false;
            notification.setChatTitle(room.isDirect() ? "Chat with " + chats.title(room, user) : "Chat in " + room.getProject().getName());
            notification.setChatUrl(chats.url(room) + "#chat_message_" + chat.getId());
            notification.setChatText(chat.getMessage());
        } else {
            return false;
        }
        return true;
    }

    private boolean inAccount(Project project, User user, Account account) {
        return projectAccess.isMember(project, user) && project.getAccount().getId() == account.getId();
    }

    // The bell's dot: something unread in the account you're working in
    @RequestMapping(method = RequestMethod.GET, value="/hasUnreadNotifications",
            produces = "application/json")
    @ResponseBody
    public boolean hasUnreadNotifications(HttpSession httpSession) {
        User user = projectAccess.currentUser();
        Account account = accounts.current(httpSession, user);
        if (account == null) {
            return false;
        }
        return notificationRepository.findAllByForUserAndReadIsFalse(user).stream()
                .anyMatch(notification -> attach(notification, user, account));
    }

    public String html2text(String html) {
        return Jsoup.parse(html).text();
    }

}
