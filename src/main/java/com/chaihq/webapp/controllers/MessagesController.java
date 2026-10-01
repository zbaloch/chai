package com.chaihq.webapp.controllers;

import java.util.Set;
import java.util.HashSet;
import com.chaihq.webapp.services.Chats;
import com.chaihq.webapp.utilities.Paths;
import com.chaihq.webapp.models.*;
import com.chaihq.webapp.repositories.*;
import com.chaihq.webapp.services.ProjectAccess;
import com.chaihq.webapp.services.ProjectNotifications;
import com.chaihq.webapp.storage.StorageFileNotFoundException;
import com.chaihq.webapp.storage.StorageService;
import com.chaihq.webapp.utilities.Constants;
import com.chaihq.webapp.validator.CommentValidator;
import com.chaihq.webapp.validator.MessageValidator;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;

import java.security.Principal;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;


@Controller
@RequestMapping(Paths.ACCOUNT)
public class MessagesController {

    private final StorageService storageService;

    @Autowired
    public MessagesController(StorageService storageService) {
        this.storageService = storageService;
    }

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActiveStorageFileRepository activeStorageFileRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private ProjectAccess projectAccess;

    @Autowired
    private ProjectNotifications notifications;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private MessageValidator messageValidator;

    @Autowired
    private CommentValidator commentValidator;

    @GetMapping("/project/{id}/messages")
    public String index(@PathVariable Long id, Model model) {
        Project project = projectAccess.project(id, projectAccess.currentUser());

        List<Message> messages = messageRepository.findAllByProjectIdOrderByCreatedAtDesc(project.getId());
        messages.removeIf(message -> Constants.DELETED.equals(message.getStatus()));
        for (Message message: messages) {
            message.setContentToDisplay(html2text(message.getContent()));
        }
        model.addAttribute("project", project);
        model.addAttribute("messages", messages);

        return "messages/index";
    }

    @GetMapping("/project/{project_id}/message/new")
    public String neew(@PathVariable Long project_id, Model model) {
        model.addAttribute("project", projectAccess.project(project_id, projectAccess.currentUser()));
        model.addAttribute("message", new Message());
        return "messages/new";
    }

    @PostMapping("/project/{project_id}/message/new")
    public String save(@ModelAttribute("message") Message message, BindingResult bindingResult,
                       @PathVariable Long project_id, Model model, RedirectAttributes redirectAttributes) {
        User currentUser = projectAccess.currentUser();
        Project project = projectAccess.project(project_id, currentUser);
        model.addAttribute(Constants.PROJECT, project);

        messageValidator.validate(message, bindingResult);
        if(bindingResult.hasErrors()) {
            return "messages/new";
        }

        message.setUser(currentUser);
        message.setProjectId(project.getId());
        message.setCreatedAt(Calendar.getInstance());
        messageRepository.save(message);

        notifications.notifyProject(project, currentUser, Constants.NOTIFICATION_TYPE_MESSAGE, message.getId(), Constants.NOTIFICATION_MESSAGE_NEW_MESSAGE);

        redirectAttributes.addFlashAttribute("notice", "Your message created!");
        return "redirect:" + Paths.project(project) + "/message/" + message.getId();
    }

    @GetMapping("/project/{project_id}/message/{message_id}")
    public String show(@PathVariable Long project_id, @PathVariable Long message_id, Model model) {
        User currentUser = projectAccess.currentUser();
        Project project = projectAccess.project(project_id, currentUser);
        Message message = projectAccess.message(project, message_id, currentUser);

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("project", project);
        model.addAttribute("message", message);
        model.addAttribute("comment", new Comment());

        for (Comment comment : message.getComments()) {
            comment.setTextToDisplay(escapeHtml4(comment.getText()));
            notifications.markRead(comment.getId(), currentUser);
        }
        notifications.markRead(message.getId(), currentUser);

        return "messages/show";
    }

    @GetMapping("/project/{project_id}/message/{message_id}/edit")
    public String edit(@PathVariable Long project_id, @PathVariable Long message_id, Model model) {
        User currentUser = projectAccess.currentUser();
        Project project = projectAccess.project(project_id, currentUser);
        Message message = projectAccess.message(project, message_id, currentUser);
        projectAccess.requireAuthorOrAdmin(project, message.getUser(), currentUser, "message", message.getId());

        model.addAttribute("message", message);
        model.addAttribute("project", project);
        return "messages/edit";
    }

    @PostMapping("/project/{project_id}/message/{message_id}/edit")
    public String update(@ModelAttribute("message") Message message, BindingResult bindingResult,
                         @PathVariable Long project_id, @PathVariable Long message_id,
                         Model model, RedirectAttributes redirectAttributes) {
        User currentUser = projectAccess.currentUser();
        Project project = projectAccess.project(project_id, currentUser);
        Message messageToUpdate = projectAccess.message(project, message_id, currentUser);
        projectAccess.requireAuthorOrAdmin(project, messageToUpdate.getUser(), currentUser, "message", messageToUpdate.getId());

        model.addAttribute("project", project);
        message.setId(message_id);
        model.addAttribute("message", message);

        messageValidator.validate(message, bindingResult);
        if(bindingResult.hasErrors()) {
            return "messages/edit";
        }

        messageToUpdate.setTitle(message.getTitle());
        messageToUpdate.setContent(message.getContent());
        messageRepository.save(messageToUpdate);

        redirectAttributes.addFlashAttribute("notice", "Your message was updated!");
        return "redirect:" + Paths.project(project) + "/message/" + messageToUpdate.getId();
    }

