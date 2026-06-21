package com.chaihq.webapp.controllers;

import com.chaihq.webapp.models.Project;
import com.chaihq.webapp.models.ProjectUserForm;
import com.chaihq.webapp.models.Timesheet;
import com.chaihq.webapp.models.User;
import com.chaihq.webapp.repositories.ProjectRepository;
import com.chaihq.webapp.repositories.TimesheetRepository;
import com.chaihq.webapp.repositories.UserRepository;
import com.chaihq.webapp.utilities.Constants;
import com.chaihq.webapp.validator.ProjectValidator;
import com.chaihq.webapp.validator.TimesheetValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

@Controller
public class TimesheetsController {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TimesheetRepository timesheetRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectValidator projectValidator;

    @Autowired
    private TimesheetValidator timesheetValidator;

    @Autowired
    private TimesheetRepository timeLogRepository;


    // @GetMapping("/projects")
    @RequestMapping(value = { "/timesheets"}, method = RequestMethod.GET)
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
        List<Timesheet> timeLogs = timeLogRepository.findAllByUserOrderByTimeLogDateDesc(currentUser);
        model.put("timeLogs", timeLogs);

        return "projects/index";
    }

    @GetMapping("/timesheet/new")
    public String neew(@ModelAttribute("timesheet")Timesheet timesheet, HttpSession httpSession,
                       Map<String, Object> model) {
        User currentUser = (User) httpSession.getAttribute(Constants.CURRENT_USER);
        List<Project> projects = projectRepository.findByUserAndProjectTypeEquals(currentUser,
                Constants.PROJECT_TYPE_PROJECT);
        model.put("projects", projects);
        return "timesheets/new";
    }

    @PostMapping("/timesheet/new")
    public String save(@ModelAttribute("timesheet")Timesheet timesheet, final RedirectAttributes redirectAttributes,
                       HttpSession httpSession,
                       BindingResult bindingResult,
                       Map<String, Object> model) {
        timesheetValidator.validate(timesheet, bindingResult);
        if (bindingResult.hasErrors()) {
            User currentUser = (User) httpSession.getAttribute(Constants.CURRENT_USER);
            List<Project> projects = projectRepository.findByUserAndProjectTypeEquals(currentUser,
                    Constants.PROJECT_TYPE_PROJECT);
            model.put("projects", projects);
            return "timesheets/new";
        }
        Project project = projectRepository.getReferenceById(timesheet.getProjectId());
        User user = (User) httpSession.getAttribute(Constants.CURRENT_USER);
        System.out.println("user: " + user.getId());
        timesheet.setProject(project);
        timesheet.setUser(user);
        timesheet.setCreatedAt(Calendar.getInstance());
        timesheetRepository.save(timesheet);
        redirectAttributes.addFlashAttribute("notice", "Timesheet entry created!");
        return "redirect:/projects";
    }

    @GetMapping("/timesheet/{id}")
    public String show(@PathVariable Long id, Map<String, Object> model) {
        Project project = projectRepository.getReferenceById(id);
        System.out.println(project.getName());
        model.put("project", project);
        return "timesheets/show";
    }

    @GetMapping("/timesheet/{id}/delete")
    public String delete(@PathVariable Long id, final RedirectAttributes redirectAttributes,
                         HttpSession httpSession) {
        // TODO: Make sure this belongs to the current loggedin user
        Timesheet timesheetToDelete = timesheetRepository.getReferenceById(id);
        User currentUser = (User) httpSession.getAttribute(Constants.CURRENT_USER);
        if(timesheetToDelete.getUser().getId() == currentUser.getId()) {
            timesheetRepository.delete(timesheetToDelete);
            redirectAttributes.addFlashAttribute("destruction_notice", "Timesheet entry deleted!");
        } else {
            redirectAttributes.addFlashAttribute("destruction_notice", "This is not your timesheet!");
        }
        return "redirect:/projects";
    }



    @GetMapping("/timesheet/{id}/edit")
    public String edit(@PathVariable Long id, Map<String, Object> model, HttpSession httpSession,
                       final RedirectAttributes redirectAttributes) {

        Timesheet timesheet = timesheetRepository.getReferenceById(id);
        User currentUser = (User) httpSession.getAttribute(Constants.CURRENT_USER);

        if(timesheet.getUser().getId() == currentUser.getId()) {
            List<Project> projects = projectRepository.findByUserAndProjectTypeEquals(currentUser,
                    Constants.PROJECT_TYPE_PROJECT);
            model.put("projects", projects);
            model.put("timesheet", timesheet);
            return "timesheets/edit";
        } else {
            redirectAttributes.addFlashAttribute("destruction_notice", "This timesheet does not belong to you!");
            return "redirect:/projects";
        }
    }

    @PostMapping("/timesheet/{id}/edit")
    public String update(@PathVariable Long id, @ModelAttribute("timesheet")Timesheet timesheet,
                         Map<String, Object> model, HttpSession httpSession, BindingResult bindingResult,
                         final RedirectAttributes redirectAttributes) {

        timesheetValidator.validate(timesheet, bindingResult);

        if (bindingResult.hasErrors()) {
            model.put("timesheet", timesheet);
            User currentUser = (User) httpSession.getAttribute(Constants.CURRENT_USER);
            List<Project> projects = projectRepository.findByUserAndProjectTypeEquals(currentUser,
                    Constants.PROJECT_TYPE_PROJECT);
            model.put("projects", projects);
            return "timesheets/edit";
        }

        Timesheet timesheetToUpdate = timesheetRepository.getReferenceById(id);

        Project project = projectRepository.getReferenceById(timesheet.getProjectId());
        User user = (User) httpSession.getAttribute(Constants.CURRENT_USER);
        System.out.println("user: " + user.getId());
        timesheetToUpdate.setProject(project);
        timesheetToUpdate.setTask(timesheet.getTask());
        timesheetToUpdate.setNotes(timesheet.getNotes());
        timesheetToUpdate.setManHours(timesheet.getManHours());
        timesheetToUpdate.setTimeLogDate(timesheet.getTimeLogDate());
        // timesheet.setUser(user);
        timesheetToUpdate.setUpdatedAt(Calendar.getInstance());
        timesheetRepository.save(timesheetToUpdate);
        redirectAttributes.addFlashAttribute("notice", "Timesheet entry updated!");
        return "redirect:/projects";

    }

}
