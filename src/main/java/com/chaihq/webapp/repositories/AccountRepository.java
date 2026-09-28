package com.chaihq.webapp.repositories;

import com.chaihq.webapp.models.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {
}
