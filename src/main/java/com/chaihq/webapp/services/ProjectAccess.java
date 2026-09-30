package com.chaihq.webapp.services;

import com.chaihq.webapp.models.*;
import com.chaihq.webapp.repositories.*;
import com.chaihq.webapp.utilities.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.chaihq.webapp.interceptors.CurrentUserSessionInterceptor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

/**
 * The one place that decides who may see or change what. Projects belong to an account.
 * Everything inside a project (messages, to-dos, comments, files, chat, attachments) is
 * visible to the project's members, and to the account's owners and admins, as long as they
 * are still in that account. Anything a user may not access is reported as "not found", so the
 * response doesn't reveal that it exists.
 */
@Service
public class ProjectAccess {

    private static final Logger log = LoggerFactory.getLogger(ProjectAccess.class);

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final MessageRepository messageRepository;
    private final TodoRepository todoRepository;
    private final CommentRepository commentRepository;
    private final Accounts accounts;

    public ProjectAccess(UserRepository userRepository, ProjectRepository projectRepository,
                         MessageRepository messageRepository, TodoRepository todoRepository,
                         CommentRepository commentRepository, Accounts accounts) {
        this.accounts = accounts;
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.messageRepository = messageRepository;
        this.todoRepository = todoRepository;
        this.commentRepository = commentRepository;
    }

    /** The signed-in user, loaded fresh from the database. */
    public User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = authentication == null ? null : userRepository.findByEmail(authentication.getName());
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return user;
    }

    public boolean isMember(Project project, User user) {
        if (project == null || user == null || project.getAccount() == null || Constants.DELETED.equals(project.getStatus())) {
            return false;
        }
        String role = accounts.role(project.getAccount(), user);
        if (role == null) {
            return false; // no longer in the project's account
        }
        if (Constants.ROLE_OWNER.equals(role) || Constants.ROLE_ADMIN.equals(role)) {
            return true;
        }
        return project.getUsers() != null && project.getUsers().stream().anyMatch(u -> u.getId() == user.getId());
    }

    /** For WebSocket messages, which run outside a web request (and its open database session). */
    @Transactional(readOnly = true)
    public boolean isMember(Long projectId, String email) {
        Project project = projectId == null ? null : projectRepository.findById(projectId).orElse(null);
        User user = email == null ? null : userRepository.findByEmail(email);
        return isMember(project, user);
    }

    /** Account owners and admins manage every project in their account. */
    public boolean canManage(Project project, User user) {
        return isMember(project, user) && accounts.isAdmin(project.getAccount(), user);
    }

    /** A project the user can work in. Under /{accountId}/... it must also be in that account. */
    public Project project(Long projectId, User user) {
        Project project = projectId == null ? null : projectRepository.findById(projectId).orElse(null);
        if (!isMember(project, user) || !inUrlAccount(project)) {
            throw denied(user, "project", projectId);
        }
        return project;
    }

    private static boolean inUrlAccount(Project project) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return true; // not a web request
        }
        Object accountId = CurrentUserSessionInterceptor.accountIdFromUrl(attributes.getRequest());
        return accountId == null || accountId.toString().equals(String.valueOf(project.getAccount().getId()));
    }

    /** A project the user can manage (delete) — an owner or admin of its account. */
    public Project managedProject(Long projectId, User user) {
        Project project = project(projectId, user);
        if (!canManage(project, user)) {
            throw denied(user, "managed project", projectId);
        }
        return project;
    }

    public Message message(Project project, Long messageId, User user) {
        Message message = messageId == null ? null : messageRepository.findById(messageId).orElse(null);
        if (message == null || !Objects.equals(message.getProjectId(), project.getId())
                || Constants.DELETED.equals(message.getStatus())) {
            throw denied(user, "message", messageId);
        }
        return message;
    }

    public Todo todo(Project project, Long todoId, User user) {
        Todo todo = todoId == null ? null : todoRepository.findById(todoId).orElse(null);
        if (todo == null || todo.getProject() == null || todo.getProject().getId() != project.getId()) {
            throw denied(user, "todo", todoId);
        }
        return todo;
    }

    public Comment comment(Project project, Long commentId, User user) {
        Comment comment = commentId == null ? null : commentRepository.findById(commentId).orElse(null);
        if (comment == null || comment.getProjectId() != project.getId()) {
            throw denied(user, "comment", commentId);
        }
        return comment;
    }

    /** Authors can change their own posts; account owners and admins can moderate anything. */
    public void requireAuthorOrAdmin(Project project, User author, User user, String what, long id) {
        boolean isAuthor = author != null && author.getId() == user.getId();
        if (!isAuthor && !canManage(project, user)) {
            throw denied(user, what, id);
        }
    }

    public ResponseStatusException denied(User user, String what, Object id) {
        log.warn("Access denied: user {} tried to access {} {}", user == null ? "anonymous" : user.getId(), what, id);
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
