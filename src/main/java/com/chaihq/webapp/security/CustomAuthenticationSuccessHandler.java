package com.chaihq.webapp.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.chaihq.webapp.models.CustomUserDetails;
import com.chaihq.webapp.models.User;
import com.chaihq.webapp.repositories.UserRepository;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.logging.Logger;

@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger logger = Logger.getLogger(CustomAuthenticationSuccessHandler.class.getName());

    @Autowired
    private UserRepository userRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        try {
            Object principal = authentication.getPrincipal();

            if (principal instanceof CustomUserDetails) {
                CustomUserDetails userDetails = (CustomUserDetails) principal;
                User user = userDetails.getUser();

                if (user != null) {
                    HttpSession session = request.getSession();
                    session.setAttribute("currentUser", user);
                    logger.info("Added currentUser to session: " + user.getFirstName() + " " + user.getLastName());
                }
            } else if (principal instanceof String) {
                // For remember-me or other auth mechanisms that return String username
                String username = (String) principal;
                User user = userRepository.findByEmail(username);
                if (user != null) {
                    HttpSession session = request.getSession();
                    session.setAttribute("currentUser", user);
                    logger.info("Added currentUser to session (from username): " + user.getFirstName() + " " + user.getLastName());
                }
            }
        } catch (Exception e) {
            logger.warning("Error in CustomAuthenticationSuccessHandler: " + e.getMessage());
            e.printStackTrace();
        }

        // Redirect to default target URL
        response.sendRedirect(request.getContextPath() + "/projects");
    }
}
