package com.chaihq.webapp.controllers;

import com.chaihq.webapp.models.Account;
import com.chaihq.webapp.models.Project;
import com.chaihq.webapp.models.Todo;
import com.chaihq.webapp.models.User;
import com.chaihq.webapp.repositories.TodoRepository;
import com.chaihq.webapp.services.Accounts;
import com.chaihq.webapp.services.ProjectAccess;
import com.chaihq.webapp.services.ProjectStars;
import com.chaihq.webapp.utilities.Paths;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Everyone's open to-dos across the projects you can see, grouped by project with your starred
 * ones first. Filters live in the URL so a filtered view can be bookmarked.
 */
@Controller
@RequestMapping(Paths.ACCOUNT)
public class TasksController {

    private final TodoRepository todoRepository;
    private final Accounts accounts;
    private final ProjectAccess projectAccess;
    private final ProjectStars projectStars;

    public TasksController(TodoRepository todoRepository, Accounts accounts,
                           ProjectAccess projectAccess, ProjectStars projectStars) {
        this.todoRepository = todoRepository;
        this.accounts = accounts;
        this.projectAccess = projectAccess;
        this.projectStars = projectStars;
    }

    /**
     * @param project  a project id, or blank for all
     * @param assignee "me", "none" (unassigned), a person's id, or blank for anyone
     * @param due      "has", "none", "overdue", or blank for any
     */
    @GetMapping("/tasks")
    public String index(@RequestParam(value = "project", required = false) Long project,
                        @RequestParam(value = "assignee", defaultValue = "") String assignee,
                        @RequestParam(value = "due", defaultValue = "") String due,
                        Model model, HttpSession session) {
        User currentUser = projectAccess.currentUser();
        Account account = accounts.current(session, currentUser);
        if (account == null) {
            return "redirect:/accounts";
        }

        Set<Long> starredProjectIds = projectStars.projectIds(currentUser);
        List<Project> projects = projectStars.starredFirst(accounts.visibleProjects(account, currentUser), starredProjectIds);
        List<Todo> todos = projects.isEmpty() ? List.of() : todoRepository.findAllByProjectInAndDoneFalseOrderByPositionAscDueDateAsc(projects);

        // Anyone with an open to-do or on one of these projects, except you ("Me" covers you)
        Map<Long, User> people = new LinkedHashMap<>();
        projects.forEach(p -> p.getUsers().forEach(user -> people.putIfAbsent(user.getId(), user)));
        todos.stream().map(Todo::getAssignedTo).filter(Objects::nonNull).forEach(user -> people.putIfAbsent(user.getId(), user));
        people.remove(currentUser.getId());

        Predicate<Todo> matches = assigneeFilter(assignee, currentUser).and(dueFilter(due));
        if (project != null) {
            matches = matches.and(todo -> todo.getProject().getId() == project);
        }

        // Group in project order (starred first, then by name)
        Map<Long, List<Todo>> byProject = todos.stream().filter(matches)
                .collect(Collectors.groupingBy(todo -> todo.getProject().getId()));
        List<ProjectTasks> groups = projects.stream()
                .filter(p -> byProject.containsKey(p.getId()))
                .map(p -> new ProjectTasks(p, starredProjectIds.contains(p.getId()), byProject.get(p.getId())))
                .toList();

        model.addAttribute("account", account);
        model.addAttribute("groups", groups);
        model.addAttribute("anyTasks", !todos.isEmpty());
        model.addAttribute("projects", projects);
        model.addAttribute("people", people.values().stream()
                .sorted(Comparator.comparing(User::getFirstName, String.CASE_INSENSITIVE_ORDER)).toList());
        model.addAttribute("projectFilter", project);
        model.addAttribute("assigneeFilter", assignee);
        model.addAttribute("dueFilter", due);
        model.addAttribute("filtered", project != null || !assignee.isEmpty() || !due.isEmpty());
        return "tasks/index";
    }

    public record ProjectTasks(Project project, boolean starred, List<Todo> todos) {}

    private Predicate<Todo> assigneeFilter(String assignee, User currentUser) {
        return switch (assignee) {
            case "" -> todo -> true;
            case "me" -> todo -> todo.getAssignedTo() != null && todo.getAssignedTo().getId() == currentUser.getId();
            case "none" -> todo -> todo.getAssignedTo() == null;
            default -> {
                long id;
                try {
                    id = Long.parseLong(assignee);
                } catch (NumberFormatException e) {
                    yield todo -> true;
                }
                yield todo -> todo.getAssignedTo() != null && todo.getAssignedTo().getId() == id;
            }
        };
    }

    private Predicate<Todo> dueFilter(String due) {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        return switch (due) {
            case "has" -> todo -> todo.getDueDate() != null;
            case "none" -> todo -> todo.getDueDate() == null;
            case "overdue" -> todo -> todo.getDueDate() != null && todo.getDueDate().before(today);
            default -> todo -> true;
        };
    }
}
