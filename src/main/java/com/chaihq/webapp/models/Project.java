package com.chaihq.webapp.models;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    private String description;

    @Column(name = "project_type")
    private String projectType;

    @Column(name = "created_at")
    private Calendar createdAt;


    // Who created the project. Only a record: projects have no owner, their account does.
    @ManyToOne
    @JoinColumn(name = "created_by", referencedColumnName = "id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "account_id", referencedColumnName = "id")
    private Account account;

    @ManyToMany
    @JoinTable(
            name = "project_users",
            joinColumns = @JoinColumn(name = "project_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private List<User> users;

    private String status;

}
