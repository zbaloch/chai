package com.chaihq.webapp.controllers;

import com.chaihq.webapp.utilities.Paths;
import com.chaihq.webapp.models.*;
import com.chaihq.webapp.repositories.ProjectRepository;
import com.chaihq.webapp.repositories.TimesheetRepository;
import com.chaihq.webapp.repositories.UserRepository;
import com.chaihq.webapp.services.Accounts;
import com.chaihq.webapp.services.Chats;
import com.chaihq.webapp.services.ProjectAccess;
import com.chaihq.webapp.utilities.Constants;
import com.chaihq.webapp.utilities.Util;
import com.chaihq.webapp.validator.ProjectValidator;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

@Controller
@RequestMapping(Paths.ACCOUNT)
public class ProjectsController {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectValidator projectValidator;

    @Autowired
    private ProjectAccess projectAccess;

    @Autowired
    private Accounts accounts;

    @Autowired
    private TimesheetRepository timesheetRepository;

    @Autowired
    private Chats chats;

    // Home: the projects in the account you're working in
    @GetMapping("/projects")
    public String index(Model model, HttpSession session) {
        User currentUser = projectAccess.currentUser();
        Account account = accounts.current(session, currentUser);
        if (account == null) {
            return "redirect:/accounts";
        }

        model.addAttribute("account", account);
        model.addAttribute("projects", accounts.visibleProjects(account, currentUser));
        model.addAttribute("unreadChatProjectIds", chats.unread(account, currentUser).projectIds());

        // The current user's time log, shown on the home page
        List<Timesheet> timesheets = timesheetRepository.findAllByUserOrderByTimeLogDateDesc(currentUser);
        Util util = new Util();
        for (Timesheet timesheet : timesheets) {
            timesheet.setNotesHTML(util.markdownToHtml(timesheet.getNotes()));
        }
        model.addAttribute("timesheets", timesheets);

        return "projects/index";
    }

    @GetMapping("/project/new")
    public String neew(@ModelAttribute("project") Project project, HttpSession session) {
        if (accounts.current(session, projectAccess.currentUser()) == null) {
            return "redirect:/accounts";
        }
        return "projects/new";
    }

    // Anyone in the account can start a project; they're its first member
    @PostMapping("/project/new")
    public String save(@ModelAttribute("project") Project project, BindingResult bindingResult,
                       HttpSession session, RedirectAttributes redirectAttributes) {
        User currentUser = projectAccess.currentUser();
        Account account = accounts.current(session, currentUser);
        if (account == null) {
            return "redirect:/accounts";
        }

        projectValidator.validate(project, bindingResult);
        if (bindingResult.hasErrors()) {
            return "projects/new";
        }

        project.setAccount(account);
        project.setUser(currentUser);
        project.setUsers(new ArrayList<>(List.of(currentUser)));
        project.setProjectType(Constants.PROJECT_TYPE_PROJECT);
        project.setCreatedAt(Calendar.getInstance());
        projectRepository.save(project);
        redirectAttributes.addFlashAttribute("notice", "Project saved!");
        return "redirect:" + Paths.project(project);
    }

    @GetMapping("/project/{id}")
    public String show(@PathVariable Long id, Model model, HttpSession session) {
        User currentUser = projectAccess.currentUser();
        Project project = projectAccess.project(id, currentUser);

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("project", project);
        model.addAttribute("canManage", projectAccess.canManage(project, currentUser));
        model.addAttribute("chatUnread", chats.unread(project.getAccount(), currentUser).projectIds().contains(project.getId()));
        return "projects/show";
    }

    // The people an @mention in this project's comments can name (everyone on it but you)
    @GetMapping("/project/{id}/mentionable")
    public String mentionable(@PathVariable Long id, Model model) {
        User currentUser = projectAccess.currentUser();
        Project project = projectAccess.project(id, currentUser);
        model.addAttribute("people", project.getUsers().stream()
                .filter(user -> user.getId() != currentUser.getId() && projectAccess.isMember(project, user))
                .toList());
        return "fragments/comments :: mentionItems";
    }

