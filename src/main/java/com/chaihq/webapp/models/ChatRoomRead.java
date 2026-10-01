package com.chaihq.webapp.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Calendar;

// How far a person has read in a chat, and whether they've muted it (no dot, mentions still reach them).
@NoArgsConstructor
@Getter
@Setter
@Entity(name = "chat_room_reads")
public class ChatRoomRead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "room_id", referencedColumnName = "id")
    private ChatRoom room;

    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;

    @Column(name = "last_read_at")
    private Calendar lastReadAt;

    private boolean muted;
}
