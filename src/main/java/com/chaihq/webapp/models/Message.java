package com.chaihq.webapp.models;

import jakarta.persistence.*;
import java.util.Calendar;
import java.util.List;

@Entity(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String content;

    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "created_at")
    private Calendar createdAt;

    @OneToMany(mappedBy = "message")
    private List<Comment> comments;

    @Transient
    private String titleToDisplay;
    @Transient
    private String contentToDisplay;

    @Transient
    private Project project;

    private String status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Calendar getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Calendar createdAt) {
        this.createdAt = createdAt;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitleToDisplay() {
        return titleToDisplay;
    }

    public void setTitleToDisplay(String titleToDisplay) {
        this.titleToDisplay = titleToDisplay;
    }

    public String getContentToDisplay() {
        return contentToDisplay;
    }

    public void setContentToDisplay(String contentToDisplay) {
        this.contentToDisplay = contentToDisplay;
    }

    public List<Comment> getComments() {
        return comments;
    }

    public void setComments(List<Comment> comments) {
        this.comments = comments;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
