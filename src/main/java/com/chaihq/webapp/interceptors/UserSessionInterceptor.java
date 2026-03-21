package com.chaihq.webapp.interceptors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.chaihq.webapp.models.CustomUserDetails;
import com.chaihq.webapp.models.User;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.logging.Logger;

@Component
public class UserSessionInterceptor implements HandlerInterceptor {

    private static final Logger logger = Logger.getLogger(UserSessionInterceptor.class.getName());

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null && authentication.isAuthenticated()) {
                Object principal = authentication.getPrincipal();

                if (principal instanceof CustomUserDetails) {
                    CustomUserDetails userDetails = (CustomUserDetails) principal;
                    User user = userDetails.getUser();

                    if (user != null) {
                        HttpSession session = request.getSession();
                        session.setAttribute("currentUser", user);
                        logger.info("Set currentUser in session: " + user.getFirstName() + " " + user.getLastName());
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Error in UserSessionInterceptor: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }
}
