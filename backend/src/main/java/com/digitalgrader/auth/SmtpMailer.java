package com.digitalgrader.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** Delivers through any SMTP provider (AWS SES, Brevo, Mailgun) using plain settings. */
public class SmtpMailer implements Mailer {
    private static final Logger log = LoggerFactory.getLogger(SmtpMailer.class);
    private final JavaMailSender sender;
    private final String from;

    public SmtpMailer(JavaMailSender sender, String from) { this.sender = sender; this.from = from; }

    @Override
    public void send(String to, String subject, String text) {
        try {
            var message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            sender.send(message);
        } catch (RuntimeException error) {
            log.warn("Mail to {} could not be sent", to, error);
        }
    }
}
