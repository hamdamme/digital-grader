package com.digitalgrader.auth;

import com.digitalgrader.users.User;
import com.digitalgrader.users.UserRepository;
import com.digitalgrader.common.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final RegistrationService registration;
    private final AuthService auth;
    private final UserRepository users;
    public AuthController(RegistrationService registration, AuthService auth, UserRepository users) {
        this.registration = registration; this.auth = auth; this.users = users;
    }
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public User.Profile register(@Valid @RequestBody AuthRequests.Register request) { return registration.register(request); }
    @PostMapping("/login")
    public AuthService.Tokens login(@Valid @RequestBody AuthRequests.Login request) { return auth.login(request); }
    @PostMapping("/refresh")
    public AuthService.Tokens refresh(@Valid @RequestBody AuthRequests.Refresh request) { return auth.refresh(request.refreshToken()); }
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody AuthRequests.Refresh request) { auth.logout(request.refreshToken()); }
    @PostMapping("/verify-email") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody AuthRequests.TokenOnly request) { auth.verifyEmail(request.token()); }
    @PostMapping("/resend-verification") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendVerification(@Valid @RequestBody AuthRequests.ForgotPassword request) {
        registration.resendVerification(request.email());
    }
    @PostMapping("/forgot-password") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgotPassword(@Valid @RequestBody AuthRequests.ForgotPassword request) { auth.forgotPassword(request.email()); }
    @PostMapping("/reset-password") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody AuthRequests.ResetPassword request) { auth.resetPassword(request.token(), request.password()); }
    @GetMapping("/me")
    public User.Profile me(@AuthenticationPrincipal User.Profile user) { return user; }
}
