package com.digitalgrader.auth;

import java.time.Duration;
import com.digitalgrader.common.ApiException;
import com.digitalgrader.users.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class RegistrationService {
    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final EmailTokenService emailTokens;
    private final Mailer mailer;
    private final String baseUrl;

    public RegistrationService(UserRepository users, PasswordEncoder passwords, EmailTokenService emailTokens,
            Mailer mailer, @Value("${APP_BASE_URL:http://127.0.0.1:5176}") String baseUrl) {
        this.users = users; this.passwords = passwords; this.emailTokens = emailTokens; this.mailer = mailer;
        this.baseUrl = baseUrl;
    }

    public User.Profile register(AuthRequests.Register request) {
        if (request.role() != Role.STUDENT) throw new ApiException(HttpStatus.FORBIDDEN, "Only students can self-register");
        return create(request, false);
    }
    public User.Profile create(AuthRequests.Register request, boolean emailVerified) {
        try {
            var user = users.create(request.email(), passwords.encode(request.password()), request.fullName(), request.role(), emailVerified);
            if (!emailVerified) sendVerification(user);
            return user.profile();
        } catch (DuplicateKeyException error) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }
    }
    public void resendVerification(String email) {
        users.findByEmail(email).ifPresent(user -> {
            if (!user.emailVerified()) sendVerification(user);
        });
    }
    public void sendVerification(User user) {
        try {
            String token = emailTokens.issue(user.id(), EmailTokenService.Purpose.VERIFY_EMAIL, Duration.ofHours(24));
            mailer.send(user.email(), "Verify your Digital Grader email",
                    "Hello " + user.fullName() + ",\n\nConfirm this email address for your Digital Grader account by opening this link:\n\n"
                            + baseUrl + "/#/verify-email?token=" + token + "\n\nThe link expires in 24 hours. If you did not create this account, ignore this message.");
        } catch (RuntimeException error) {
            log.warn("Verification mail to {} could not be sent", user.email(), error);
        }
    }
}
