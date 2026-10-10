package com.digitalgrader.auth;

import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.digitalgrader.users.Role;
import jakarta.validation.constraints.*;

public final class AuthRequests {
    private AuthRequests() {}
    public record Register(@NotBlank @Email @Size(max=255) String email,
            @NotBlank @Size(min=12, max=72) String password,
            @NotBlank @Size(max=255) String fullName, @NotNull Role role) {
        @JsonIgnore @AssertTrue
        public boolean isPasswordWithinBcryptLimit() {
            return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
        }
    }
    public record Login(@NotBlank @Email @Size(max=255) String email, @NotBlank @Size(max=72) String password) {
        @JsonIgnore @AssertTrue
        public boolean isPasswordWithinBcryptLimit() {
            return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
        }
    }
    public record Refresh(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String refreshToken) {}
}
