package com.chaihq.webapp.models;

import javax.persistence.*;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Calendar;
import java.util.List;

@NoArgsConstructor
@Getter
@Setter
@Entity(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)

    private long id;
    private String name;
    private String description;

    @Column(name = "project_type")
    private String projectType;

    @Column(name = "created_at")
    private Calendar createdAt;


    @OneToOne
    @JoinColumn(name = "created_by", referencedColumnName = "id")
    private User user;

    @ManyToMany
    @JoinTable(
            name = "project_users",
            joinColumns = @JoinColumn(name = "project_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private List<User> users;

    private String status;

}
