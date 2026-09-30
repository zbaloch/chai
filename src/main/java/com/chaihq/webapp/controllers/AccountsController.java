package com.chaihq.webapp.controllers;

import com.chaihq.webapp.utilities.Paths;
import com.chaihq.webapp.models.*;
import com.chaihq.webapp.repositories.AccountMemberRepository;
import com.chaihq.webapp.repositories.AccountRepository;
import com.chaihq.webapp.repositories.InvitationRepository;
import com.chaihq.webapp.repositories.ProjectRepository;
import com.chaihq.webapp.services.Accounts;
import com.chaihq.webapp.services.EmailService;
import com.chaihq.webapp.services.ProjectAccess;
import com.chaihq.webapp.utilities.Constants;
import com.chaihq.webapp.utilities.Tokens;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Calendar;
import java.util.Set;

/**
 * Accounts (companies) and their people — Chai's version of Basecamp's Adminland.
 * Owners and admins invite people, set roles and remove people; everyone can see who's in the account.
 */
@Controller
public class AccountsController {

    private static final Logger log = LoggerFactory.getLogger(AccountsController.class);
    private static final Set<String> ROLES = Set.of(Constants.ROLE_OWNER, Constants.ROLE_ADMIN, Constants.ROLE_MEMBER);

    private final Accounts accounts;
    private final ProjectAccess projectAccess;
    private final AccountRepository accountRepository;
    private final AccountMemberRepository memberRepository;
    private final InvitationRepository invitationRepository;
    private final ProjectRepository projectRepository;
    private final EmailService emailService;

    public AccountsController(Accounts accounts, ProjectAccess projectAccess, AccountRepository accountRepository,
                              AccountMemberRepository memberRepository, InvitationRepository invitationRepository,
                              ProjectRepository projectRepository, EmailService emailService) {
        this.accounts = accounts;
        this.projectAccess = projectAccess;
        this.accountRepository = accountRepository;
        this.memberRepository = memberRepository;
        this.invitationRepository = invitationRepository;
        this.projectRepository = projectRepository;
        this.emailService = emailService;
    }

    // Every account you're in (Basecamp's launchpad)
    @GetMapping("/accounts")
    public String index(Model model, HttpSession session) {
        User currentUser = projectAccess.currentUser();
        model.addAttribute("memberships", accounts.memberships(currentUser));
        model.addAttribute("current", accounts.current(session, currentUser));
        return "accounts/index";
    }

    @PostMapping("/accounts/{id}/switch")
    public String switchAccount(@PathVariable long id, HttpSession session) {
        User currentUser = projectAccess.currentUser();
        if (!accounts.switchTo(session, currentUser, id)) {
            throw projectAccess.denied(currentUser, "account", id);
        }
        return "redirect:" + Paths.account(id) + "/projects";
    }

    @GetMapping("/accounts/new")
    public String newAccount() {
        return "accounts/new";
    }

    @PostMapping("/accounts/new")
    public String createAccount(@RequestParam(value = "name", required = false) String name, Model model,
                                HttpSession session, RedirectAttributes redirectAttributes) {
        if (name == null || name.isBlank()) {
            model.addAttribute("error", "Give your account a name.");
            return "accounts/new";
        }
        User currentUser = projectAccess.currentUser();
        Account account = accounts.create(name, currentUser);
        accounts.switchTo(session, currentUser, account.getId());
        redirectAttributes.addFlashAttribute("notice", name.trim() + " is ready. Start a project or invite your team.");
        return "redirect:" + Paths.home(account);
    }

    @GetMapping(Paths.ACCOUNT + "/people")
    public String people(Model model, HttpSession session) {
        User currentUser = projectAccess.currentUser();
        Account account = requireCurrent(session, currentUser);
        showPeople(account, currentUser, model);
        return "account/people";
    }

