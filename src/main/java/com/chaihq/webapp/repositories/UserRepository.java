package com.chaihq.webapp.repositories;

import com.chaihq.webapp.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserRepository extends JpaRepository<User, Long> {
    public User findByEmail(String email);
    public User findByEmailAndToken(String email, String token);

    // Just this column: the User in the session may be stale, so saving it whole could undo other changes
    @Transactional
    @Modifying
    @Query("update users u set u.lastAccountId = :accountId where u.id = :userId")
    int updateLastAccountId(@Param("userId") long userId, @Param("accountId") Long accountId);
}
