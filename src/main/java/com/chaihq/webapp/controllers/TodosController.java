package com.chaihq.webapp.controllers;

import java.util.Set;
import java.util.HashSet;
import com.chaihq.webapp.services.Chats;
import com.chaihq.webapp.utilities.Paths;
import com.chaihq.webapp.models.*;
import com.chaihq.webapp.repositories.*;
import com.chaihq.webapp.services.ProjectAccess;
import com.chaihq.webapp.services.ProjectNotifications;
import com.chaihq.webapp.storage.StorageService;
import com.chaihq.webapp.utilities.Constants;
import com.chaihq.webapp.validator.CommentValidator;
import com.chaihq.webapp.validator.TodoValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import java.security.Principal;

import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;


@Controller
@RequestMapping(Paths.ACCOUNT)
public class TodosController {

    private static final Logger logger = LoggerFactory.getLogger(TodosController.class);

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

    @Autowired
    private TodoValidator todoValidator;

    @Autowired
    private CommentValidator commentValidator;

    @Autowired
    private ProjectAccess projectAccess;

    @Autowired
    private ProjectNotifications notifications;


    @GetMapping("/project/{project_id}/todos")
    public String index(@PathVariable Long project_id, Model model) {
        Project project = projectAccess.project(project_id, projectAccess.currentUser());

        model.addAttribute("project", project);
        model.addAttribute("completedTodos", todoRepository.findAllByProjectAndDoneOrderByPositionAscDueDateAsc(project, true));
        model.addAttribute("pendingTodos", todoRepository.findAllByProjectAndDoneOrderByPositionAscDueDateAsc(project, false));
        return "todos/index";
    }

    @PostMapping("/project/{project_id}/todos/reorder")
    @ResponseBody
    public Map<String, Object> reorderTodos(@PathVariable Long project_id, @RequestBody List<Long> todoIds) {
        Project project = projectAccess.project(project_id, projectAccess.currentUser());
        List<Todo> pendingTodos = todoRepository.findAllByProjectAndDoneOrderByPositionAscDueDateAsc(project, false);

        // Only this project's to-dos can be reordered; unknown ids are ignored
        Map<Long, Todo> todosById = new HashMap<>();
        for (Todo todo : pendingTodos) {
            todosById.put(todo.getId(), todo);
        }
        for (int i = 0; i < todoIds.size(); i++) {
            Todo todo = todosById.get(todoIds.get(i));
            if (todo != null) {
                todo.setPosition(i);
            }
        }

        todoRepository.saveAll(pendingTodos);
        return Map.of("success", true);
    }

    @GetMapping("/project/{project_id}/todo/new")
    public String neew(@PathVariable Long project_id, Model model) {
        model.addAttribute("project", projectAccess.project(project_id, projectAccess.currentUser()));
        model.addAttribute("todo", new Todo());
        model.addAttribute("dueOn", NO_DUE_DATE);
        return "todos/new";
    }

    @PostMapping("/project/{project_id}/todo/new")
    public String save(@ModelAttribute("todo") Todo todo, BindingResult bindingResult,
                       @RequestParam(value = "dueOn", required = false) String dueOn,
                       @PathVariable Long project_id, Model model, RedirectAttributes redirectAttributes) {
        User currentUser = projectAccess.currentUser();
        Project project = projectAccess.project(project_id, currentUser);
        model.addAttribute(Constants.PROJECT, project);

        todoValidator.validate(todo, bindingResult);
        User assignee = memberOrNull(project, todo.getAssignedToVariable());
        if (assignee == null) {
            bindingResult.rejectValue("assignedToVariable", "required.field");
        }
        Calendar dueDate = dueDate(todo, dueOn, bindingResult);
        if(bindingResult.hasErrors()) {
            model.addAttribute("dueOn", dueOn);
            return "todos/new";
        }

        todo.setCreatedBy(currentUser);
        todo.setAssignedTo(assignee);
        todo.setDueDate(dueDate);
        todo.setProject(project);
        todo.setCreatedAt(Calendar.getInstance());
        // Set position to end of list
        todo.setPosition(todoRepository.countByProjectAndDone(project, false).intValue());
        todoRepository.save(todo);

        notifications.notifyProject(project, currentUser, Constants.NOTIFICATION_TYPE_TODO, todo.getId(), Constants.NOTIFICATION_MESSAGE_NEW_TODO);

        redirectAttributes.addFlashAttribute("notice", "Your todo was created!");
        return "redirect:" + Paths.project(project) + "/todos";
    }

