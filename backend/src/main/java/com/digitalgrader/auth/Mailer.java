package com.digitalgrader.auth;

/** Transactional mail delivery. Implementations must not throw for routine send failures. */
public interface Mailer {
    void send(String to, String subject, String text);
}
