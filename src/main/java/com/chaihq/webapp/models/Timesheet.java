package com.chaihq.webapp.models;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.persistence.*;
import java.util.Calendar;
import java.util.Date;

@Entity(name = "timesheets")
public class Timesheet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;


    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;

    @OneToOne
    @JoinColumn(name = "project_id", referencedColumnName = "id")
    private Project project;

    @Transient
    private Long projectId;

    @Column(name = "created_at")
    private Calendar createdAt;

    @Column(name = "updated_at")
    private Calendar updatedAt;

    @Column(name = "time_log_date")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date timeLogDate;

    @Column(name = "task")
    private String task;

    @Column(name = "notes")
    private String notes;

    @Transient
    private String notesHTML;

    @Column(name = "man_hours")
    private int manHours;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }


    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public Calendar getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Calendar createdAt) {
        this.createdAt = createdAt;
    }

    public Calendar getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Calendar updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Date getTimeLogDate() {
        return timeLogDate;
    }

    public void setTimeLogDate(Date timeLogDate) {
        this.timeLogDate = timeLogDate;
    }

    public String getTask() {
        return task;
    }

    public void setTask(String task) {
        this.task = task;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public int getManHours() {
        return manHours;
    }

    public void setManHours(int manHours) {
        this.manHours = manHours;
    }

    public String getNotesHTML() {
        return notesHTML;
    }

    public void setNotesHTML(String notesHTML) {
        this.notesHTML = notesHTML;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }
}
