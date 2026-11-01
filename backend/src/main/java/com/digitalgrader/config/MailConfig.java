package com.digitalgrader.config;

import java.util.Properties;
import com.digitalgrader.auth.Mailer;
import com.digitalgrader.auth.LogMailer;
import com.digitalgrader.auth.SmtpMailer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
public class MailConfig {
    @Bean
    Mailer mailer(@Value("${SMTP_HOST:}") String host,
            @Value("${SMTP_PORT:587}") int port,
            @Value("${SMTP_USERNAME:}") String username,
            @Value("${SMTP_PASSWORD:}") String password,
            @Value("${SMTP_FROM:}") String from) {
        if (host.isBlank()) return new LogMailer();
        var sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        if (!username.isBlank()) { sender.setUsername(username); sender.setPassword(password); }
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.transport.protocol", "smtp");
        properties.put("mail.smtp.auth", Boolean.toString(!username.isBlank()));
        properties.put("mail.smtp.starttls.enable", "true");
        return new SmtpMailer(sender, from.isBlank() ? username : from);
    }
}
