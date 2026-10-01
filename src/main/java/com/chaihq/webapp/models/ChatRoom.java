package com.chaihq.webapp.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * A conversation. A project has one chat, open to everyone on the project (project set, no members).
 * A direct chat is between a fixed group of people in an account (project null, members set).
 */
@NoArgsConstructor
@Getter
@Setter
@Entity(name = "chat_rooms")
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "account_id", referencedColumnName = "id")
    private Account account;

    @ManyToOne
    @JoinColumn(name = "project_id", referencedColumnName = "id")
    private Project project;

    @ManyToMany
    @JoinTable(
            name = "chat_room_users",
            joinColumns = @JoinColumn(name = "room_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private List<User> members = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "created_by", referencedColumnName = "id")
    private User createdBy;

    // Messages before this aren't "new" to anyone (a project's earlier chat, from before rooms existed)
    @Column(name = "created_at")
    private Calendar createdAt;

    @Column(name = "last_message_at")
    private Calendar lastMessageAt;

    public boolean isDirect() {
        return project == null;
    }
}
