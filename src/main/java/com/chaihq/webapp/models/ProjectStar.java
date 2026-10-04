package com.chaihq.webapp.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Calendar;

// A project someone starred so it sits at the top of their home page. Only theirs: others don't see it.
@NoArgsConstructor
@Getter
@Setter
@Entity(name = "project_stars")
public class ProjectStar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "project_id", referencedColumnName = "id")
    private Project project;

    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;

    @Column(name = "created_at")
    private Calendar createdAt;
}
