package com.chaihq.webapp.controllers;

import com.chaihq.webapp.models.*;
import com.chaihq.webapp.repositories.*;
import com.chaihq.webapp.storage.StorageService;
import com.chaihq.webapp.utilities.Constants;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang.StringEscapeUtils.escapeHtml;


@Controller
public class TodosController {

    private final StorageService storageService;

    @Autowired
    public TodosController(StorageService storageService) {
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
    private TodoRepository todoRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private NotificationRepository notificationRepository;


    @GetMapping("/project/{project_id}/todos")
    public String index(@PathVariable Long project_id, Model model) {
        Project project = projectRepository.getOne(project_id); // TODO make show this belongs to the user

        List<Todo> completedTodos = todoRepository.findAllByProjectAndAndDoneOrderByDueDateAsc(project, true);
        List<Todo> pendingTodos = todoRepository.findAllByProjectAndAndDoneOrderByDueDateAsc(project, false);

        model.addAttribute("project", project);
        model.addAttribute("completedTodos", completedTodos);
        model.addAttribute("pendingTodos", pendingTodos);

        return "todos/index";
    }


    @GetMapping("/project/{project_id}/todo/new")
    public String neew(@ModelAttribute("todo") Todo todo,
                       @ModelAttribute("project") Project project,
                       HttpSession httpSession,
                       @PathVariable Long project_id, Map<String, Object> model) {

        System.out.println("project_id: " + + project_id);

        todo = new Todo();

        todo.setDueDate(Calendar.getInstance());

        User user = (User) httpSession.getAttribute(Constants.CURRENT_USER);
        project = projectRepository.getOne(project_id); // TODO make show this belongs to the user
        System.out.println(project.getName());
        model.put("project", project);
        model.put("todo", todo);
        System.out.println("neew");
        return "todos/new";

    }

    @PostMapping("/project/{project_id}/todo/new")
    public String save(@ModelAttribute("todo") Todo todo,
                       @ModelAttribute("project") Project project,
                       HttpSession httpSession, @PathVariable Long project_id,
                       Map<String, Object> model, RedirectAttributes redirectAttributes) throws Exception {

        project = projectRepository.getOne(project_id);
        User currentUser = (User) httpSession.getAttribute(Constants.CURRENT_USER);
        todo.setCreatedBy(currentUser);
        User assignedToUser = userRepository.getOne(todo.getAssignedToVariable());
        todo.setAssignedTo(assignedToUser);
        Calendar dueDate = Calendar.getInstance();
        dueDate.setTime(todo.getDueDateVariable());
        todo.setDueDate(dueDate);
        todo.setProject(project);
        todo.setCreatedAt(Calendar.getInstance());
        // TODO: Due date
        todoRepository.save(todo);

        // Get the project owner so that it gets this notification
        if(currentUser.getId() != project.getUser().getId()) {
            Notification notification = new Notification();
            notification.setType(Constants.NOTIFICATION_TYPE_TODO);
            notification.setObjectId(todo.getId());
            notification.setMessage(Constants.NOTIFICATION_MESSAGE_NEW_TODO);
            notification.setCreatedAt(Calendar.getInstance());
            notification.setFromUser(currentUser);
            notification.setForUser(project.getUser());
            notificationRepository.save(notification);
        }

        for(int i=0; i<project.getUsers().size(); i++) {
            User forUser = project.getUsers().get(i);
            if(forUser.getId() != currentUser.getId()) {
                Notification notification = new Notification();
                notification.setType(Constants.NOTIFICATION_TYPE_TODO);
                notification.setObjectId(todo.getId());
                notification.setMessage(Constants.NOTIFICATION_MESSAGE_NEW_TODO);
                notification.setCreatedAt(Calendar.getInstance());
                notification.setFromUser(currentUser);
                notification.setForUser(forUser);
                notificationRepository.save(notification);
            }
        }


        redirectAttributes.addFlashAttribute("notice", "Your todo was created!");

        return "redirect:/project/" + project_id + "/todos";

    }

    @GetMapping("/project/{project_id}/todo/{todo_id}")
    public String show( HttpSession httpSession, @PathVariable Long project_id, @PathVariable Long todo_id,
                        Map<String, Object> model, RedirectAttributes redirectAttributes) throws Exception {

        Project project = projectRepository.getOne(project_id);
        model.put("project", project);

        Todo todo = todoRepository.getOne(todo_id);
        model.put("todo", todo);

        // List<Comment> comments = commentRepository.findAllByMessageIdOrderByCreatedAtAsc(message_id);
        List<Comment> comments = todo.getComments();
        for(int i=0; i<comments.size(); i++) {
            Comment comment = comments.get(i);
            comment.setTextToDisplay(escapeHtml(comment.getText()));
        }

        return "todos/show";
    }

    @GetMapping("/project/{project_id}/todo/{todo_id}/edit")
    public String edit(
            HttpSession httpSession, @PathVariable Long project_id,
            @PathVariable Long todo_id, Map<String, Object> model,
            RedirectAttributes redirectAttributes) throws Exception {

        Project project = projectRepository.getOne(project_id);
        Todo todo = todoRepository.getOne(todo_id);
        model.put("todo", todo);
        model.put("project", project);
        return "todos/edit";
    }

    @PostMapping("/project/{project_id}/todo/{todo_id}/edit")
    public String update(@ModelAttribute("todo") Todo todo,
                         @ModelAttribute("project") Project project,
                         HttpSession httpSession, @PathVariable Long project_id, @PathVariable Long todo_id,
                         Map<String, Object> model, RedirectAttributes redirectAttributes) throws Exception {

        // TODO: Check if this belongs to this user.

        Todo todoToUpdate = todoRepository.getOne(todo_id);

        User userToUpdate = userRepository.getOne(todo.getAssignedToVariable());
        Calendar dueDateCalendar = Calendar.getInstance();
        dueDateCalendar.setTime(todo.getDueDateVariable());

        todoToUpdate.setAssignedTo(userToUpdate);
        todoToUpdate.setDueDate(dueDateCalendar);

        todoToUpdate.setDescription(todo.getDescription());
        todoToUpdate.setNotes(todo.getNotes());

        System.out.println(todoToUpdate);

        todoRepository.save(todoToUpdate);

        /* description: Sample todo
        assignedToVariable: 8
        dueDateVariable: 2019-08-22
        notes: <div>This is great!!!!</div> */

        redirectAttributes.addFlashAttribute("notice", "Your todo was updated!");
        project = projectRepository.getOne(project_id);
        model.put("project", project);
        model.put("todo", todoToUpdate);

        return "redirect:/project/" + project_id + "/todo/" + todoToUpdate.getId();

    }

    @GetMapping("/project/{project_id}/todo/{todo_id}/delete")
    public String delete(
            @ModelAttribute("todo") Todo todo,
            @ModelAttribute("project") Project project,
            HttpSession httpSession, @PathVariable Long project_id,
            @PathVariable Long todo_id, Map<String, Object> model,
            RedirectAttributes redirectAttributes) throws Exception {

        project = projectRepository.getOne(project_id);
        todo = todoRepository.getOne(todo_id);
        todoRepository.delete(todo);
        model.put("project", project);
        redirectAttributes.addFlashAttribute("notice", "Your todo was deleted!");
        return "redirect:/project/" + project_id + "/todos";
    }


    @GetMapping("/project/{project_id}/todo/{todo_id}/complete")
    public String complete(
            @ModelAttribute("todo") Todo todo,
            @ModelAttribute("project") Project project,
            HttpSession httpSession, @PathVariable Long project_id,
            @PathVariable Long todo_id, Map<String, Object> model,
            RedirectAttributes redirectAttributes) throws Exception {

        project = projectRepository.getOne(project_id);
        todo = todoRepository.getOne(todo_id);
        todo.setDone(true);
        todoRepository.save(todo);
        model.put("project", project);
        redirectAttributes.addFlashAttribute("notice", "Good job! You completed a to-do!");
        return "redirect:/project/" + project_id + "/todos";
    }

    @PostMapping("/project/{project_id}/todo/{todo_id}")
    public String addComment(@ModelAttribute("comment") Comment comment,
                             HttpSession httpSession, @PathVariable Long project_id, @PathVariable Long todo_id,
                             Map<String, Object> model, RedirectAttributes redirectAttributes) throws Exception {

        Project project = projectRepository.getOne(project_id);

        Todo todo = todoRepository.getOne(todo_id);


        System.out.println("comment.getText(): " + comment.getText());
        // TODO: Make sure the user has the rights to add a comment here.
        comment.setProjectId(project_id);
        User currentUser = (User) httpSession.getAttribute(Constants.CURRENT_USER);
        comment.setUser(currentUser);
        comment.setCreatedAt(Calendar.getInstance());
        comment.setCommentType(Constants.TODO);
        comment.setTodo(todo);
        commentRepository.save(comment);


        // Get the project owner so that it gets this notification
        if(currentUser.getId() != project.getUser().getId()) {
            Notification notification = new Notification();
            notification.setType(Constants.NOTIFICATION_TYPE_TODO_COMMENT);
            notification.setObjectId(comment.getId());
            notification.setMessage(Constants.NOTIFICATION_MESSAGE_NEW_TODO_COMMENT);
            notification.setCreatedAt(Calendar.getInstance());
            notification.setFromUser(currentUser);
            notification.setForUser(project.getUser());
            notificationRepository.save(notification);
        }

        for(int i=0; i<project.getUsers().size(); i++) {
            User forUser = project.getUsers().get(i);
            if(forUser.getId() != currentUser.getId()) {
                Notification notification = new Notification();
                notification.setType(Constants.NOTIFICATION_TYPE_TODO_COMMENT);
                notification.setObjectId(comment.getId());
                notification.setMessage(Constants.NOTIFICATION_MESSAGE_NEW_TODO_COMMENT);
                notification.setCreatedAt(Calendar.getInstance());
                notification.setFromUser(currentUser);
                notification.setForUser(forUser);
                notificationRepository.save(notification);
            }
        }


        // Get all the update comments
        List<Comment> comments = commentRepository.findAllByMessageIdOrderByCreatedAtAsc(todo_id);
        for(int i=0; i<comments.size(); i++) {
            Comment commentTemp = comments.get(i);
            commentTemp.setTextToDisplay(escapeHtml(commentTemp.getText()));
        }

        redirectAttributes.addFlashAttribute("notice", "Your comment has been added!");
        // model.put("notice", "Your comment has been added!");
        model.put("project", project);
        model.put("todo", todo);
        // model.put(Constants.COMMENTS, comments);
        return "redirect:/project/" + project_id + "/todo/" + todo.getId() + "#comment_" + comment.getId();
        // return "messages/show";
    }

    @RequestMapping(method = RequestMethod.DELETE, value="/project/{project_id}/todo/{message_id}/comment/{comment_id}/delete",
            produces = "application/json")
    @ResponseBody
    public Comment deleteComment(@PathVariable("project_id") long projectId, @PathVariable("message_id") long messageId,
                                 @PathVariable("comment_id") long commentId) {
        // TODO: Make sure the user has the access
        Comment commentToDelete = commentRepository.getOne(commentId);
        commentRepository.delete(commentToDelete);
        return commentToDelete;
    }





    public String html2text(String html) {
        return Jsoup.parse(html).text();
    }

}
