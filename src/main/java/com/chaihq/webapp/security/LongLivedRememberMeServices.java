package com.chaihq.webapp.security;

import com.chaihq.webapp.models.User;
import com.chaihq.webapp.repositories.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;

/**
 * Keeps people signed in until they sign out, including across application restarts.
 *
 * The cookie is signed with the server key and a random per-user secret, since magic-link
 * accounts have no password (the stock service refuses to sign without one). It is re-issued
 * each time it logs someone back in, so active users never reach its expiry.
 */
public class LongLivedRememberMeServices extends TokenBasedRememberMeServices {

    private static final SecureRandom RANDOM = new SecureRandom();

    public LongLivedRememberMeServices(String key, UserRepository userRepository) {
        super(key, username -> loadUser(userRepository, username));
    }

    private static UserDetails loadUser(UserRepository userRepository, String email) {
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new UsernameNotFoundException("User not found");
        }
        if (user.getRememberSecret() == null) {
            byte[] secret = new byte[32];
            RANDOM.nextBytes(secret);
            user.setRememberSecret(HexFormat.of().formatHex(secret));
            userRepository.save(user);
        }
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getRememberSecret())
                .authorities(List.of())
                .build();
    }

    @Override
    protected UserDetails processAutoLoginCookie(String[] cookieTokens, HttpServletRequest request, HttpServletResponse response) {
        UserDetails user = super.processAutoLoginCookie(cookieTokens, request, response);
        onLoginSuccess(request, response, UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
        return user;
    }
}
