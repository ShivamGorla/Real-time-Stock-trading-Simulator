package com.stocktrading.simulator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.Properties;

/**
 * SMTP from environment variables MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD. No sender bean
 * exists unless MAIL_HOST is set. Also turns on @Async for EmailService.
 */
@Configuration
@EnableAsync
public class MailConfig {

    @Bean
    @ConditionalOnExpression("!'${MAIL_HOST:}'.isEmpty()")
    public JavaMailSender javaMailSender(@Value("${MAIL_HOST}") String host,
                                         @Value("${MAIL_PORT:587}") int port,
                                         @Value("${MAIL_USERNAME:}") String username,
                                         @Value("${MAIL_PASSWORD:}") String password) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        Properties props = sender.getJavaMailProperties();
        if (!username.isEmpty()) {
            sender.setUsername(username);
            sender.setPassword(password);
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
        }
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        return sender;
    }
}