    @PostMapping(Paths.ACCOUNT + "/invitations")
    public String invite(@RequestParam(value = "email", required = false) String emailParam,
                         @RequestParam(value = "role", required = false) String role,
                         Model model, HttpSession session) {
        User currentUser = projectAccess.currentUser();
        Account account = requireAdmin(session, currentUser);
        final String email = emailParam == null ? "" : emailParam.trim().toLowerCase();
        role = Constants.ROLE_ADMIN.equals(role) ? Constants.ROLE_ADMIN : Constants.ROLE_MEMBER;

        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            model.addAttribute("error", "Enter a valid email address.");
        } else if (accounts.people(account).stream().anyMatch(m -> m.getUser().getEmail().equalsIgnoreCase(email))) {
            model.addAttribute("error", "That person is already in " + account.getName() + ".");
        } else {
            // One live invitation per email: a new one replaces any earlier one
            invitationRepository.deleteAll(invitationRepository.findByAccountAndEmailIgnoreCaseAndAcceptedAtIsNull(account, email));

            String token = Tokens.generate();
            Invitation invitation = new Invitation();
            invitation.setAccount(account);
            invitation.setEmail(email);
            invitation.setRole(role);
            invitation.setTokenHash(Tokens.hash(token));
            invitation.setInvitedBy(currentUser);
            invitation.setCreatedAt(Calendar.getInstance());
            Calendar expires = Calendar.getInstance();
            expires.add(Calendar.DAY_OF_MONTH, 14);
            invitation.setExpiresAt(expires);
            invitationRepository.save(invitation);

            emailService.sendInvitation(email, currentUser.getFirstName() + " " + currentUser.getLastName(), account.getName(), token);
            log.info("User {} invited someone to account {}", currentUser.getId(), account.getId());
            model.addAttribute("message", "Invitation sent to " + email + ".");
        }

