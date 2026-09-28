package com.chaihq.webapp.controllers;

import com.chaihq.webapp.models.Invitation;
import com.chaihq.webapp.models.User;
import com.chaihq.webapp.repositories.InvitationRepository;
import com.chaihq.webapp.repositories.UserRepository;
import com.chaihq.webapp.services.Accounts;
import com.chaihq.webapp.services.SignIn;
import com.chaihq.webapp.services.UserService;
import com.chaihq.webapp.utilities.Tokens;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Calendar;

/**
 * Accepting an invitation to an account. The link was emailed to the invited address, so opening it
 * proves that address: accepting creates the person's user if needed, adds them to the account and
 * signs them in — no separate sign-up or login link.
 */
@Controller
public class InvitationsController {

    private static final Logger log = LoggerFactory.getLogger(InvitationsController.class);

    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final Accounts accounts;
    private final SignIn signIn;

    public InvitationsController(InvitationRepository invitationRepository, UserRepository userRepository,
                                 UserService userService, Accounts accounts, SignIn signIn) {
        this.invitationRepository = invitationRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.accounts = accounts;
        this.signIn = signIn;
    }

    @GetMapping("/invitations/{token}")
    public String show(@PathVariable String token, Model model) {
        Invitation invitation = validInvitation(token);
        model.addAttribute("invitation", invitation);
        model.addAttribute("token", token);
        model.addAttribute("existingUser", invitation == null ? null : userRepository.findByEmail(invitation.getEmail()));
        return "invitations/show";
    }

    @PostMapping("/invitations/{token}")
    public String accept(@PathVariable String token,
                         @RequestParam(value = "firstName", required = false) String firstName,
                         @RequestParam(value = "lastName", required = false) String lastName,
                         Model model, HttpServletRequest request, HttpServletResponse response,
                         RedirectAttributes redirectAttributes) {
        Invitation invitation = validInvitation(token);
        if (invitation == null) {
            return "invitations/show";
        }

        User user = userRepository.findByEmail(invitation.getEmail());
        if (user == null) {
            if (firstName == null || firstName.isBlank() || lastName == null || lastName.isBlank()) {
                model.addAttribute("invitation", invitation);
                model.addAttribute("token", token);
                model.addAttribute("error", "Enter your first and last name.");
                return "invitations/show";
            }
            user = new User();
            user.setFirstName(firstName.trim());
            user.setLastName(lastName.trim());
            user.setEmail(invitation.getEmail());
            userService.save(user);
        }

        accounts.addPerson(invitation.getAccount(), user, invitation.getRole());
        invitation.setAcceptedAt(Calendar.getInstance());
        invitationRepository.save(invitation);

        signIn.signIn(user, request, response);
        accounts.switchTo(request.getSession(), user, invitation.getAccount().getId());
        log.info("User {} joined account {} from an invitation", user.getId(), invitation.getAccount().getId());

        redirectAttributes.addFlashAttribute("notice", "Welcome to " + invitation.getAccount().getName() + "!");
        return "redirect:/projects";
    }

    // Unused, unexpired invitations only
    private Invitation validInvitation(String token) {
        if (token == null || !token.matches("[0-9a-f]{64}")) {
            return null;
        }
        Invitation invitation = invitationRepository.findByTokenHash(Tokens.hash(token)).orElse(null);
        if (invitation == null || invitation.getAcceptedAt() != null
                || invitation.getExpiresAt() == null || invitation.getExpiresAt().before(Calendar.getInstance())) {
            return null;
        }
        return invitation;
    }
}
