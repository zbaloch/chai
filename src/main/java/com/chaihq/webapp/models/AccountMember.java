package com.chaihq.webapp.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Calendar;

// A person's place in an account: owner, admin or member (see Constants.ROLE_*).
@NoArgsConstructor
@Getter
@Setter
@Entity(name = "account_users")
public class AccountMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "account_id", referencedColumnName = "id")
    private Account account;

    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;

    private String role;

    @Column(name = "created_at")
    private Calendar createdAt;
}
