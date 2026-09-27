package com.chaihq.webapp.interceptors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.chaihq.webapp.models.User;
import com.chaihq.webapp.repositories.UserRepository;
import com.chaihq.webapp.utilities.Constants;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.logging.Logger;

@Component
public class CurrentUserSessionInterceptor implements HandlerInterceptor {

    private static final Logger logger = Logger.getLogger(CurrentUserSessionInterceptor.class.getName());

    @Autowired
    private UserRepository userRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        try {
            HttpSession session = request.getSession();

            // Controllers read the user from "current_user" and templates from "currentUser". A new
            // session (e.g. after a restart, signed back in by the remember-me cookie) has neither.
            if (session.getAttribute(Constants.CURRENT_USER) == null || session.getAttribute("currentUser") == null) {
                // Try to get authenticated user
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

                if (authentication != null && authentication.isAuthenticated()) {
                    String username = authentication.getName();

                    // Check if it's not "anonymousUser"
                    if (!"anonymousUser".equals(username)) {
                        // Fetch user from database
                        User user = userRepository.findByEmail(username);

                        if (user != null) {
                            user.setInitialFirstNameLastName(initial(user.getFirstName()) + initial(user.getLastName()));
                            session.setAttribute(Constants.CURRENT_USER, user);
                            session.setAttribute("currentUser", user);
                            logger.info("Loaded currentUser from database: " + user.getFirstName() + " " + user.getLastName());
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Error in CurrentUserSessionInterceptor: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    private static String initial(String name) {
        return name == null || name.isEmpty() ? "" : name.substring(0, 1);
    }
}