    @GetMapping("/project/{project_id}/todo/{todo_id}")
    public String show(@PathVariable Long project_id, @PathVariable Long todo_id, Model model) {
        User currentUser = projectAccess.currentUser();
        Project project = projectAccess.project(project_id, currentUser);
        Todo todo = projectAccess.todo(project, todo_id, currentUser);

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("project", project);
        model.addAttribute("todo", todo);
        model.addAttribute("canChange", canChange(project, todo, currentUser));

        for (Comment comment : todo.getComments()) {
            comment.setTextToDisplay(escapeHtml4(comment.getText()));
            notifications.markRead(comment.getId(), currentUser);
        }
        notifications.markRead(todo.getId(), currentUser);

        return "todos/show";
    }

    @GetMapping("/project/{project_id}/todo/{todo_id}/edit")
    public String edit(@PathVariable Long project_id, @PathVariable Long todo_id, Model model) {
        User currentUser = projectAccess.currentUser();
        Project project = projectAccess.project(project_id, currentUser);
        Todo todo = projectAccess.todo(project, todo_id, currentUser);
        requireCanChange(project, todo, currentUser);

        model.addAttribute("todo", todo);
        model.addAttribute("project", project);
        if (todo.getDueDate() != null) {
            todo.setDueDateVariable(todo.getDueDate().getTime());
        }
        model.addAttribute("dueOn", todo.getDueDate() == null ? NO_DUE_DATE : SPECIFIC_DAY);
        return "todos/edit";
    }

    @PostMapping("/project/{project_id}/todo/{todo_id}/edit")
    public String update(@ModelAttribute("todo") Todo todo, BindingResult bindingResult,
                         @RequestParam(value = "dueOn", required = false) String dueOn,
                         @PathVariable Long project_id, @PathVariable Long todo_id,
                         Model model, RedirectAttributes redirectAttributes) {
        User currentUser = projectAccess.currentUser();
        Project project = projectAccess.project(project_id, currentUser);
        Todo todoToUpdate = projectAccess.todo(project, todo_id, currentUser);
        requireCanChange(project, todoToUpdate, currentUser);

        todo.setId(todo_id);
        model.addAttribute("project", project);
        model.addAttribute("todo", todo);

        todoValidator.validate(todo, bindingResult);
        User assignee = memberOrNull(project, todo.getAssignedToVariable());
        if (assignee == null) {
            bindingResult.rejectValue("assignedToVariable", "required.field");
        }
        Calendar dueDate = dueDate(todo, dueOn, bindingResult);
        if(bindingResult.hasErrors()) {
            model.addAttribute("dueOn", dueOn);
            return "todos/edit";
        }

        todoToUpdate.setAssignedTo(assignee);
        todoToUpdate.setDueDate(dueDate);
        todoToUpdate.setDescription(todo.getDescription());
        todoToUpdate.setNotes(todo.getNotes());
        todoRepository.save(todoToUpdate);

        redirectAttributes.addFlashAttribute("notice", "Your todo was updated!");
        return "redirect:" + Paths.project(project) + "/todo/" + todoToUpdate.getId();
    }

    @PostMapping("/project/{project_id}/todo/{todo_id}/delete")
    public String delete(@PathVariable Long project_id, @PathVariable Long todo_id, RedirectAttributes redirectAttributes) {
        User currentUser = projectAccess.currentUser();
        Project project = projectAccess.project(project_id, currentUser);
        Todo todo = projectAccess.todo(project, todo_id, currentUser);
        projectAccess.requireAuthorOrAdmin(project, todo.getCreatedBy(), currentUser, "todo", todo.getId());

        todoRepository.delete(todo);
        notificationRepository.deleteInBatch(notificationRepository.findAllByObjectId(todo.getId()));

        redirectAttributes.addFlashAttribute("notice", "Your todo was deleted!");
        return "redirect:" + Paths.project(project) + "/todos";
    }

