package com.chaihq.webapp;

import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.security.web.SecurityFilterChain;

import com.chaihq.webapp.services.CustomUserDetailsService;
import com.chaihq.webapp.security.CustomAuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    private final CustomAuthenticationSuccessHandler authenticationSuccessHandler;

    @Value("${security.remember-me.key:chai-remember-me-key}")
    private String rememberMeKey;

    @Value("${security.remember-me.token-validity-seconds:2592000}")
    private int rememberMeTokenValiditySeconds;

    public WebSecurityConfig(CustomAuthenticationSuccessHandler authenticationSuccessHandler) {
        this.authenticationSuccessHandler = authenticationSuccessHandler;
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return new CustomUserDetailsService();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Scoped to the filter chain below (not exposed as a global @Bean) so that the
    // configured UserDetailsService is still used for username/password login.
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService());
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authenticationProvider(authenticationProvider())
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
                .requestMatchers("/registration", "/js/*", "/css/*", "/trix/*", "/login-magic", "/login", "/verify-token-and-login").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(authenticationSuccessHandler)
                .permitAll()
            )
            .logout(logout -> logout.permitAll())
            .rememberMe(rememberMe -> rememberMe
                .rememberMeServices(rememberMeServices())
                .key(rememberMeKey)
                .tokenValiditySeconds(rememberMeTokenValiditySeconds)
            )
            .cors(cors -> cors.disable())
            .csrf(csrf -> csrf.disable());

        return http.build();
    }

    @Bean
    public RememberMeServices rememberMeServices() {
        TokenBasedRememberMeServices services =
                new TokenBasedRememberMeServices(rememberMeKey, userDetailsService());
        services.setAlwaysRemember(true);
        services.setTokenValiditySeconds(rememberMeTokenValiditySeconds);
        return services;
    }

}
