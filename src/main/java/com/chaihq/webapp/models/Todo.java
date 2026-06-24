package com.chaihq.webapp.models;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.persistence.*;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Entity(name = "todos")
public class Todo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String description;

    @OneToOne
    @JoinColumn(name = "assigned_to", referencedColumnName = "id")
    private User assignedTo;

    @Transient
    private Long assignedToVariable;

    @Column(name = "due_date")
    private Calendar dueDate;

    @Transient
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date dueDateVariable;

    private boolean done;

    @OneToOne
    @JoinColumn(name = "created_by", referencedColumnName = "id")
    private User createdBy;

    @Column(name = "created_at")
    private Calendar createdAt;

    @OneToOne
    @JoinColumn(name = "project_id", referencedColumnName = "id")
    private Project project;

    @OneToMany(mappedBy = "todo")
    private List<Comment> comments;

    private String status;

    private String notes;

    @Column(name = "position")
    private Integer position = 0;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public User getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(User assignedTo) {
        this.assignedTo = assignedTo;
    }

    public Calendar getDueDate() {
        return dueDate;
    }

    public void setDueDate(Calendar dueDate) {
        this.dueDate = dueDate;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public Calendar getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Calendar createdAt) {
        this.createdAt = createdAt;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }


    public Long getAssignedToVariable() {
        return assignedToVariable;
    }

    public void setAssignedToVariable(Long assignedToVariable) {
        this.assignedToVariable = assignedToVariable;
    }

    public Date getDueDateVariable() {
        return dueDateVariable;
    }

    public void setDueDateVariable(Date dueDateVariable) {
        this.dueDateVariable = dueDateVariable;
    }

    public List<Comment> getComments() {
        return comments;
    }

    public void setComments(List<Comment> comments) {
        this.comments = comments;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    @Override
    public String toString() {
        return "Todo{" +
                "id=" + id +
                ", description='" + description + '\'' +
                ", assignedTo=" + assignedTo +
                ", assignedToVariable=" + assignedToVariable +
                ", dueDate=" + dueDate +
                ", dueDateVariable=" + dueDateVariable +
                ", done=" + done +
                ", createdBy=" + createdBy +
                ", createdAt=" + createdAt +
                ", project=" + project +
                ", comments=" + comments +
                ", status='" + status + '\'' +
                ", notes='" + notes + '\'' +
                '}';
    }
}
