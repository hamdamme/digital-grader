package com.digitalgrader;

import java.util.Map;
import java.util.regex.Pattern;
import com.digitalgrader.auth.*;
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
class EmailVerificationIntegrationTest {
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
        properties.add("APP_BASE_URL", () -> "http://127.0.0.1:5176");
        properties.add("RATE_LIMIT_FORGOT_CAPACITY", () -> "20");
        properties.add("RATE_LIMIT_VERIFY_CAPACITY", () -> "20");
    }
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired Mailer mailer;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    @Autowired TokenService tokenService;
    static final String PASSWORD = "fixture-password-123";
    static final Pattern LINK = Pattern.compile("\\?token=([A-Za-z0-9_-]{43})");
    LogMailer post;
    @BeforeEach void clean() {
        jdbc.execute("TRUNCATE users CASCADE");
        post = (LogMailer) mailer;
        post.clear();
    }

    @Test void registrationSendsVerificationAndLoginIsGated() {
        assertThat(post("/auth/register", registration("student@fixture.local")).getStatusCode().value()).isEqualTo(201);
        String token = tokenFromLastMail("student@fixture.local");
        // Login is blocked until the email address is verified.
        assertThat(post("/auth/login", Map.of("email", "student@fixture.local", "password", PASSWORD)).getStatusCode().value()).isEqualTo(403);
        assertThat(post("/auth/verify-email", Map.of("token", token)).getStatusCode().value()).isEqualTo(204);
        assertThat(post("/auth/login", Map.of("email", "student@fixture.local", "password", PASSWORD)).getStatusCode().value()).isEqualTo(200);
        assertThat(users.findByEmail("student@fixture.local").orElseThrow().emailVerified()).isTrue();
    }
    @Test void verificationTokenIsSingleUseAndExpires() {
        post("/auth/register", registration("student@fixture.local"));
        String token = tokenFromLastMail("student@fixture.local");
        assertThat(post("/auth/verify-email", Map.of("token", token)).getStatusCode().value()).isEqualTo(204);
        assertThat(post("/auth/verify-email", Map.of("token", token)).getStatusCode().value()).isEqualTo(400);
        post("/auth/register", registration("second@fixture.local"));
        String other = tokenFromLastMail("second@fixture.local");
        jdbc.update("UPDATE email_tokens SET expires_at=now()-interval '1 second' WHERE token_hash=?", AuthService.hash(other));
        assertThat(post("/auth/verify-email", Map.of("token", other)).getStatusCode().value()).isEqualTo(400);
        assertThat(users.findByEmail("second@fixture.local").orElseThrow().emailVerified()).isFalse();
    }
    @Test void resendVerificationRotatesTheToken() {
        post("/auth/register", registration("student@fixture.local"));
        String first = tokenFromLastMail("student@fixture.local");
        assertThat(post("/auth/login", Map.of("email", "student@fixture.local", "password", PASSWORD)).getStatusCode().value()).isEqualTo(403);
        assertThat(post("/auth/resend-verification", Map.of("email", "student@fixture.local")).getStatusCode().value()).isEqualTo(204);
        // The old token no longer works; the fresh one does.
        assertThat(post("/auth/verify-email", Map.of("token", first)).getStatusCode().value()).isEqualTo(400);
        String second = tokenFromLastMail("student@fixture.local");
        assertThat(second).isNotEqualTo(first);
        assertThat(post("/auth/verify-email", Map.of("token", second)).getStatusCode().value()).isEqualTo(204);
    }
    @Test void resendVerificationNeverRevealsAccountExistence() {
        assertThat(post("/auth/resend-verification", Map.of("email", "ghost@fixture.local")).getStatusCode().value()).isEqualTo(204);
        post("/auth/register", registration("student@fixture.local"));
        String first = tokenFromLastMail("student@fixture.local");
        assertThat(post("/auth/resend-verification", Map.of("email", "student@fixture.local")).getStatusCode().value()).isEqualTo(204);
        String second = tokenFromLastMail("student@fixture.local");
        assertThat(second).isNotEqualTo(first);
        assertThat(users.findByEmail("student@fixture.local").orElseThrow().emailVerified()).isFalse();
    }
    @Test void verifiedAccountsAreNotResent() {
        post("/auth/register", registration("student@fixture.local"));
        String token = tokenFromLastMail("student@fixture.local");
        post("/auth/verify-email", Map.of("token", token));
        assertThat(post("/auth/resend-verification", Map.of("email", "student@fixture.local")).getStatusCode().value()).isEqualTo(204);
        assertThat(post.sentMessages().stream().filter(m -> m.startsWith("To: student@fixture.local")).count()).isEqualTo(1);
    }
    @Test void forgotPasswordNeverRevealsAccountExistence() {
        var existing = post("/auth/forgot-password", Map.of("email", "known@fixture.local"));
        var absent = post("/auth/forgot-password", Map.of("email", "unknown@fixture.local"));
        assertThat(existing.getStatusCode().value()).isEqualTo(204);
        assertThat(absent.getStatusCode().value()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM email_tokens WHERE purpose='PASSWORD_RESET'", Integer.class)).isZero();
        // Only existing accounts receive a reset link.
        fixture("known@fixture.local");
        post("/auth/forgot-password", Map.of("email", "known@fixture.local"));
        String token = tokenFromLastMail("known@fixture.local");
        assertThat(post("/auth/reset-password", Map.of("token", token, "password", "replacement-password-123")).getStatusCode().value()).isEqualTo(204);
        assertThat(post("/auth/login", Map.of("email", "known@fixture.local", "password", "replacement-password-123")).getStatusCode().value()).isEqualTo(200);
        assertThat(post("/auth/reset-password", Map.of("token", token, "password", "another-password-123")).getStatusCode().value()).isEqualTo(400);
    }
    @Test void resetPasswordRevokesExistingSessions() {
        fixture("student@fixture.local");
        String access = accessToken();
        assertThat(post("/auth/forgot-password", Map.of("email", "student@fixture.local")).getStatusCode().value()).isEqualTo(204);
        String token = tokenFromLastMail("student@fixture.local");
        post("/auth/reset-password", Map.of("token", token, "password", "replacement-password-123"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens", Integer.class)).isZero();
        assertThat(get("/auth/me", access).getStatusCode().value()).isEqualTo(401);
    }
    private String accessToken() {
        // Resend requires authentication; issue a session directly for the fixture user.
        var user = users.findByEmail("student@fixture.local").orElseThrow();
        String sessionHash = java.util.UUID.randomUUID().toString().replace("-", "").repeat(2);
        jdbc.update("INSERT INTO refresh_tokens(user_id,token_hash,expires_at) VALUES (?,?,now()+interval '7 days')", user.id(), sessionHash);
        return tokenService.issue(user, sessionHash);
    }
    private User fixture(String email) { return users.create(email, passwords.encode(PASSWORD), "Fixture", Role.STUDENT, true); }
    private Map<String, String> registration(String email) {
        return Map.of("email", email, "password", PASSWORD, "fullName", "Fixture", "role", "STUDENT");
    }
    private String tokenFromLastMail(String to) {
        var match = post.sentMessages().stream().filter(m -> m.startsWith("To: " + to)).reduce((a, b) -> b)
                .flatMap(m -> LINK.matcher(m).results().findFirst().map(r -> r.group(1)));
        return match.orElseThrow(() -> new IllegalStateException("no token mailed to " + to));
    }
    private ResponseEntity<String> post(String path, Object body) { return request(HttpMethod.POST, path, body, null); }
    private ResponseEntity<String> get(String path, String token) { return request(HttpMethod.GET, path, null, token); }
    private ResponseEntity<String> request(HttpMethod method, String path, Object body, String token) {
        var headers = new HttpHeaders();
        if (token != null) headers.setBearerAuth(token);
        return http.exchange("/api/v1" + path, method, new HttpEntity<>(body, headers), String.class);
    }
}
