package com.chaihq.webapp.controllers;

import com.chaihq.webapp.models.Account;
import com.chaihq.webapp.models.Project;
import com.chaihq.webapp.services.Accounts;
import com.chaihq.webapp.services.ProjectAccess;
import com.chaihq.webapp.utilities.Paths;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * URLs without an account, from before accounts were in the URL (bookmarks, emails, notifications)
 * and the site root. They redirect to the same page in the right account — for projects that's the
 * project's own account, after the usual access check.
 */
@Controller
public class LegacyUrlsController {

    private final Accounts accounts;
    private final ProjectAccess projectAccess;

    public LegacyUrlsController(Accounts accounts, ProjectAccess projectAccess) {
        this.accounts = accounts;
        this.projectAccess = projectAccess;
    }

    // The account you last worked in, or your accounts if you have none
    @GetMapping({"/", "/projects"})
    public String home(HttpSession session) {
        Account account = accounts.current(session, projectAccess.currentUser());
        return account == null ? "redirect:/accounts" : "redirect:" + Paths.home(account);
    }

    @GetMapping({"/project/{projectId:\\d+}", "/project/{projectId:\\d+}/**"})
    public String project(@PathVariable long projectId, HttpServletRequest request) {
        Project project = projectAccess.project(projectId, projectAccess.currentUser());
        return "redirect:" + Paths.account(project.getAccount()) + request.getRequestURI() + query(request);
    }

    @GetMapping("/account/people")
    public String people(HttpSession session) {
        return inCurrentAccount(session, "/people", "");
    }

    @GetMapping("/account/edit")
    public String settings(HttpSession session) {
        return inCurrentAccount(session, "/settings", "");
    }

    @GetMapping("/search")
    public String search(HttpSession session, HttpServletRequest request) {
        return inCurrentAccount(session, "/search", query(request));
    }

    private String inCurrentAccount(HttpSession session, String path, String query) {
        Account account = accounts.current(session, projectAccess.currentUser());
        return account == null ? "redirect:/accounts" : "redirect:" + Paths.account(account) + path + query;
    }

    private static String query(HttpServletRequest request) {
        return request.getQueryString() == null ? "" : "?" + request.getQueryString();
    }
}
