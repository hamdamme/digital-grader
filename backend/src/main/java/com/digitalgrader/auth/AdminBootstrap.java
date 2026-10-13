package com.digitalgrader.auth;

import com.digitalgrader.users.*;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final UserRepository users;
    private final RegistrationService registration;
    private final Validator validator;
    private final String email, password, name;
    public AdminBootstrap(UserRepository users, RegistrationService registration, Validator validator,
            @Value("${BOOTSTRAP_ADMIN_EMAIL:}") String email, @Value("${BOOTSTRAP_ADMIN_PASSWORD:}") String password,
            @Value("${BOOTSTRAP_ADMIN_NAME:}") String name) {
        this.users = users; this.registration = registration; this.validator = validator;
        this.email = email; this.password = password; this.name = name;
    }
    @Override public void run(ApplicationArguments args) {
        if (email.isEmpty() && password.isEmpty() && name.isEmpty()) return;
        var request = new AuthRequests.Register(email, password, name, Role.ADMIN);
        if (!validator.validate(request).isEmpty())
            throw new IllegalStateException("Provide valid BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD and BOOTSTRAP_ADMIN_NAME");
        var existing = users.findByEmail(email);
        if (existing.isPresent()) {
            if (existing.get().role() != Role.ADMIN)
                throw new IllegalStateException("Bootstrap email already belongs to a non-admin account");
            return; // Never reset credentials or elevate an existing account on restart.
        }
        registration.create(request);
    }
}
