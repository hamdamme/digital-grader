package com.digitalgrader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.digitalgrader.auth.AuthRequests;
import com.digitalgrader.auth.RegistrationService;
import com.digitalgrader.users.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegistrationTest {
    @Test void rejectsPrivilegedSelfRegistrationBeforePersistence() {
        var users = mock(UserRepository.class);
        var service = new RegistrationService(users, new BCryptPasswordEncoder(10));
        for (Role role : new Role[]{Role.ADMIN, Role.INSTRUCTOR})
            assertThatThrownBy(() -> service.register(new AuthRequests.Register("a@example.org", "long-password-123", "Test", role)))
                    .hasMessage("Only students can self-register");
        verifyNoInteractions(users);
    }
    @Test void validatesUtf8PasswordLimitAndRequiredFields() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new AuthRequests.Register("a@example.org", "é".repeat(37), "Test", Role.STUDENT))).isNotEmpty();
            assertThat(validator.validate(new AuthRequests.Register("bad", "short", " ", null))).hasSize(4);
            assertThat(validator.validate(new AuthRequests.Register("a@example.org", "good-password-123", "Test", Role.STUDENT))).isEmpty();
        }
    }
    @Test void hashesPasswordBeforePersistence() {
        var users = mock(UserRepository.class);
        var encoder = new BCryptPasswordEncoder(10);
        when(users.create(anyString(), anyString(), anyString(), any())).thenAnswer(call -> {
            String hash = call.getArgument(1);
            assertThat(hash).startsWith("$2a$10$");
            assertThat(encoder.matches("long-password-123", hash)).isTrue();
            return new User(1, "a@example.org", hash, "Test", Role.STUDENT);
        });
        var profile = new RegistrationService(users, encoder).register(new AuthRequests.Register("a@example.org", "long-password-123", "Test", Role.STUDENT));
        assertThat(profile.role()).isEqualTo(Role.STUDENT);
    }
    @Test void userSerializationNeverIncludesPasswordHash() throws Exception {
        String json = new ObjectMapper().writeValueAsString(new User(1, "a@example.org", "private-hash", "Test", Role.STUDENT));
        assertThat(json).doesNotContain("private-hash", "passwordHash");
    }
}
