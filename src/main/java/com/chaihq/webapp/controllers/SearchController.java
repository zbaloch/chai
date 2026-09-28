package com.chaihq.webapp.controllers;

import com.chaihq.webapp.models.Account;
import com.chaihq.webapp.models.Project;
import com.chaihq.webapp.models.User;
import com.chaihq.webapp.services.Accounts;
import com.chaihq.webapp.services.ProjectAccess;
import com.chaihq.webapp.services.Search;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Set;

@Controller
public class SearchController {

    public record Filter(String type, String label, int count) {}

    private static final Set<String> TYPES = Set.of(Search.MESSAGES, Search.TODOS, Search.COMMENTS);

    private final Search search;
    private final Accounts accounts;
    private final ProjectAccess projectAccess;

    public SearchController(Search search, Accounts accounts, ProjectAccess projectAccess) {
        this.search = search;
        this.accounts = accounts;
        this.projectAccess = projectAccess;
    }

    // Everything searched is limited to projects the person can see in their current account
    @GetMapping("/search")
    public String search(@RequestParam(value = "q", required = false) String query,
                         @RequestParam(value = "type", required = false) String type,
                         @RequestParam(value = "project", required = false) Long projectId,
                         Model model, HttpSession session) {
        User currentUser = projectAccess.currentUser();
        Account account = accounts.current(session, currentUser);
        if (account == null) {
            return "redirect:/accounts";
        }

        List<Project> projects = accounts.visibleProjects(account, currentUser);
        List<Project> searched = projectId == null ? projects
                : projects.stream().filter(project -> project.getId() == projectId).toList();
        String filter = type != null && TYPES.contains(type) ? type : null;

        model.addAttribute("q", query == null ? "" : query.trim());
        model.addAttribute("terms", Search.terms(query));
        model.addAttribute("type", filter);
        model.addAttribute("projectId", searched.size() == 1 && projectId != null ? projectId : null);
        model.addAttribute("projects", projects);
        model.addAttribute("account", account);
        Search.Results results = search.search(query, searched, filter);
        model.addAttribute("results", results);
        int total = results.counts().values().stream().mapToInt(Integer::intValue).sum();
        model.addAttribute("filters", List.of(
                new Filter(null, "All", total),
                new Filter(Search.MESSAGES, "Messages", results.counts().get(Search.MESSAGES)),
                new Filter(Search.TODOS, "To-dos", results.counts().get(Search.TODOS)),
                new Filter(Search.COMMENTS, "Comments", results.counts().get(Search.COMMENTS))));
        return "search/index";
    }
}
