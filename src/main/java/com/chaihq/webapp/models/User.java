package com.chaihq.webapp.models;

import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Calendar;
import java.util.List;
import java.util.Set;

@Entity(name = "users")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String email; // Use this is a handle

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    private Instant createdAt;
    private Instant updatedAt;


    @Transient
    private String passwordConfirm;

    @Transient
    private String initialFirstNameLastName;

    @Transient
    private boolean addedAlready;

    private String status;

    private String token;
    private Calendar tokenExpirationDate;
    private Calendar tokenUsedDate;

    // Stored as a (LONG)BLOB but bound/extracted as a materialized byte[]
    // (getBytes/setBytes) rather than the streaming java.sql.Blob API, which the
    // SQLite JDBC driver does not implement.
    @JdbcTypeCode(SqlTypes.MATERIALIZED_BLOB)
    private byte[] photo;


    @ManyToMany(mappedBy = "users")
    private List<Project> projects;

    @ManyToMany
    @JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    // @PrePersist
    // protected void onUpdate() {
    //     updatedAt = Instant.now();
    // }

}
