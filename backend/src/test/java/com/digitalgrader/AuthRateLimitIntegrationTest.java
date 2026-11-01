package com.digitalgrader;

import java.util.Map;
import com.digitalgrader.users.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties="digital-grader.dispatch.enabled=false")
class AuthRateLimitIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        properties.add("DB_URL", POSTGRES::getJdbcUrl);
        properties.add("DB_USERNAME", POSTGRES::getUsername);
        properties.add("DB_PASSWORD", POSTGRES::getPassword);
        properties.add("JWT_SECRET", () -> "fixture-secret-".repeat(6));
        properties.add("BOOTSTRAP_ADMIN_EMAIL", () -> "");
        properties.add("BOOTSTRAP_ADMIN_PASSWORD", () -> "");
        properties.add("BOOTSTRAP_ADMIN_NAME", () -> "");
        properties.add("CORS_ALLOWED_ORIGINS", () -> "http://127.0.0.1:5176");
        properties.add("RATE_LIMIT_LOGIN_CAPACITY", () -> "2");
        properties.add("RATE_LIMIT_LOGIN_WINDOW_SECONDS", () -> "60");
        properties.add("RATE_LIMIT_REGISTER_CAPACITY", () -> "1");
        properties.add("RATE_LIMIT_REGISTER_WINDOW_SECONDS", () -> "60");
        properties.add("RATE_LIMIT_REFRESH_CAPACITY", () -> "1");
        properties.add("RATE_LIMIT_REFRESH_WINDOW_SECONDS", () -> "60");
        properties.add("RATE_LIMIT_LOGOUT_CAPACITY", () -> "1");
        properties.add("RATE_LIMIT_LOGOUT_WINDOW_SECONDS", () -> "60");
    }
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired com.digitalgrader.auth.TokenService tokens;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    private static int testCounter;
    private String ip;
    @BeforeEach void clean() {
        jdbc.execute("TRUNCATE users CASCADE");
        ip = "198.51.100." + (++testCounter % 250);
    }

    @Test void loginIsThrottledPerIpWithRetryAfter() {
        fixture("student@fixture.local");
        Map<String, String> body = Map.of("email", "student@fixture.local", "password", "wrong-password");
        assertThat(post("/auth/login", body, null).getStatusCode().value()).isEqualTo(401);
        assertThat(post("/auth/login", body, null).getStatusCode().value()).isEqualTo(401);
        var blocked = post("/auth/login", body, null);
        assertThat(blocked.getStatusCode().value()).isEqualTo(429);
        assertThat(blocked.getHeaders().getFirst("Retry-After")).isEqualTo("60");
        assertThat(blocked.getBody()).contains("Too many requests");
    }
    @Test void throttlingCountsFailedAndSuccessfulAttemptsTogether() {
        fixture("student@fixture.local");
        Map<String, String> body = Map.of("email", "student@fixture.local", "password", "fixture-password-123");
        assertThat(post("/auth/login", body, null).getStatusCode().value()).isEqualTo(200);
        assertThat(post("/auth/login", body, null).getStatusCode().value()).isEqualTo(200);
        assertThat(post("/auth/login", body, null).getStatusCode().value()).isEqualTo(429);
    }
    @Test void limitsAreIndependentPerEndpointAndPerIp() {
        fixture("student@fixture.local");
        Map<String, String> login = Map.of("email", "student@fixture.local", "password", "wrong-password");
        for (int i = 0; i < 2; i++) assertThat(post("/auth/login", login, null).getStatusCode().value()).isEqualTo(401);
        // The register budget is separate, so the spent login budget does not block registration.
        assertThat(post("/auth/register", Map.of("email", "new@fixture.local", "password", "fixture-password-123",
                "fullName", "Fixture", "role", "STUDENT"), null).getStatusCode().value()).isEqualTo(201);
        assertThat(post("/auth/register", Map.of("email", "second@fixture.local", "password", "fixture-password-123",
                "fullName", "Fixture", "role", "STUDENT"), null).getStatusCode().value()).isEqualTo(429);
        // A different forwarded IP gets its own budget.
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Forwarded-For", "203.0.113.7");
        var foreign = http.exchange("/api/v1/auth/login", HttpMethod.POST, new HttpEntity<>(login, headers), String.class);
        assertThat(foreign.getStatusCode().value()).isEqualTo(401);
    }
    @Test void refreshAndLogoutAreThrottled() {
        fixture("student@fixture.local");
        Map<String, String> login = Map.of("email", "student@fixture.local", "password", "fixture-password-123");
        var session = post("/auth/login", login, null);
        assertThat(session.getStatusCode().value()).isEqualTo(200);
        String refreshToken = extractRefreshToken(session.getBody());
        Map<String, String> refresh = Map.of("refreshToken", refreshToken);
        assertThat(post("/auth/refresh", refresh, null).getStatusCode().value()).isEqualTo(200);
        assertThat(post("/auth/refresh", refresh, null).getStatusCode().value()).isEqualTo(429);
        assertThat(post("/auth/logout", refresh, null).getStatusCode().value()).isEqualTo(204);
        assertThat(post("/auth/logout", refresh, null).getStatusCode().value()).isEqualTo(429);
    }
    @Test void authenticatedEndpointsAreNotThrottledByThisFilter() {
        String token = sessionToken(fixture("student@fixture.local"));
        for (int i = 0; i < 3; i++)
            assertThat(get("/auth/me", token).getStatusCode().value()).isEqualTo(200);
    }
    private User fixture(String email) {
        return users.create(email, passwords.encode(PASSWORD), "Fixture", Role.STUDENT, true);
    }
    static final String PASSWORD = "fixture-password-123";
    private String sessionToken(User user) {
        String sessionHash = java.util.UUID.randomUUID().toString().replace("-", "").repeat(2);
        jdbc.update("INSERT INTO refresh_tokens(user_id,token_hash,expires_at) VALUES (?,?,now()+interval '7 days')", user.id(), sessionHash);
        return tokens.issue(user, sessionHash);
    }
    private String extractRefreshToken(String responseBody) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(responseBody).get("refreshToken").asText();
        } catch (Exception error) { throw new IllegalStateException(error); }
    }
    private ResponseEntity<String> post(String path, Object body, String token) { return request(HttpMethod.POST, path, body, token); }
    private ResponseEntity<String> get(String path, String token) { return request(HttpMethod.GET, path, null, token); }
    private ResponseEntity<String> request(HttpMethod method, String path, Object body, String token) {
        var headers = new HttpHeaders();
        headers.add("X-Forwarded-For", ip);
        if (token != null) headers.setBearerAuth(token);
        return http.exchange("/api/v1" + path, method, new HttpEntity<>(body, headers), String.class);
    }
}
