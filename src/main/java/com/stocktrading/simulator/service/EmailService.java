package com.stocktrading.simulator.service;

import com.stocktrading.simulator.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends account emails. Async so response time does not reveal whether an address is registered.
 * Tokens appear only in the email body, never in logs. If SMTP is not configured (MAIL_HOST empty)
 * nothing is sent and a warning is logged.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;
    private final String baseUrl;

    public EmailService(ObjectProvider<JavaMailSender> mailSender,
                        @Value("${app.mail.from:no-reply@stocksim.local}") String from,
                        @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Async
    public void sendVerification(User user, String rawToken) {
        send(user.getEmail(), "Verify your StockSim email",
            "Hi " + user.getName() + ",\n\n"
                + "Confirm your email address to finish setting up StockSim:\n"
                + baseUrl + "/verify-email.html?token=" + rawToken + "\n\n"
                + "This link works once and expires in 24 hours. If you did not create an account, ignore this email.");
    }

    @Async
    public void sendPasswordReset(User user, String rawToken) {
        send(user.getEmail(), "Reset your StockSim password",
            "Hi " + user.getName() + ",\n\n"
                + "Choose a new password here:\n"
                + baseUrl + "/reset-password.html?token=" + rawToken + "\n\n"
                + "This link works once and expires in 30 minutes. If you did not ask for this, ignore this email.");
    }

    private void send(String to, String subject, String body) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("Mail is not configured (set MAIL_HOST); not sending '{}'", subject);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
        } catch (MailException e) {
            // Class name only: the exception message can echo addresses or credentials.
            log.error("Failed to send '{}' email: {}", subject, e.getClass().getSimpleName());
        }
    }
}