        showPeople(account, currentUser, model);
        return "account/people";
    }

    @PostMapping(Paths.ACCOUNT + "/invitations/{id}/cancel")
    public String cancelInvitation(@PathVariable long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User currentUser = projectAccess.currentUser();
        Account account = requireAdmin(session, currentUser);
        Invitation invitation = invitationRepository.findById(id).orElse(null);
        if (invitation == null || invitation.getAccount().getId() != account.getId()) {
            throw projectAccess.denied(currentUser, "invitation", id);
        }
        invitationRepository.delete(invitation);
        redirectAttributes.addFlashAttribute("notice", "Invitation to " + invitation.getEmail() + " cancelled.");
        return "redirect:" + Paths.account(account) + "/people";
    }

    @PostMapping(Paths.ACCOUNT + "/people/{memberId}/role")
    public String changeRole(@PathVariable long memberId, @RequestParam("role") String role,
                             HttpSession session, RedirectAttributes redirectAttributes) {
        User currentUser = projectAccess.currentUser();
        Account account = requireAdmin(session, currentUser);
        AccountMember membership = membershipIn(account, memberId, currentUser);

        boolean touchesOwner = Constants.ROLE_OWNER.equals(role) || Constants.ROLE_OWNER.equals(membership.getRole());
        if (!ROLES.contains(role)) {
            throw projectAccess.denied(currentUser, "role", role);
        } else if (touchesOwner && !accounts.isOwner(account, currentUser)) {
            redirectAttributes.addFlashAttribute("destruction_notice", "Only owners can add or remove owners.");
        } else if (isLastOwner(account, membership) && !Constants.ROLE_OWNER.equals(role)) {
            redirectAttributes.addFlashAttribute("destruction_notice", "An account needs at least one owner.");
        } else {
            membership.setRole(role);
            memberRepository.save(membership);
            redirectAttributes.addFlashAttribute("notice", membership.getUser().getFirstName() + " is now " + roleName(role) + ".");
        }
        return "redirect:" + Paths.account(account) + "/people";
    }

    // Admins remove people; anyone can leave. Leaving or removal also takes them off the account's projects.
    @PostMapping(Paths.ACCOUNT + "/people/{memberId}/remove")
    public String remove(@PathVariable long memberId, HttpSession session, RedirectAttributes redirectAttributes) {
        User currentUser = projectAccess.currentUser();
        Account account = requireCurrent(session, currentUser);
        AccountMember membership = membershipIn(account, memberId, currentUser);
        boolean leaving = membership.getUser().getId() == currentUser.getId();

        if (!leaving && !accounts.isAdmin(account, currentUser)) {
            throw projectAccess.denied(currentUser, "account member", memberId);
        }
        if (Constants.ROLE_OWNER.equals(membership.getRole()) && !leaving && !accounts.isOwner(account, currentUser)) {
            redirectAttributes.addFlashAttribute("destruction_notice", "Only owners can remove an owner.");
            return "redirect:" + Paths.account(account) + "/people";
        }
        if (isLastOwner(account, membership)) {
            redirectAttributes.addFlashAttribute("destruction_notice", "An account needs at least one owner. Make someone else an owner first.");
            return "redirect:" + Paths.account(account) + "/people";
        }

        User person = membership.getUser();
        for (Project project : projectRepository.findByAccountOrderByNameAsc(account)) {
            if (project.getUsers().removeIf(user -> user.getId() == person.getId())) {
                projectRepository.save(project);
            }
        }
        memberRepository.delete(membership);

        if (leaving) {
            redirectAttributes.addFlashAttribute("notice", "You left " + account.getName() + ".");
            return "redirect:/accounts";
        }
        redirectAttributes.addFlashAttribute("notice", person.getFirstName() + " was removed from " + account.getName() + ".");
        return "redirect:" + Paths.account(account) + "/people";
    }

    @GetMapping(Paths.ACCOUNT + "/settings")
    public String edit(Model model, HttpSession session) {
        model.addAttribute("account", requireAdmin(session, projectAccess.currentUser()));
        return "account/edit";
    }

    @PostMapping(Paths.ACCOUNT + "/settings")
    public String update(@RequestParam(value = "name", required = false) String name, Model model,
                         HttpSession session, RedirectAttributes redirectAttributes) {
        User currentUser = projectAccess.currentUser();
        Account account = requireAdmin(session, currentUser);
        if (name == null || name.isBlank()) {
            model.addAttribute("account", account);
            model.addAttribute("error", "Give your account a name.");
            return "account/edit";
        }
        account.setName(name.trim());
        accountRepository.save(account);
        accounts.switchTo(session, currentUser, account.getId());
        redirectAttributes.addFlashAttribute("notice", "Account renamed to " + account.getName() + ".");
        return "redirect:" + Paths.account(account) + "/people";
    }

    private void showPeople(Account account, User currentUser, Model model) {
        model.addAttribute("account", account);
        model.addAttribute("people", accounts.people(account));
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("isAdmin", accounts.isAdmin(account, currentUser));
        model.addAttribute("isOwner", accounts.isOwner(account, currentUser));
        model.addAttribute("now", Calendar.getInstance());
        model.addAttribute("invitations", accounts.isAdmin(account, currentUser)
                ? invitationRepository.findByAccountAndAcceptedAtIsNullOrderByCreatedAtDesc(account)
                : java.util.List.of());
    }

    private Account requireCurrent(HttpSession session, User user) {
        Account account = accounts.current(session, user);
        if (account == null) {
            throw projectAccess.denied(user, "account", null);
        }
        return account;
    }

    private Account requireAdmin(HttpSession session, User user) {
        Account account = requireCurrent(session, user);
        if (!accounts.isAdmin(account, user)) {
            throw projectAccess.denied(user, "account administration", account.getId());
        }
        return account;
    }

    private AccountMember membershipIn(Account account, long memberId, User user) {
        AccountMember membership = memberRepository.findById(memberId).orElse(null);
        if (membership == null || membership.getAccount().getId() != account.getId()) {
            throw projectAccess.denied(user, "account member", memberId);
        }
        return membership;
    }

    private boolean isLastOwner(Account account, AccountMember membership) {
        return Constants.ROLE_OWNER.equals(membership.getRole())
                && memberRepository.countByAccountAndRole(account, Constants.ROLE_OWNER) <= 1;
    }

    private static String roleName(String role) {
        return switch (role) {
            case Constants.ROLE_OWNER -> "an owner";
            case Constants.ROLE_ADMIN -> "an admin";
            default -> "a member";
        };
    }
}