    @PostMapping("/project/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Project projectToDelete = projectAccess.managedProject(id, projectAccess.currentUser());
        projectToDelete.setStatus(Constants.DELETED);
        projectRepository.save(projectToDelete);
        redirectAttributes.addFlashAttribute("destruction_notice", "Project deleted!");
        return "redirect:" + Paths.home(projectToDelete.getAccount());
    }

    // Anyone on a project can add people from its account; only account owners and admins can remove them
    @GetMapping("/project/{id}/users")
    public String users(@PathVariable Long id, Model model) {
        User currentUser = projectAccess.currentUser();
        showPeople(projectAccess.project(id, currentUser), currentUser, model);
        return "projects/users";
    }

    @PostMapping("/project/{id}/users")
    public String addRemoveUser(Model model, @ModelAttribute("puf") ProjectUserForm puf, @PathVariable Long id) {
        User currentUser = projectAccess.currentUser();
        Project project = projectAccess.project(id, currentUser);
        User person = userRepository.findById(puf.getUserId()).orElse(null);

        if ("add".equals(puf.getAction())) {
            if (person == null || accounts.membership(project.getAccount(), person) == null) {
                model.addAttribute("error", "Only people in " + project.getAccount().getName() + " can be added.");
            } else if (project.getUsers().stream().noneMatch(user -> user.getId() == person.getId())) {
                project.getUsers().add(person);
                projectRepository.save(project);
                model.addAttribute("message", person.getFirstName() + " added to " + project.getName() + ".");
            }
        } else if ("remove".equals(puf.getAction())) {
            // Members can't leave or remove anyone; only account owners and admins take people off a project
            if (!projectAccess.canManage(project, currentUser)) {
                throw projectAccess.denied(currentUser, "project member removal", project.getId());
            }
            if (person != null && project.getUsers().removeIf(user -> user.getId() == person.getId())) {
                projectRepository.save(project);
                model.addAttribute("message", person.getFirstName() + " removed from " + project.getName() + ".");
            }
        }

        showPeople(project, currentUser, model);
        return "projects/users";
    }

    private void showPeople(Project project, User currentUser, Model model) {
        List<User> onProject = project.getUsers();
        List<User> others = new ArrayList<>();
        for (AccountMember membership : accounts.people(project.getAccount())) {
            User person = membership.getUser();
            if (onProject.stream().noneMatch(user -> user.getId() == person.getId())) {
                others.add(person);
            }
        }
        model.addAttribute("project", project);
        model.addAttribute("people", onProject);
        model.addAttribute("others", others);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("canManage", projectAccess.canManage(project, currentUser));
    }

    @GetMapping("/project/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("project", projectAccess.project(id, projectAccess.currentUser()));
        return "projects/edit";
    }

    @PostMapping("/project/{id}/edit")
    public String update(@PathVariable Long id, @ModelAttribute("project") Project project,
                         BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        Project projectToUpdate = projectAccess.project(id, projectAccess.currentUser());

        projectValidator.validate(project, bindingResult);
        if (bindingResult.hasErrors()) {
            project.setId(id);
            return "projects/edit";
        }

        projectToUpdate.setName(project.getName());
        projectToUpdate.setDescription(project.getDescription());
        projectRepository.save(projectToUpdate);
        redirectAttributes.addFlashAttribute("notice", "Project updated!");
        return "redirect:" + Paths.project(projectToUpdate);
    }

    // Only these fields may come from forms; anything else (account, members, status, id) is ignored
    @InitBinder("project")
    public void projectFields(WebDataBinder binder) {
        binder.setAllowedFields("name", "description");
    }

    @InitBinder("puf")
    public void memberFields(WebDataBinder binder) {
        binder.setAllowedFields("action", "userId");
    }
}
