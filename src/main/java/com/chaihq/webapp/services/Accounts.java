package com.chaihq.webapp.services;

import com.chaihq.webapp.models.Account;
import com.chaihq.webapp.models.AccountMember;
import com.chaihq.webapp.models.Project;
import com.chaihq.webapp.models.User;
import com.chaihq.webapp.repositories.AccountMemberRepository;
import com.chaihq.webapp.repositories.AccountRepository;
import com.chaihq.webapp.repositories.ProjectRepository;
import com.chaihq.webapp.repositories.UserRepository;
import com.chaihq.webapp.utilities.Constants;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.List;
import java.util.Objects;

/**
 * Accounts (companies) and people's roles in them. Someone can belong to several accounts;
 * the one they're working in is kept in their session, like switching accounts in Basecamp,
 * and remembered on the user so a new session opens in the same one.
 */
@Service
public class Accounts {

    private static final String CURRENT_ACCOUNT_ID = "currentAccountId";

    private final AccountRepository accountRepository;
    private final AccountMemberRepository memberRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public Accounts(AccountRepository accountRepository, AccountMemberRepository memberRepository,
                    ProjectRepository projectRepository, UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.memberRepository = memberRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
    }

    public AccountMember membership(Account account, User user) {
        if (account == null || user == null) {
            return null;
        }
        return memberRepository.findFirstByAccountAndUser(account, user).orElse(null);
    }

    public String role(Account account, User user) {
        AccountMember membership = membership(account, user);
        return membership == null ? null : membership.getRole();
    }

    /** Owners and admins manage the account: people, invitations and every project in it. */
    public boolean isAdmin(Account account, User user) {
        String role = role(account, user);
        return Constants.ROLE_OWNER.equals(role) || Constants.ROLE_ADMIN.equals(role);
    }

    public boolean isOwner(Account account, User user) {
        return Constants.ROLE_OWNER.equals(role(account, user));
    }

    public List<AccountMember> memberships(User user) {
        return memberRepository.findByUserOrderByAccountNameAsc(user);
    }

    public List<AccountMember> people(Account account) {
        return memberRepository.findByAccountOrderByUserFirstNameAscUserLastNameAsc(account);
    }

    /**
     * The account this person is working in: the one in the session, else the one they last
     * worked in (a new session), else their first account if that's gone (or they were removed
     * from it); null if they belong to none.
     */
    public Account current(HttpSession session, User user) {
        AccountMember membership = null;
        if (session.getAttribute(CURRENT_ACCOUNT_ID) instanceof Long accountId) {
            membership = membership(accountId, user);
        }
        if (membership == null && user != null && user.getLastAccountId() != null) {
            membership = membership(user.getLastAccountId(), user);
        }
        if (membership == null) {
            List<AccountMember> all = memberships(user);
            membership = all.isEmpty() ? null : all.get(0);
        }
        remember(session, user, membership);
        return membership == null ? null : membership.getAccount();
    }

    /** Switch to another of this person's accounts. Returns false if they don't belong to it. */
    public boolean switchTo(HttpSession session, User user, long accountId) {
        AccountMember membership = membership(accountId, user);
        if (membership == null) {
            return false;
        }
        remember(session, user, membership);
        return true;
    }

    private AccountMember membership(long accountId, User user) {
        return accountRepository.findById(accountId).map(account -> membership(account, user)).orElse(null);
    }

    private void remember(HttpSession session, User user, AccountMember membership) {
        Long accountId = membership == null ? null : membership.getAccount().getId();
        // Runs on every request, so only write when it changes
        if (user != null && !Objects.equals(user.getLastAccountId(), accountId)) {
            userRepository.updateLastAccountId(user.getId(), accountId);
            user.setLastAccountId(accountId);
        }

        if (membership == null) {
            session.removeAttribute(CURRENT_ACCOUNT_ID);
            session.removeAttribute(Constants.CURRENT_ACCOUNT);
            session.removeAttribute(Constants.CURRENT_ACCOUNT_ROLE);
        } else {
            session.setAttribute(CURRENT_ACCOUNT_ID, membership.getAccount().getId());
            session.setAttribute(Constants.CURRENT_ACCOUNT, membership.getAccount());
            session.setAttribute(Constants.CURRENT_ACCOUNT_ROLE, membership.getRole());
        }
    }

    /** A new account, with its creator as owner. */
    public Account create(String name, User owner) {
        Account account = new Account();
        account.setName(name.trim());
        account.setCreatedBy(owner);
        account.setCreatedAt(Calendar.getInstance());
        accountRepository.save(account);
        addPerson(account, owner, Constants.ROLE_OWNER);
        return account;
    }

    /** Adds someone to an account; if they're already in it, their role is left alone. */
    public AccountMember addPerson(Account account, User user, String role) {
        AccountMember existing = membership(account, user);
        if (existing != null) {
            return existing;
        }
        AccountMember membership = new AccountMember();
        membership.setAccount(account);
        membership.setUser(user);
        membership.setRole(role);
        membership.setCreatedAt(Calendar.getInstance());
        return memberRepository.save(membership);
    }

    /** Projects this person sees in the account: all of them for owners and admins, otherwise the ones they're on. */
    public List<Project> visibleProjects(Account account, User user) {
        if (account == null) {
            return List.of();
        }
        List<Project> projects = isAdmin(account, user)
                ? projectRepository.findByAccountOrderByNameAsc(account)
                : projectRepository.findDistinctByAccountAndUsersContainingOrderByNameAsc(account, user);
        return projects.stream().filter(project -> !Constants.DELETED.equals(project.getStatus())).toList();
    }
}
