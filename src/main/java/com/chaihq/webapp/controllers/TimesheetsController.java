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
import com.chaihq.webapp.validator.ProjectValidator;
import com.chaihq.webapp.validator.TimesheetValidator;
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
    private ProjectAccess projectAccess;

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



        // Projects and (legacy) teams are the same thing — a space. Show them together.
        List<Project> projects = projectRepository.findByUserAndProjectTypeEquals(currentUser, Constants.PROJECT_TYPE_PROJECT);
        projects.addAll(projectRepository.findByUsersInAndProjectTypeIs(users, Constants.PROJECT_TYPE_PROJECT));
        projects.addAll(projectRepository.findByUserAndProjectTypeEquals(currentUser, Constants.PROJECT_TYPE_TEAM));
        projects.addAll(projectRepository.findByUsersInAndProjectTypeIs(users, Constants.PROJECT_TYPE_TEAM));
        model.put("projects", projects);

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
    public String save(@ModelAttribute("timesheet")Timesheet timesheet, BindingResult bindingResult,
                       final RedirectAttributes redirectAttributes, Map<String, Object> model) {
        User currentUser = projectAccess.currentUser();
        timesheetValidator.validate(timesheet, bindingResult);
        if (bindingResult.hasErrors()) {
            model.put("projects", projectRepository.findByUserAndProjectTypeEquals(currentUser, Constants.PROJECT_TYPE_PROJECT));
            return "timesheets/new";
        }
        timesheet.setProject(projectAccess.project(timesheet.getProjectId(), currentUser));
        timesheet.setUser(currentUser);
        timesheet.setCreatedAt(Calendar.getInstance());
        timesheetRepository.save(timesheet);
        redirectAttributes.addFlashAttribute("notice", "Timesheet entry created!");
        return "redirect:/projects";
    }

    @GetMapping("/timesheet/{id}")
    public String show(@PathVariable Long id, Map<String, Object> model) {
        model.put("project", projectAccess.project(id, projectAccess.currentUser()));
        return "timesheets/show";
    }

    @PostMapping("/timesheet/{id}/delete")
    public String delete(@PathVariable Long id, final RedirectAttributes redirectAttributes) {
        timesheetRepository.delete(ownTimesheet(id));
        redirectAttributes.addFlashAttribute("destruction_notice", "Timesheet entry deleted!");
        return "redirect:/projects";
    }

    @GetMapping("/timesheet/{id}/edit")
    public String edit(@PathVariable Long id, Map<String, Object> model) {
        Timesheet timesheet = ownTimesheet(id);
        model.put("projects", projectRepository.findByUserAndProjectTypeEquals(timesheet.getUser(), Constants.PROJECT_TYPE_PROJECT));
        model.put("timesheet", timesheet);
        return "timesheets/edit";
    }

    @PostMapping("/timesheet/{id}/edit")
    public String update(@PathVariable Long id, @ModelAttribute("timesheet")Timesheet timesheet, BindingResult bindingResult,
                         Map<String, Object> model, final RedirectAttributes redirectAttributes) {
        Timesheet timesheetToUpdate = ownTimesheet(id);
        User currentUser = timesheetToUpdate.getUser();

        timesheetValidator.validate(timesheet, bindingResult);
        if (bindingResult.hasErrors()) {
            model.put("timesheet", timesheet);
            model.put("projects", projectRepository.findByUserAndProjectTypeEquals(currentUser, Constants.PROJECT_TYPE_PROJECT));
            return "timesheets/edit";
        }

        timesheetToUpdate.setProject(projectAccess.project(timesheet.getProjectId(), currentUser));
        timesheetToUpdate.setTask(timesheet.getTask());
        timesheetToUpdate.setNotes(timesheet.getNotes());
        timesheetToUpdate.setManHours(timesheet.getManHours());
        timesheetToUpdate.setTimeLogDate(timesheet.getTimeLogDate());
        timesheetToUpdate.setUpdatedAt(Calendar.getInstance());
        timesheetRepository.save(timesheetToUpdate);
        redirectAttributes.addFlashAttribute("notice", "Timesheet entry updated!");
        return "redirect:/projects";
    }

    // People can only see and change their own time entries
    private Timesheet ownTimesheet(Long id) {
        User currentUser = projectAccess.currentUser();
        Timesheet timesheet = timesheetRepository.findById(id).orElse(null);
        if (timesheet == null || timesheet.getUser() == null || timesheet.getUser().getId() != currentUser.getId()) {
            throw projectAccess.denied(currentUser, "timesheet", id);
        }
        return timesheet;
    }

    @InitBinder("timesheet")
    public void timesheetFields(WebDataBinder binder) {
        binder.setAllowedFields("projectId", "task", "notes", "manHours", "timeLogDate");
    }

}
