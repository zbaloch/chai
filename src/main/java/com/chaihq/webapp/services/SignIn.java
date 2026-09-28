package com.chaihq.webapp.services;

import com.chaihq.webapp.models.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

import java.util.Collections;

/** Signs someone in after they've proven their email (a login link or an invitation link). */
@Service
public class SignIn {

    private final SecurityContextHolderStrategy strategy = SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();
    private final RememberMeServices rememberMeServices;

    public SignIn(RememberMeServices rememberMeServices) {
        this.rememberMeServices = rememberMeServices;
    }

    public void signIn(User user, HttpServletRequest request, HttpServletResponse response) {
        // New session id on login so a session id planted before login can't be reused
        request.getSession(true);
        request.changeSessionId();

        SecurityContext context = strategy.createEmptyContext();
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                user.getEmail(), null, Collections.emptyList());
        context.setAuthentication(authentication);
        strategy.setContext(context);
        contextRepository.saveContext(context, request, response);
        rememberMeServices.loginSuccess(request, response, authentication);

        request.getSession().setAttribute("currentUser", user);
    }
}
