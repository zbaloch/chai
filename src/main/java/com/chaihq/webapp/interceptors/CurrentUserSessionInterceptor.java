package com.chaihq.webapp.interceptors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.chaihq.webapp.models.User;
import com.chaihq.webapp.repositories.UserRepository;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
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

            // Check if currentUser is already in session
            Object currentUserAttr = session.getAttribute("currentUser");

            if (currentUserAttr == null) {
                // Try to get authenticated user
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

                if (authentication != null && authentication.isAuthenticated()) {
                    String username = authentication.getName();

                    // Check if it's not "anonymousUser"
                    if (!"anonymousUser".equals(username)) {
                        // Fetch user from database
                        User user = userRepository.findByEmail(username);

                        if (user != null) {
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
}
