package com.chaihq.webapp.models;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.Calendar;
import java.util.List;

@Entity(name = "chat_messages")
public class Chat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    // @OneToOne(cascade = CascadeType.ALL)
    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;

    // Set for a project's chat; 0 for a direct chat
    @Column(name = "project_id")
    private long projectId;

    @Column(name = "room_id")
    private Long roomId;

    // Ids of the people @mentioned, comma-separated
    @Column(length = 2000)
    private String mentions;

    @Column(name = "edited_at")
    private Calendar editedAt;

    // For display, filled in when a chat is shown
    @Transient
    private String html;

    @Transient
    private boolean continued;

    @Transient
    private String dayLabel;

    @Transient
    private boolean firstUnread;

    @Transient
    private boolean mentionsMe;

    public Long getRoomId() {
        return roomId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }

    public String getMentions() {
        return mentions;
    }

    public void setMentions(String mentions) {
        this.mentions = mentions;
    }

    public Calendar getEditedAt() {
        return editedAt;
    }

    public void setEditedAt(Calendar editedAt) {
        this.editedAt = editedAt;
    }

    public String getHtml() {
        return html;
    }

    public void setHtml(String html) {
        this.html = html;
    }

    public boolean isContinued() {
        return continued;
    }

    public void setContinued(boolean continued) {
        this.continued = continued;
    }

    public String getDayLabel() {
        return dayLabel;
    }

    public void setDayLabel(String dayLabel) {
        this.dayLabel = dayLabel;
    }

    public boolean isFirstUnread() {
        return firstUnread;
    }

    public void setFirstUnread(boolean firstUnread) {
        this.firstUnread = firstUnread;
    }

    public boolean isMentionsMe() {
        return mentionsMe;
    }

    public void setMentionsMe(boolean mentionsMe) {
        this.mentionsMe = mentionsMe;
    }

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    private String message;

    @Column(name = "created_at")
    private Calendar createdAt;

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

    public long getProjectId() {
        return projectId;
    }

    public void setProjectId(long projectId) {
        this.projectId = projectId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Calendar getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Calendar createdAt) {
        this.createdAt = createdAt;
    }
}
