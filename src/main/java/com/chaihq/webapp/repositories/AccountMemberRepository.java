package com.chaihq.webapp.repositories;

import com.chaihq.webapp.models.Account;
import com.chaihq.webapp.models.AccountMember;
import com.chaihq.webapp.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountMemberRepository extends JpaRepository<AccountMember, Long> {
    Optional<AccountMember> findFirstByAccountAndUser(Account account, User user);
    List<AccountMember> findByUserOrderByAccountNameAsc(User user);
    List<AccountMember> findByAccountOrderByUserFirstNameAscUserLastNameAsc(Account account);
    long countByAccountAndRole(Account account, String role);
}