    @PostMapping("/project/{project_id}/todo/{todo_id}/complete")
    public String complete(@PathVariable Long project_id, @PathVariable Long todo_id, RedirectAttributes redirectAttributes) {
        User currentUser = projectAccess.currentUser();
        Project project = projectAccess.project(project_id, currentUser);
        Todo todo = projectAccess.todo(project, todo_id, currentUser);
        requireCanChange(project, todo, currentUser);

        todo.setDone(true);
        todoRepository.save(todo);
        redirectAttributes.addFlashAttribute("notice", "Good job! You completed a to-do!");
        return "redirect:" + Paths.project(project) + "/todos";
    }

    @PostMapping("/project/{project_id}/todo/{todo_id}/comment")
    public String addComment(@ModelAttribute("comment") Comment comment, BindingResult bindingResult,
                             @PathVariable Long project_id, @PathVariable Long todo_id,
                             Model model, RedirectAttributes redirectAttributes) {
        User currentUser = projectAccess.currentUser();
        Project project = projectAccess.project(project_id, currentUser);
        Todo todo = projectAccess.todo(project, todo_id, currentUser);

        model.addAttribute("project", project);
        model.addAttribute("todo", todo);
        model.addAttribute("currentUser", currentUser);

        commentValidator.validate(comment, bindingResult);
        if(bindingResult.hasErrors()) {
            return "todos/show";
        }

        comment.setProjectId(project.getId());
        comment.setUser(currentUser);
        comment.setCreatedAt(Calendar.getInstance());
        comment.setCommentType(Constants.TODO);
        comment.setTodo(todo);
        Set<Long> mentioned = ProjectNotifications.mentionedIn(comment.getText(), project);
        comment.setMentions(Chats.csv(mentioned));
        commentRepository.save(comment);

        notifications.notifyProject(project, currentUser, Constants.NOTIFICATION_TYPE_TODO_COMMENT, comment.getId(),
                Constants.NOTIFICATION_MESSAGE_NEW_TODO_COMMENT, mentioned, Constants.NOTIFICATION_MESSAGE_MENTION_TODO_COMMENT);

        redirectAttributes.addFlashAttribute("notice", "Your comment has been added!");
        return "redirect:" + Paths.project(project) + "/todo/" + todo.getId() + "#comment_" + comment.getId();
    }

    // Only the author can change what they wrote. Anyone newly @mentioned hears about it.
    @PostMapping("/project/{project_id}/todo/{todo_id}/comment/{comment_id}/edit")
    public String editComment(@PathVariable long project_id, @PathVariable long todo_id, @PathVariable long comment_id,
                              @RequestParam(value = "text", required = false) String text, RedirectAttributes redirectAttributes) {
        User currentUser = projectAccess.currentUser();
        Project project = projectAccess.project(project_id, currentUser);
        Todo todo = projectAccess.todo(project, todo_id, currentUser);
        Comment comment = projectAccess.comment(project, comment_id, currentUser);
        if (comment.getTodo() == null || comment.getTodo().getId() != todo.getId()
                || comment.getUser() == null || comment.getUser().getId() != currentUser.getId()) {
            throw projectAccess.denied(currentUser, "comment", comment_id);
        }
        String back = "redirect:" + Paths.project(project) + "/todo/" + todo.getId() + "#comment_" + comment.getId();
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
        notifications.notifyNewlyMentioned(project, currentUser, Constants.NOTIFICATION_TYPE_TODO_COMMENT, comment.getId(),
                Constants.NOTIFICATION_MESSAGE_MENTION_TODO_COMMENT, before, after);

        redirectAttributes.addFlashAttribute("notice", "Your comment has been updated!");
        return back;
    }

