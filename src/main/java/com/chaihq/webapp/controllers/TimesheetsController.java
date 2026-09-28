package com.chaihq.webapp.controllers;

import com.chaihq.webapp.models.Project;
import com.chaihq.webapp.models.ProjectUserForm;
import com.chaihq.webapp.models.Timesheet;
import com.chaihq.webapp.models.User;
import com.chaihq.webapp.repositories.ProjectRepository;
import com.chaihq.webapp.repositories.TimesheetRepository;
import com.chaihq.webapp.repositories.UserRepository;
import com.chaihq.webapp.services.Accounts;
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
    private Accounts accounts;


    // @GetMapping("/projects")
    @GetMapping("/timesheets")
    public String index() {
        return "redirect:/projects";
    }

    @GetMapping("/timesheet/new")
    public String neew(@ModelAttribute("timesheet")Timesheet timesheet, HttpSession session,
                       Map<String, Object> model) {
        User currentUser = projectAccess.currentUser();
        List<Project> projects = accounts.visibleProjects(accounts.current(session, currentUser), currentUser);
        model.put("projects", projects);
        return "timesheets/new";
    }

    @PostMapping("/timesheet/new")
    public String save(@ModelAttribute("timesheet")Timesheet timesheet, BindingResult bindingResult,
                       final RedirectAttributes redirectAttributes, Map<String, Object> model, HttpSession session) {
        User currentUser = projectAccess.currentUser();
        timesheetValidator.validate(timesheet, bindingResult);
        if (bindingResult.hasErrors()) {
            model.put("projects", accounts.visibleProjects(accounts.current(session, currentUser), currentUser));
            return "timesheets/new";
        }
        timesheet.setProject(projectAccess.project(timesheet.getProjectId(), currentUser));
        timesheet.setUser(currentUser);
        timesheet.setCreatedAt(Calendar.getInstance());
        timesheetRepository.save(timesheet);
        redirectAttributes.addFlashAttribute("notice", "Timesheet entry created!");
        return "redirect:/projects";
    }

    // Old duplicate of the project page
    @GetMapping("/timesheet/{id}")
    public String show(@PathVariable Long id) {
        return "redirect:/project/" + projectAccess.project(id, projectAccess.currentUser()).getId();
    }

    @PostMapping("/timesheet/{id}/delete")
    public String delete(@PathVariable Long id, final RedirectAttributes redirectAttributes) {
        timesheetRepository.delete(ownTimesheet(id));
        redirectAttributes.addFlashAttribute("destruction_notice", "Timesheet entry deleted!");
        return "redirect:/projects";
    }

    @GetMapping("/timesheet/{id}/edit")
    public String edit(@PathVariable Long id, Map<String, Object> model, HttpSession session) {
        Timesheet timesheet = ownTimesheet(id);
        model.put("projects", accounts.visibleProjects(accounts.current(session, timesheet.getUser()), timesheet.getUser()));
        model.put("timesheet", timesheet);
        return "timesheets/edit";
    }

    @PostMapping("/timesheet/{id}/edit")
    public String update(@PathVariable Long id, @ModelAttribute("timesheet")Timesheet timesheet, BindingResult bindingResult,
                         Map<String, Object> model, final RedirectAttributes redirectAttributes, HttpSession session) {
        Timesheet timesheetToUpdate = ownTimesheet(id);
        User currentUser = timesheetToUpdate.getUser();

        timesheetValidator.validate(timesheet, bindingResult);
        if (bindingResult.hasErrors()) {
            model.put("timesheet", timesheet);
            model.put("projects", accounts.visibleProjects(accounts.current(session, currentUser), currentUser));
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
