package com.digitalgrader.auth;

import com.digitalgrader.users.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final RegistrationService registration;
    private final AuthService auth;
    public AuthController(RegistrationService registration, AuthService auth) { this.registration = registration; this.auth = auth; }
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public User.Profile register(@Valid @RequestBody AuthRequests.Register request) { return registration.register(request); }
    @PostMapping("/login")
    public AuthService.Tokens login(@Valid @RequestBody AuthRequests.Login request) { return auth.login(request); }
    @PostMapping("/refresh")
    public AuthService.Tokens refresh(@Valid @RequestBody AuthRequests.Refresh request) { return auth.refresh(request.refreshToken()); }
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody AuthRequests.Refresh request) { auth.logout(request.refreshToken()); }
    @GetMapping("/me")
    public User.Profile me(@AuthenticationPrincipal User.Profile user) { return user; }
}
