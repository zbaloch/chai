package com.chaihq.webapp.controllers;

import com.chaihq.webapp.models.Project;
import com.chaihq.webapp.models.ProjectUserForm;
import com.chaihq.webapp.models.Timesheet;
import com.chaihq.webapp.models.User;
import com.chaihq.webapp.repositories.ProjectRepository;
import com.chaihq.webapp.repositories.TimesheetRepository;
import com.chaihq.webapp.repositories.UserRepository;
import com.chaihq.webapp.services.ProjectAccess;
import com.chaihq.webapp.utilities.Constants;
import com.chaihq.webapp.utilities.Util;
import com.chaihq.webapp.validator.ProjectValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;

import java.security.Principal;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

@Controller
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
    private TimesheetRepository timesheetRepository;


    // @GetMapping("/projects")
    @RequestMapping(value = {"/", "/projects"}, method = RequestMethod.GET)
    public String index(Map<String, Object> model, HttpSession httpSession) {
        // List<Project> projects = projectRepository.findAll();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentPrincipalName = authentication.getName();
        // TODO: Maybe put this user in the session so that its not required to inqure from database again and again.
        User user = userRepository.findByEmail(currentPrincipalName);
        String intialFirstNameLastName = "" + user.getFirstName().charAt(0) + "" + user.getLastName().charAt(0);
        user.setInitialFirstNameLastName(intialFirstNameLastName);
        httpSession.setAttribute(Constants.CURRENT_USER, user); // current_user is a term used within ruby on rails framework.

        User currentUser = (User) httpSession.getAttribute(Constants.CURRENT_USER);
        List<User> users = new ArrayList<User>();
        users.add(currentUser);

        List<Project> hqs = projectRepository.findByUserAndProjectTypeEquals(currentUser, Constants.PROJECT_TYPE_HQ);
        List<Project> hqsPartOf = projectRepository.findByUsersInAndProjectTypeIs(users, Constants.PROJECT_TYPE_HQ);
        hqs.addAll(hqsPartOf);
        model.put("hqs", hqs);



        // Projects and (legacy) teams are the same thing — a space. Show them together.
        List<Project> projects = projectRepository.findByUserAndProjectTypeEquals(currentUser, Constants.PROJECT_TYPE_PROJECT);
        projects.addAll(projectRepository.findByUsersInAndProjectTypeIs(users, Constants.PROJECT_TYPE_PROJECT));
        projects.addAll(projectRepository.findByUserAndProjectTypeEquals(currentUser, Constants.PROJECT_TYPE_TEAM));
        projects.addAll(projectRepository.findByUsersInAndProjectTypeIs(users, Constants.PROJECT_TYPE_TEAM));
        model.put("projects", projects);

        // Get current users timelog to display on the homepage
        List<Timesheet> timesheets = timesheetRepository.findAllByUserOrderByTimeLogDateDesc(currentUser);
        Util util = new Util();
        for(Timesheet timesheet: timesheets) {
            timesheet.setNotesHTML( util.markdownToHtml(timesheet.getNotes()));
        }
        model.put("timesheets", timesheets);

        return "projects/index";
    }

    @GetMapping("/project/new")
    public String neew(@ModelAttribute("project")Project project) {
        return "projects/new";
    }

    @PostMapping("/project/new")
    public String save(@ModelAttribute("project")Project project, final RedirectAttributes redirectAttributes,
                       BindingResult bindingResult) {
        projectValidator.validate(project, bindingResult);
        if (bindingResult.hasErrors()) {
            return "projects/new";
        }
        project.setUser(projectAccess.currentUser());
        project.setProjectType(Constants.PROJECT_TYPE_PROJECT);
        project.setCreatedAt(Calendar.getInstance());
        projectRepository.save(project);
        redirectAttributes.addFlashAttribute("notice", "Project saved!");
        return "redirect:/projects";
    }

    @GetMapping("/project/{id}")
    public String show(@PathVariable Long id, Model model) {
        User currentUser = projectAccess.currentUser();
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("project", projectAccess.project(id, currentUser));
        return "projects/show";
    }

    @PostMapping("/project/{id}/delete")
    public String delete(@PathVariable Long id, final RedirectAttributes redirectAttributes) {
        Project projectToDelete = projectAccess.ownedProject(id, projectAccess.currentUser());
        projectToDelete.setStatus(Constants.DELETED);
        projectRepository.save(projectToDelete);
        redirectAttributes.addFlashAttribute("destruction_notice", "Project deleted!");
        return "redirect:/projects";
    }

    @GetMapping("/project/{id}/users")
    public String users(@PathVariable Long id, Model model) {
        Project project = projectAccess.ownedProject(id, projectAccess.currentUser());
        showPeople(project, model);
        return "projects/users";
    }

    @PostMapping("/project/{id}/users")
    public String addRemoveUser(Model model, @ModelAttribute("puf") ProjectUserForm puf, @PathVariable Long id) {
        // The project comes from the URL, never from the form, and only its owner may change members
        Project project = projectAccess.ownedProject(id, projectAccess.currentUser());

        if ("add".equals(puf.getAction())) {
            String email = puf.getEmail() == null ? "" : puf.getEmail().trim();
            User userToAdd = userRepository.findByEmail(email);
            if (userToAdd == null) {
                model.addAttribute("error", "No one with that email has a Chai account yet.");
            } else if (projectAccess.isMember(project, userToAdd)) {
                model.addAttribute("message", userToAdd.getFirstName() + " is already in " + project.getName() + ".");
            } else {
                project.getUsers().add(userToAdd);
                projectRepository.save(project);
                model.addAttribute("message", userToAdd.getFirstName() + " added to " + project.getName() + ".");
            }
        } else if ("remove".equals(puf.getAction())) {
            if (project.getUsers().removeIf(user -> user.getId() == puf.getUserId())) {
                projectRepository.save(project);
                model.addAttribute("message", "Removed from " + project.getName() + ".");
            }
        }

        showPeople(project, model);
        return "projects/users";
    }

    // Only the project's own people are listed — never every account in the system
    private void showPeople(Project project, Model model) {
        List<User> people = new ArrayList<>();
        people.add(project.getUser());
        for (User member : project.getUsers()) {
            member.setAddedAlready(true);
            people.add(member);
        }
        model.addAttribute("project", project);
        model.addAttribute("users", people);
        model.addAttribute("puf", new ProjectUserForm());
    }

    @GetMapping("/project/{id}/edit")
    public String edit(@PathVariable Long id, Map<String, Object> model) {
        model.put("project", projectAccess.ownedProject(id, projectAccess.currentUser()));
        return "projects/edit";
    }

    @PostMapping("/project/{id}/edit")
    public String update(@PathVariable Long id, @ModelAttribute("project")Project project,
                         BindingResult bindingResult, final RedirectAttributes redirectAttributes) {
        Project projectToUpdate = projectAccess.ownedProject(id, projectAccess.currentUser());

        projectValidator.validate(project, bindingResult);
        if (bindingResult.hasErrors()) {
            project.setId(id);
            return "projects/edit";
        }

        projectToUpdate.setName(project.getName());
        projectToUpdate.setDescription(project.getDescription());
        projectRepository.save(projectToUpdate);
        redirectAttributes.addFlashAttribute("notice", "Project updated!");
        return "redirect:/project/" + id;
    }

    // Only these fields may come from forms; anything else (owner, members, status, id) is ignored
    @InitBinder("project")
    public void projectFields(WebDataBinder binder) {
        binder.setAllowedFields("name", "description");
    }

    @InitBinder("puf")
    public void memberFields(WebDataBinder binder) {
        binder.setAllowedFields("action", "email", "userId");
    }
}
