package com.digitalgrader.auth;

import com.digitalgrader.common.ApiException;
import com.digitalgrader.users.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class RegistrationService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    public RegistrationService(UserRepository users, PasswordEncoder passwords) { this.users = users; this.passwords = passwords; }
    public User.Profile register(AuthRequests.Register request) {
        if (request.role() != Role.STUDENT) throw new ApiException(HttpStatus.FORBIDDEN, "Only students can self-register");
        return create(request);
    }
    public User.Profile create(AuthRequests.Register request) {
        try {
            return users.create(request.email(), passwords.encode(request.password()), request.fullName(), request.role()).profile();
        } catch (DuplicateKeyException error) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }
    }
}
