package com.chaihq.webapp.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Calendar;

// An emailed invitation to join an account. Only a hash of the link's token is stored.
@NoArgsConstructor
@Getter
@Setter
@Entity(name = "invitations")
public class Invitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "account_id", referencedColumnName = "id")
    private Account account;

    private String email;

    private String role;

    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @ManyToOne
    @JoinColumn(name = "invited_by", referencedColumnName = "id")
    private User invitedBy;

    @Column(name = "created_at")
    private Calendar createdAt;

    @Column(name = "expires_at")
    private Calendar expiresAt;

    @Column(name = "accepted_at")
    private Calendar acceptedAt;
}
