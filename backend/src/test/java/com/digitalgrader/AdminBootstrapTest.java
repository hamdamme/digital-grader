package com.digitalgrader;

import java.util.Optional;
import com.digitalgrader.auth.AdminBootstrap;
import com.digitalgrader.auth.RegistrationService;
import com.digitalgrader.users.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminBootstrapTest {
    @Test void disabledByDefault() {
        var users = mock(UserRepository.class);
        var registration = mock(RegistrationService.class);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            new AdminBootstrap(users, registration, factory.getValidator(), "", "", "").run(null);
        }
        verifyNoInteractions(users, registration);
    }
    @Test void rejectsPartialConfigWithoutPrintingPassword() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThatThrownBy(() -> new AdminBootstrap(mock(UserRepository.class), mock(RegistrationService.class),
                    factory.getValidator(), "", "private-password", "").run(null))
                    .hasMessageContaining("BOOTSTRAP_ADMIN_EMAIL").hasMessageNotContaining("private-password");
        }
    }
    @Test void createsAdminOnceAndNeverPromotesExistingStudent() {
        var users = mock(UserRepository.class);
        var registration = mock(RegistrationService.class);
        when(users.findByEmail("admin@fixture.local")).thenReturn(Optional.empty());
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var bootstrap = new AdminBootstrap(users, registration, factory.getValidator(), "admin@fixture.local", "fixture-password-123", "Admin");
            bootstrap.run(null);
            verify(registration).create(argThat(request -> request.role() == Role.ADMIN));
            clearInvocations(registration);
            when(users.findByEmail("admin@fixture.local")).thenReturn(Optional.of(new User(1, "admin@fixture.local", "hash", "Admin", Role.ADMIN)));
            bootstrap.run(null);
            verifyNoInteractions(registration);
            when(users.findByEmail("admin@fixture.local")).thenReturn(Optional.of(new User(1, "admin@fixture.local", "hash", "Student", Role.STUDENT)));
            assertThatThrownBy(() -> bootstrap.run(null)).hasMessageContaining("non-admin");
            verifyNoInteractions(registration);
        }
    }
}
