package com.digitalgrader.auth;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Development and pilot default when no SMTP provider is configured. Logs the full message. */
public class LogMailer implements Mailer {
    private static final Logger log = LoggerFactory.getLogger(LogMailer.class);
    private final List<String> messages = new CopyOnWriteArrayList<>();

    @Override
    public void send(String to, String subject, String text) {
        String message = "To: " + to + "\nSubject: " + subject + "\n" + text;
        messages.add(message);
        log.info("Mail (no SMTP configured):\n{}", message);
    }

    /** Test hook: every message sent through this mailer, oldest first. */
    public List<String> sentMessages() { return List.copyOf(messages); }
    public void clear() { messages.clear(); }
}
