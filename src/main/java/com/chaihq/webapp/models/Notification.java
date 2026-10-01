package com.chaihq.webapp.models;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Entity(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "for_user", referencedColumnName = "id")
    private User forUser;


    private String type;

    @Column(name = "object_id")
    private long objectId;

    @Column(name = "created_at")
    private Calendar createdAt;

    @Column(name = "is_read")
    private boolean read;

    @Column(name = "read_at")
    private Calendar readAt;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    private String message;

    /* todo
            message
    message_comment
            todo_comment
    todo */

    @Transient
    private Todo todo;

    @Transient
    private Message messageObject;

    @Transient
    private Comment comment;

    @Transient
    private ActiveStorageFile activeStorageFile;

    // For an @mention in a chat: where it was, a link to it, and what was said
    @Transient
    private String chatTitle;

    @Transient
    private String chatUrl;

    @Transient
    private String chatText;


    @ManyToOne
    @JoinColumn(name = "from_user", referencedColumnName = "id")
    private User fromUser;


    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }


    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public long getObjectId() {
        return objectId;
    }

    public void setObjectId(long objectId) {
        this.objectId = objectId;
    }

    public Calendar getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Calendar createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public Calendar getReadAt() {
        return readAt;
    }

    public void setReadAt(Calendar readAt) {
        this.readAt = readAt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public User getForUser() {
        return forUser;
    }

    public void setForUser(User forUser) {
        this.forUser = forUser;
    }

    public User getFromUser() {
        return fromUser;
    }

    public void setFromUser(User fromUser) {
        this.fromUser = fromUser;
    }

    public Todo getTodo() {
        return todo;
    }

    public void setTodo(Todo todo) {
        this.todo = todo;
    }

    public Message getMessageObject() {
        return messageObject;
    }

    public void setMessageObject(Message messageObject) {
        this.messageObject = messageObject;
    }

    public Comment getComment() {
        return comment;
    }

    public void setComment(Comment comment) {
        this.comment = comment;
    }

    public ActiveStorageFile getActiveStorageFile() {
        return activeStorageFile;
    }

    public void setActiveStorageFile(ActiveStorageFile activeStorageFile) {
        this.activeStorageFile = activeStorageFile;
    }

    public String getChatTitle() {
        return chatTitle;
    }

    public void setChatTitle(String chatTitle) {
        this.chatTitle = chatTitle;
    }

    public String getChatUrl() {
        return chatUrl;
    }

    public void setChatUrl(String chatUrl) {
        this.chatUrl = chatUrl;
    }

    public String getChatText() {
        return chatText;
    }

    public void setChatText(String chatText) {
        this.chatText = chatText;
    }
}
