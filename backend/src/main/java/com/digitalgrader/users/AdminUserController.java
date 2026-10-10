package com.digitalgrader.users;

import com.digitalgrader.auth.AuthRequests;
import com.digitalgrader.auth.RegistrationService;
import com.digitalgrader.common.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private final RegistrationService registration;
    public AdminUserController(RegistrationService registration) { this.registration = registration; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public User.Profile create(@Valid @RequestBody AuthRequests.Register request) {
        if (request.role() == Role.ADMIN) throw new ApiException(HttpStatus.FORBIDDEN, "Admin creation is unavailable through this endpoint");
        return registration.create(request);
    }
}
