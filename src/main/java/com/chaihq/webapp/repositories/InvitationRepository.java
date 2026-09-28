package com.chaihq.webapp.repositories;

import com.chaihq.webapp.models.Account;
import com.chaihq.webapp.models.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {
    Optional<Invitation> findByTokenHash(String tokenHash);
    List<Invitation> findByAccountAndAcceptedAtIsNullOrderByCreatedAtDesc(Account account);
    List<Invitation> findByAccountAndEmailIgnoreCaseAndAcceptedAtIsNull(Account account, String email);
}