    @RequestMapping(method = RequestMethod.DELETE, value="/project/{project_id}/todo/{todo_id}/comment/{comment_id}/delete",
            produces = "application/json")
    @ResponseBody
    public Map<String, Object> deleteComment(@PathVariable("project_id") long projectId, @PathVariable("todo_id") long todoId,
                                             @PathVariable("comment_id") long commentId) {
        Comment comment = deletableComment(projectId, todoId, commentId);
        deleteWithNotifications(comment);
        // Only the id: returning the entity would serialise its author, login token included
        return Map.of("id", comment.getId());
    }

    @PostMapping("/project/{project_id}/todo/{todo_id}/comment/{comment_id}/delete")
    public String deleteCommentFromForm(@PathVariable("project_id") long projectId,
                                        @PathVariable("todo_id") long todoId,
                                        @PathVariable("comment_id") long commentId,
                                        RedirectAttributes redirectAttributes) {
        Comment comment = deletableComment(projectId, todoId, commentId);
        deleteWithNotifications(comment);
        redirectAttributes.addFlashAttribute("notice", "Your comment has been deleted!");
        return "redirect:" + Paths.project(comment.getTodo().getProject()) + "/todo/" + todoId + "#comment_form";
    }

    private Comment deletableComment(long projectId, long todoId, long commentId) {
        User currentUser = projectAccess.currentUser();
        Project project = projectAccess.project(projectId, currentUser);
        Todo todo = projectAccess.todo(project, todoId, currentUser);
        Comment comment = projectAccess.comment(project, commentId, currentUser);
        if (comment.getTodo() == null || comment.getTodo().getId() != todo.getId()) {
            throw projectAccess.denied(currentUser, "comment", commentId);
        }
        projectAccess.requireAuthorOrAdmin(project, comment.getUser(), currentUser, "comment", commentId);
        return comment;
    }

    private void deleteWithNotifications(Comment comment) {
        commentRepository.delete(comment);
        notificationRepository.deleteInBatch(notificationRepository.findAllByObjectId(comment.getId()));
    }

    // The creator, the assignee and account owners/admins may edit or complete a to-do
    private boolean canChange(Project project, Todo todo, User user) {
        boolean isCreator = todo.getCreatedBy() != null && todo.getCreatedBy().getId() == user.getId();
        boolean isAssignee = todo.getAssignedTo() != null && todo.getAssignedTo().getId() == user.getId();
        return isCreator || isAssignee || projectAccess.canManage(project, user);
    }

    private void requireCanChange(Project project, Todo todo, User user) {
        if (!canChange(project, todo, user)) {
            throw projectAccess.denied(user, "todo", todo.getId());
        }
    }

    // Due dates are optional: "No due date", or "A specific day" which then needs a date
    private static final String NO_DUE_DATE = "none", SPECIFIC_DAY = "date";

    private Calendar dueDate(Todo todo, String dueOn, BindingResult bindingResult) {
        if (!SPECIFIC_DAY.equals(dueOn)) {
            todo.setDueDateVariable(null);
            return null;
        }
        if (todo.getDueDateVariable() == null) {
            bindingResult.rejectValue("dueDateVariable", "todo.dueDate.missing", "Pick a date, or choose No due date.");
            return null;
        }
        Calendar dueDate = Calendar.getInstance();
        dueDate.setTime(todo.getDueDateVariable());
        return dueDate;
    }

    // To-dos can only be assigned to people in the project
    private User memberOrNull(Project project, Long userId) {
        if (userId == null) {
            return null;
        }
        User user = userRepository.findById(userId).orElse(null);
        return projectAccess.isMember(project, user) ? user : null;
    }

    @InitBinder("todo")
    public void todoFields(WebDataBinder binder) {
        binder.setAllowedFields("description", "assignedToVariable", "dueDateVariable", "notes");
    }

    @InitBinder("comment")
    public void commentFields(WebDataBinder binder) {
        binder.setAllowedFields("text");
    }

    public String html2text(String html) {
        return Jsoup.parse(html).text();
    }

}