    @PostMapping("/project/{project_id}/message/{message_id}/delete")
    public String delete(@PathVariable Long project_id, @PathVariable Long message_id,
                         RedirectAttributes redirectAttributes) {
        User currentUser = projectAccess.currentUser();
        Project project = projectAccess.project(project_id, currentUser);
        Message message = projectAccess.message(project, message_id, currentUser);
        projectAccess.requireAuthorOrAdmin(project, message.getUser(), currentUser, "message", message.getId());

        message.setStatus(Constants.DELETED);
        messageRepository.save(message);
        notificationRepository.deleteInBatch(notificationRepository.findAllByObjectId(message.getId()));

        redirectAttributes.addFlashAttribute("notice", "Your message was deleted!");
        return "redirect:" + Paths.project(project) + "/messages";
    }

    @PostMapping("/project/{project_id}/message/{message_id}/comment")
    public String addComment(@ModelAttribute("comment") Comment comment, BindingResult bindingResult,
                             @PathVariable Long project_id, @PathVariable Long message_id,
                             Model model, RedirectAttributes redirectAttributes) {
        User currentUser = projectAccess.currentUser();
        Project project = projectAccess.project(project_id, currentUser);
        Message message = projectAccess.message(project, message_id, currentUser);

        model.addAttribute("project", project);
        model.addAttribute("message", message);
        model.addAttribute("currentUser", currentUser);

        commentValidator.validate(comment, bindingResult);
        if(bindingResult.hasErrors()) {
            return "messages/show";
        }

        comment.setProjectId(project.getId());
        comment.setUser(currentUser);
        comment.setCreatedAt(Calendar.getInstance());
        comment.setCommentType(Constants.MESSAGE);
        comment.setMessage(message);
        Set<Long> mentioned = ProjectNotifications.mentionedIn(comment.getText(), project);
        comment.setMentions(Chats.csv(mentioned));
        commentRepository.save(comment);

        notifications.notifyProject(project, currentUser, Constants.NOTIFICATION_TYPE_MESSAGE_COMMENT, comment.getId(),
                Constants.NOTIFICATION_MESSAGE_NEW_MESSAGE_COMMENT, mentioned, Constants.NOTIFICATION_MESSAGE_MENTION_MESSAGE_COMMENT);

        redirectAttributes.addFlashAttribute("notice", "Your comment has been added!");
        return "redirect:" + Paths.project(project) + "/message/" + message.getId() + "#comment_" + comment.getId();
    }

    // Only the author can change what they wrote. Anyone newly @mentioned hears about it.
    @PostMapping("/project/{project_id}/message/{message_id}/comment/{comment_id}/edit")
    public String editComment(@PathVariable long project_id, @PathVariable long message_id, @PathVariable long comment_id,
                              @RequestParam(value = "text", required = false) String text, RedirectAttributes redirectAttributes) {
        User currentUser = projectAccess.currentUser();
        Project project = projectAccess.project(project_id, currentUser);
        Message message = projectAccess.message(project, message_id, currentUser);
        Comment comment = projectAccess.comment(project, comment_id, currentUser);
        if (comment.getMessage() == null || comment.getMessage().getId() != message.getId()
                || comment.getUser() == null || comment.getUser().getId() != currentUser.getId()) {
            throw projectAccess.denied(currentUser, "comment", comment_id);
        }
        String back = "redirect:" + Paths.project(project) + "/message/" + message.getId() + "#comment_" + comment.getId();
        if (text == null || (Jsoup.parse(text).text().isBlank() && !text.contains("<action-text-attachment"))) {
            redirectAttributes.addFlashAttribute("destruction_notice", "A comment can’t be empty.");
            return back;
        }

        Set<Long> before = new HashSet<>(Chats.ids(comment.getMentions()));
        Set<Long> after = ProjectNotifications.mentionedIn(text, project);
        comment.setText(text);
        comment.setMentions(Chats.csv(after));
        comment.setEditedAt(Calendar.getInstance());
        commentRepository.save(comment);
        notifications.notifyNewlyMentioned(project, currentUser, Constants.NOTIFICATION_TYPE_MESSAGE_COMMENT, comment.getId(),
                Constants.NOTIFICATION_MESSAGE_MENTION_MESSAGE_COMMENT, before, after);

        redirectAttributes.addFlashAttribute("notice", "Your comment has been updated!");
        return back;
    }

    @RequestMapping(method = RequestMethod.DELETE, value="/project/{project_id}/message/{message_id}/comment/{comment_id}/delete",
            produces = "application/json")
    @ResponseBody
    public Map<String, Object> deleteComment(@PathVariable("project_id") long projectId, @PathVariable("message_id") long messageId,
                                             @PathVariable("comment_id") long commentId) {
        User currentUser = projectAccess.currentUser();
        Project project = projectAccess.project(projectId, currentUser);
        Message message = projectAccess.message(project, messageId, currentUser);
        Comment commentToDelete = projectAccess.comment(project, commentId, currentUser);
        if (commentToDelete.getMessage() == null || commentToDelete.getMessage().getId() != message.getId()) {
            throw projectAccess.denied(currentUser, "comment", commentId);
        }
        projectAccess.requireAuthorOrAdmin(project, commentToDelete.getUser(), currentUser, "comment", commentId);

        commentRepository.delete(commentToDelete);
        notificationRepository.deleteInBatch(notificationRepository.findAllByObjectId(commentToDelete.getId()));

        // Only the id: returning the entity would serialise its author, login token included
        return Map.of("id", commentToDelete.getId());
    }

    @InitBinder("message")
    public void messageFields(WebDataBinder binder) {
        binder.setAllowedFields("title", "content");
    }

    @InitBinder("comment")
    public void commentFields(WebDataBinder binder) {
        binder.setAllowedFields("text");
    }

    public String html2text(String html) {
        return Jsoup.parse(html).text();
    }

}
