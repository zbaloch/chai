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
        String link = url + "/verify-token-and-login?email=" + URLEncoder.encode(user.getEmail(), StandardCharsets.UTF_8)
                + "&token=" + token;
        // Only at DEBUG: anyone who can read the log could use the link to sign in
        log.debug("Login link for user {}: {}", user.getId(), link);
        send(user.getEmail(), "Chai login magic link", "Please use this link to login: \n\n" + link);
    }

    @Async
    public void sendInvitation(String email, String inviterName, String accountName, String token) {
        String link = url + "/invitations/" + token;
        log.debug("Invitation link for {}: {}", email, link);
        send(email, inviterName + " invited you to join " + accountName + " on Chai",
                inviterName + " invited you to join " + accountName + " on Chai.\n\n"
                        + "Accept the invitation here: \n\n" + link + "\n\nThe link works for 14 days.");
    }

    private void send(String to, String subject, String text) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(587);
        mailSender.setUsername(username);
        mailSender.setPassword(password);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            mailSender.send(message);
        } catch (RuntimeException e) {
            log.error("Could not send email \"{}\": {}", subject, e.getMessage());
        }
    }
}