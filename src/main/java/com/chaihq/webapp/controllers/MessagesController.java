package com.chaihq.webapp.controllers;

import com.chaihq.webapp.models.*;
import com.chaihq.webapp.repositories.*;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;

import java.security.Principal;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;


@Controller
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
    private NotificationRepository notificationRepository;

    @Autowired
    private MessageValidator messageValidator;

    @Autowired
    private CommentValidator commentValidator;

    @GetMapping("/project/{id}/messages")
    public String index(@PathVariable Long id, Model model) {
        Project project = projectRepository.getReferenceById(id); // TODO make show this belongs to the user

        List<Message> messages = messageRepository.findAllByProjectIdOrderByCreatedAtDesc(id);

        for (Message message: messages) {
            message.setContentToDisplay(html2text(message.getContent()));
        }
        model.addAttribute("project", project);
        model.addAttribute("messages", messages);

        return "messages/index";
    }


    @GetMapping("/project/{project_id}/message/new")
    public String neew(@ModelAttribute("message") Message message,
                       @ModelAttribute("project") Project project,
                       HttpSession httpSession,
                       @PathVariable Long project_id, Map<String, Object> model) {

        System.out.println("project_id: " + + project_id);

        message = new Message();

        User user = (User) httpSession.getAttribute(Constants.CURRENT_USER);
        project = projectRepository.getReferenceById(project_id); // TODO make show this belongs to the user
        System.out.println(project.getName());
        model.put("project", project);
        System.out.println("neew");
        return "messages/new";

    }

    @PostMapping("/project/{project_id}/message/new")
    public String save(@ModelAttribute("message") Message message,
                       HttpSession httpSession, @PathVariable Long project_id,
                       Map<String, Object> model, RedirectAttributes redirectAttributes,
                       BindingResult bindingResult) throws Exception {

        messageValidator.validate(message, bindingResult);

        System.out.println("Message content: " + message.getContent());

        Project project = projectRepository.getReferenceById(project_id);
        model.put(Constants.PROJECT, project);

        if(bindingResult.hasErrors()) {
            return "messages/new";
        }

        User currentUser = (User) httpSession.getAttribute(Constants.CURRENT_USER);
        message.setUser(currentUser);
        message.setProjectId(project_id);
        message.setCreatedAt(Calendar.getInstance());
        messageRepository.save(message);



        if(currentUser.getId() != project.getUser().getId()) {
            Notification notification = new Notification();
            notification.setType(Constants.NOTIFICATION_TYPE_MESSAGE);
            notification.setObjectId(message.getId());
            notification.setMessage(Constants.NOTIFICATION_MESSAGE_NEW_MESSAGE);
            notification.setCreatedAt(Calendar.getInstance());
            notification.setFromUser(currentUser);
            notification.setForUser(project.getUser());
            notificationRepository.save(notification);
        }

        for(int i=0; i<project.getUsers().size(); i++) {
            User forUser = project.getUsers().get(i);
            if (forUser.getId() != currentUser.getId()) {
                Notification notification = new Notification();
                notification.setType(Constants.NOTIFICATION_TYPE_MESSAGE);
                notification.setObjectId(message.getId());
                notification.setMessage(Constants.NOTIFICATION_MESSAGE_NEW_MESSAGE);
                notification.setCreatedAt(Calendar.getInstance());
                notification.setFromUser(currentUser);
                notification.setForUser(forUser);
                notificationRepository.save(notification);
            }

        }

        redirectAttributes.addFlashAttribute("notice", "Your message created!");

        System.out.println("project: " + project_id);
        System.out.println("message.getContent(): " + message.getContent());
        return "redirect:/project/" + project_id + "/message/" + message.getId();

    }

    @GetMapping("/project/{project_id}/message/{message_id}")
    public String show( Principal principal, @PathVariable Long project_id, @PathVariable Long message_id,
                       Model model, RedirectAttributes redirectAttributes) throws Exception {

        User currentUser = userRepository.findByEmail(principal.getName());

        model.addAttribute("currentUser", currentUser);

        Project project = projectRepository.getReferenceById(project_id);
        model.addAttribute("project", project);

        Message message = messageRepository.getReferenceById(message_id);
        model.addAttribute("message", message);

        model.addAttribute("comment", new Comment());

        // List<Comment> comments = commentRepository.findAllByMessageIdOrderByCreatedAtAsc(message_id);
        List<Comment> comments = message.getComments();
        for(int i=0; i<comments.size(); i++) {
            Comment comment = comments.get(i);
            comment.setTextToDisplay(escapeHtml4(comment.getText()));

            List<Notification> notifications = notificationRepository.findAllByObjectIdAndForUser(comment.getId(), currentUser);
            for (Notification notification: notifications
            ) {
                notification.setRead(true);
                notification.setReadAt(Calendar.getInstance());
                notificationRepository.save(notification);
            }
        }
        // model.put(Constants.COMMENTS, comments);

        List<Notification> notifications = notificationRepository.findAllByObjectIdAndForUser(message.getId(), currentUser);
        for (Notification notification: notifications
        ) {
            notification.setRead(true);
            notification.setReadAt(Calendar.getInstance());
            notificationRepository.save(notification);
        }

        System.out.println("project: " + project_id);
        System.out.println("message.getContent(): " + message.getContent());
        return "messages/show";
    }

    @GetMapping("/project/{project_id}/message/{message_id}/edit")
    public String edit(
                    HttpSession httpSession, @PathVariable Long project_id,
                    @PathVariable Long message_id, Map<String, Object> model,
                    RedirectAttributes redirectAttributes) throws Exception {

        Project project = projectRepository.getReferenceById(project_id);
        Message message = messageRepository.getReferenceById(message_id);
        message.setContentToDisplay(escapeHtml4(message.getContent()));
        model.put("message", message);
        model.put("project", project);
        System.out.println("edit: " + message_id);
        return "messages/edit";
    }

    @PostMapping("/project/{project_id}/message/{message_id}/edit")
    public String update(@ModelAttribute("message") Message message,
                       HttpSession httpSession, @PathVariable Long project_id, @PathVariable Long message_id,
                       Map<String, Object> model, RedirectAttributes redirectAttributes,
                         BindingResult bindingResult) throws Exception {

        // TODO: Check if this belongs to this user.



        Project project = projectRepository.getReferenceById(project_id);
        Message messageToUpdate = messageRepository.getReferenceById(message_id);

        model.put("project", project);

        message.setId(message_id);
        message.setContentToDisplay(escapeHtml4(message.getContent()));

        model.put("message", message);

        messageValidator.validate(message, bindingResult);

        if(bindingResult.hasErrors()) {
            return "messages/edit";
        }


        messageToUpdate.setTitle(message.getTitle());
        messageToUpdate.setContent(message.getContent()); // TODO:Probably need last updated?

        messageRepository.save(messageToUpdate);

        redirectAttributes.addFlashAttribute("notice", "Your message was updated!");
        model.put("message", messageToUpdate);

        return "redirect:/project/" + project_id + "/message/" + messageToUpdate.getId();

    }

    @GetMapping("/project/{project_id}/message/{message_id}/delete")
    public String delete(
            @ModelAttribute("message") Message message, @ModelAttribute("project") Project project,
            HttpSession httpSession, @PathVariable Long project_id,
            @PathVariable Long message_id, Map<String, Object> model,
            RedirectAttributes redirectAttributes) throws Exception {

        project = projectRepository.getReferenceById(project_id);
        message = messageRepository.getReferenceById(message_id);
        message.setStatus(Constants.DELETED);
        messageRepository.save(message);

        List<Notification> notificationsToDelete = notificationRepository.findAllByObjectId(message.getId());
        notificationRepository.deleteInBatch(notificationsToDelete);

        model.put("project", project);
        redirectAttributes.addFlashAttribute("notice", "Your message was deleted!");
        return "redirect:/project/" + project_id + "/messages";
    }

    @PostMapping("/project/{project_id}/message/{message_id}/comment")
    public String addComment(@ModelAttribute("comment") Comment comment, 
            Principal principal, @PathVariable Long project_id, @PathVariable Long message_id,
                             Model model, RedirectAttributes redirectAttributes,
                             BindingResult bindingResult) throws Exception {



        User currentUser = userRepository.findByEmail(principal.getName());
        Project project = projectRepository.getReferenceById(project_id);

        Message message = messageRepository.getReferenceById(message_id);

        model.addAttribute("project", project);
        model.addAttribute("message", message);
        model.addAttribute("currentUser", currentUser);

        commentValidator.validate(comment, bindingResult);
        if(bindingResult.hasErrors()) {
            return   "messages/show";
        }


        System.out.println("comment.getText(): " + comment.getText());
        // TODO: Make sure the user has the rights to add a comment here.
        comment.setProjectId(project_id);
        
        comment.setUser(currentUser);
        comment.setCreatedAt(Calendar.getInstance());
        comment.setCommentType(Constants.MESSAGE);
        comment.setMessage(message);
        commentRepository.save(comment);

        if(currentUser.getId() != project.getUser().getId()) {
            Notification notification = new Notification();
            notification.setType(Constants.NOTIFICATION_TYPE_MESSAGE_COMMENT);
            notification.setObjectId(comment.getId());
            notification.setMessage(Constants.NOTIFICATION_MESSAGE_NEW_MESSAGE_COMMENT);
            notification.setCreatedAt(Calendar.getInstance());
            notification.setFromUser(currentUser);
            notification.setForUser(project.getUser());
            notificationRepository.save(notification);
        }


        for(int i=0; i<project.getUsers().size(); i++) {
            User forUser = project.getUsers().get(i);
            if(forUser.getId() != currentUser.getId()) {

                Notification notification = new Notification();
                notification.setType(Constants.NOTIFICATION_TYPE_MESSAGE_COMMENT);
                notification.setObjectId(comment.getId());
                notification.setMessage(Constants.NOTIFICATION_MESSAGE_NEW_MESSAGE_COMMENT);
                notification.setCreatedAt(Calendar.getInstance());
                notification.setFromUser(currentUser);
                notification.setForUser(forUser);
                notificationRepository.save(notification);

            }
        }

        // Get all the update comments
        List<Comment> comments = commentRepository.findAllByMessageIdOrderByCreatedAtAsc(message_id);
        for(int i=0; i<comments.size(); i++) {
            Comment commentTemp = comments.get(i);
            commentTemp.setTextToDisplay(escapeHtml4(commentTemp.getText()));
        }

        redirectAttributes.addFlashAttribute("notice", "Your comment has been added!");
        // model.put("notice", "Your comment has been added!");
        model.addAttribute("project", project);
        model.addAttribute("message", message);
        // model.put(Constants.COMMENTS, comments);
        return "redirect:/project/" + project_id + "/message/" + message.getId() + "#comment_" + comment.getId();
        // return "messages/show";
    }

    // http://localhost:8080/chaiweb/project/1/message/16/comment/41/delete

    /* @PostMapping("/project/{project_id}/message/{message_id}/comment/{comment_id}/delete")
    public String deleteComment(HttpSession httpSession, @PathVariable Long project_id, @PathVariable Long message_id,
                                @PathVariable Long comment_id,
                             Map<String, Object> model, RedirectAttributes redirectAttributes) throws Exception {

        commentRepository.deleteById(comment_id);

        Project project = projectRepository.getReferenceById(project_id);
        Message message = messageRepository.getReferenceById(message_id);

        List<Comment> comments = message.getComments();
        for(int i=0; i<comments.size(); i++) {
            Comment commentTemp = comments.get(i);
            commentTemp.setTextToDisplay(escapeHtml4(commentTemp.getText()));
        }

        redirectAttributes.addFlashAttribute("notice", "Your comment has been deleted!");
        model.put(Constants.PROJECT, project);
        model.put(Constants.MESSAGE, message);
        return "redirect:/project/" + project_id + "/message/" + message.getId() + "#comment_form";
    } */


    @RequestMapping(method = RequestMethod.DELETE, value="/project/{project_id}/message/{message_id}/comment/{comment_id}/delete",
            produces = "application/json")
    @ResponseBody
    public Comment deleteComment(@PathVariable("project_id") long projectId, @PathVariable("message_id") long messageId,
                                  @PathVariable("comment_id") long commentId) {
        // TODO: Make sure the user has the access
        Comment commentToDelete = commentRepository.getReferenceById(commentId);
        commentRepository.delete(commentToDelete);

        List<Notification> notificationsToDelete = notificationRepository.findAllByObjectId(commentToDelete.getId());
        notificationRepository.deleteInBatch(notificationsToDelete);

        return commentToDelete;
    }





    public String html2text(String html) {
        return Jsoup.parse(html).text();
    }

}
