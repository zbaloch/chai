package com.chaihq.webapp.controllers;

import com.chaihq.webapp.models.Project;
import com.chaihq.webapp.models.ProjectUserForm;
import com.chaihq.webapp.models.Timesheet;
import com.chaihq.webapp.models.User;
import com.chaihq.webapp.repositories.ProjectRepository;
import com.chaihq.webapp.repositories.TimesheetRepository;
import com.chaihq.webapp.repositories.UserRepository;
import com.chaihq.webapp.utilities.Constants;
import com.chaihq.webapp.utilities.Util;
import com.chaihq.webapp.validator.ProjectValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
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



        List<Project> projects = projectRepository.findByUserAndProjectTypeEquals(currentUser, Constants.PROJECT_TYPE_PROJECT);
        List<Project> projectsPartOf = projectRepository.findByUsersInAndProjectTypeIs(users, Constants.PROJECT_TYPE_PROJECT);
        projects.addAll(projectsPartOf);
        model.put("projects", projects);

        List<Project> teams = projectRepository.findByUserAndProjectTypeEquals(currentUser, Constants.PROJECT_TYPE_TEAM);
        List<Project> teamsPartOf = projectRepository.findByUsersInAndProjectTypeIs(users, Constants.PROJECT_TYPE_TEAM);
        teams.addAll(teamsPartOf);
        model.put("teams", teams);

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
    public String neew(@ModelAttribute("project")Project project, HttpSession httpSession) {
        // httpSession.setAttribute("current_user", dummyLoginUser());
        System.out.println("neew");
        return "projects/new";
    }

    @PostMapping("/project/new")
    public String save(@ModelAttribute("project")Project project, final RedirectAttributes redirectAttributes, HttpSession httpSession,
                       BindingResult bindingResult) {
        projectValidator.validate(project, bindingResult);
        if (bindingResult.hasErrors()) {
            return "projects/new";
        }
        User user = (User) httpSession.getAttribute(Constants.CURRENT_USER);
        System.out.println("user: " + user.getId());
        project.setUser(user);
        project.setCreatedAt(Calendar.getInstance());
        projectRepository.save(project);
        redirectAttributes.addFlashAttribute("notice", "Project saved!");
        return "redirect:/projects";
    }

    @GetMapping("/project/{id}")
    public String show(@PathVariable Long id, Model model, Principal principal) {
        User currentUser = userRepository.findByEmail(principal.getName());
        Project project = projectRepository.getReferenceById(id);
        System.out.println(project.getName());
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("project", project);
        return "projects/show";
    }

    @GetMapping("/project/{id}/delete")
    public String delete(@PathVariable Long id, final RedirectAttributes redirectAttributes ) {
        // TODO: Make sure this belongs to the current loggedin user
        Project projectToDelete = projectRepository.getReferenceById(id);
        projectToDelete.setStatus(Constants.DELETED);
        projectRepository.save(projectToDelete);
        redirectAttributes.addFlashAttribute("destruction_notice", "Project deleted!");
        return "redirect:/projects";
    }


    @GetMapping("/project/{id}/users")
    public String users(@PathVariable Long id, Model model, HttpSession httpSession
                        ) {
        Project project = projectRepository.getReferenceById(id);
        model.addAttribute("project", project);
        // Get all the users
        List<User> users = userRepository.findAll();

        for(int i=0; i<users.size(); i++) {
            User user = users.get(i);
            for(int j=0; j<project.getUsers().size(); j++) {
                User projectUser = project.getUsers().get(j);
                if(user.getId() == projectUser.getId()) {
                    user.setAddedAlready(true);
                }
            }
        }

        model.addAttribute("users", users);
        model.addAttribute("puf", new ProjectUserForm());
        return "projects/users";
    }

    @PostMapping("/project/{id}/users")
    public String addRemoveUser(Map<String, Object> model,
                        @ModelAttribute ProjectUserForm puf,
                        @PathVariable Long id,
                        HttpSession httpSession) {
        System.out.println("Adding users..." + puf.getProjectId() + ", " + puf.getUserId() + ", " + puf.getAction());
        // TODO: Make sure the project belongs to this user
        Project project = projectRepository.getReferenceById(puf.getProjectId());
        System.out.println(project.getUsers().size());
        User userToAdd = (User) userRepository.getReferenceById(puf.getUserId());
        List<User> users = userRepository.findAll();

        if("add".equals(puf.getAction())) {
            project.getUsers().add(userToAdd);
            model.put("notice", userToAdd.getFirstName() + " added to " + project.getName());
            // model.put("notice", "User added!");
            projectRepository.save(project);
        } else if("remove".equals(puf.getAction())) {

            for(int i=0; i<project.getUsers().size(); i++) {
                User user = project.getUsers().get(i);
                System.out.println(user.getId() + " == " + puf.getUserId());
                System.out.println(user.getId() == puf.getUserId());
                if(user.getId() == puf.getUserId()) {
                    project.getUsers().remove(i);
                    projectRepository.save(project);
                    break;
                }
            }

            model.put("notice", "User removed!");
        } else {
            // model.put("notice", "User added!");
        }

        for(int i=0; i<users.size(); i++) {
            User user = users.get(i);
            for(int j=0; j<project.getUsers().size(); j++) {
                User projectUser = project.getUsers().get(j);
                if(user.getId() == projectUser.getId()) {
                    user.setAddedAlready(true);
                }
            }
        }
        model.put("project", project);
        model.put("users", users);
        model.put("puf", puf);



        return "projects/users";
    }





    @GetMapping("/project/{id}/edit")
    public String edit(@PathVariable Long id, Map<String, Object> model) {
        Project project = projectRepository.getReferenceById(id);
        System.out.println(project.getName());
        model.put("project", project);
        return "projects/edit";
    }


    @PostMapping("/project/{id}/edit")
    public String update(@PathVariable Long id, @ModelAttribute("project")Project project,
                         Map<String, Object> model, HttpSession httpSession, BindingResult bindingResult) {

        projectValidator.validate(project, bindingResult);

        if (bindingResult.hasErrors()) {
            return "projects/edit";
        }

        Project projectToUpdate = projectRepository.getReferenceById(id);
        System.out.println(project.getName());
        projectToUpdate.setName(project.getName());
        projectToUpdate.setDescription(project.getDescription());
        projectRepository.save(projectToUpdate);
        model.put("project", projectToUpdate);
        model.put("notice", "Project updated!");
        return "projects/show";
    }

}
