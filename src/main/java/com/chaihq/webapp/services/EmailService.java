package com.chaihq.webapp.services;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import com.chaihq.webapp.models.User;

@Service
public class EmailService {
     @Value("${passwordless.email.from}")
    private String from;

    @Value("${spring.mail.host}")
    private String host;

    @Value("${spring.mail.username}")
    private String username;

    @Value("${spring.mail.password}")
    private String password;

    @Value("${host.url}")
    String url;

    private static Logger log = LoggerFactory.getLogger(EmailService.class);

    // The token is passed separately: only its hash is stored on the user
    @Async
    public void sendEmail(User user, String token) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(587);
        mailSender.setUsername(username);
        mailSender.setPassword(password);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        String link = url + "/verify-token-and-login?email=" + URLEncoder.encode(user.getEmail(), StandardCharsets.UTF_8)
                + "&token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(user.getEmail());
        message.setSubject("Chai login magic link");
        message.setText("Please use this link to login: \n\n" + link);
        // Only at DEBUG: anyone who can read the log could use the link to sign in
        log.debug("Login link for user {}: {}", user.getId(), link);
        try {
            mailSender.send(message);
        } catch (RuntimeException e) {
            log.error("Could not send login email to user {}: {}", user.getId(), e.getMessage());
        }
    }
}