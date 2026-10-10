package com.digitalgrader;

import java.util.Map;
import java.util.List;
import java.util.concurrent.*;
import com.digitalgrader.auth.AuthService;
import com.digitalgrader.auth.TokenService;
import com.digitalgrader.users.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties="digital-grader.dispatch.enabled=false")
class AuthIntegrationTest {
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
    }
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired TokenService tokens;
    @Autowired AuthService auth;
    @Autowired com.digitalgrader.cohorts.CohortAccess access;
    static final String PASSWORD = "fixture-password-123";
    @BeforeEach void clean() { jdbc.execute("TRUNCATE users CASCADE"); }

    @Test void registrationNormalizesEmailAndDoesNotExposeHash() {
        var response = post("/auth/register", registration("Student@Fixture.local", "STUDENT"));
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).doesNotContain("password", "$2a$").contains("student@fixture.local");
        var user = users.findByEmail("STUDENT@fixture.local").orElseThrow();
        assertThat(passwords.matches(PASSWORD, user.passwordHash())).isTrue();
        assertThat(post("/auth/register", registration("student@fixture.local", "STUDENT")).getStatusCode().value()).isEqualTo(409);
    }
    @Test void registrationRejectsElevationAndInvalidFields() {
        for (String role : List.of("ADMIN", "INSTRUCTOR"))
            assertThat(post("/auth/register", registration("fixture@example.org", role)).getStatusCode().value()).isEqualTo(403);
        var invalid = post("/auth/register", Map.of("email", "bad", "password", "private", "fullName", "", "role", "STUDENT"));
        assertThat(invalid.getStatusCode().value()).isEqualTo(400);
        assertThat(invalid.getBody()).doesNotContain("private");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isZero();
    }
    @Test void loginAuthenticatesMeAndStoresOnlyRefreshHash() {
        fixture("student@fixture.local", Role.STUDENT);
        var session = login("student@fixture.local");
        var me = get("/auth/me", session.accessToken());
        assertThat(me.getStatusCode().value()).isEqualTo(200);
        assertThat(me.getBody()).contains("STUDENT").doesNotContain("password");
        assertThat(jdbc.queryForObject("SELECT token_hash FROM refresh_tokens", String.class)).isEqualTo(AuthService.hash(session.refreshToken()));
        assertThat(get("/auth/me", session.refreshToken()).getStatusCode().value()).isEqualTo(401);
    }
    @Test void loginFailuresAreGeneric() {
        fixture("student@fixture.local", Role.STUDENT);
        var wrong = post("/auth/login", Map.of("email", "student@fixture.local", "password", "wrong-password"));
        var unknown = post("/auth/login", Map.of("email", "absent@fixture.local", "password", "wrong-password"));
        assertThat(wrong.getStatusCode().value()).isEqualTo(401);
        assertThat(unknown.getStatusCode()).isEqualTo(wrong.getStatusCode());
        assertThat(unknown.getBody()).isEqualTo(wrong.getBody());
    }
    @Test void refreshRotatesAndRejectsReplay() {
        fixture("student@fixture.local", Role.STUDENT);
        var session = login("student@fixture.local");
        var response = http.postForEntity("/api/v1/auth/refresh", Map.of("refreshToken", session.refreshToken()), AuthService.Tokens.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().refreshToken()).isNotEqualTo(session.refreshToken());
        assertThat(get("/auth/me", response.getBody().accessToken()).getStatusCode().value()).isEqualTo(200);
        assertThat(post("/auth/refresh", Map.of("refreshToken", session.refreshToken())).getStatusCode().value()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens", Integer.class)).isEqualTo(1);
    }
    @Test void simultaneousRefreshAllowsOneWinner() throws Exception {
        fixture("student@fixture.local", Role.STUDENT);
        var session = login("student@fixture.local");
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        Callable<Integer> refresh = () -> { start.await(); return post("/auth/refresh", Map.of("refreshToken", session.refreshToken())).getStatusCode().value(); };
        try {
            var first = pool.submit(refresh); var second = pool.submit(refresh); start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 401);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens", Integer.class)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    @Test void failedRefreshIssuanceRollsBackConsumption() {
        fixture("student@fixture.local", Role.STUDENT);
        var session = login("student@fixture.local");
        String oldHash = AuthService.hash(session.refreshToken());
        // A test-only constraint simulates a database failure when storing the replacement token.
        jdbc.execute("ALTER TABLE refresh_tokens ADD CONSTRAINT fixture_reject_replacement CHECK (token_hash='" + oldHash + "')");
        try {
            assertThatThrownBy(() -> auth.refresh(session.refreshToken())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("SELECT token_hash FROM refresh_tokens", String.class)).isEqualTo(oldHash);
        } finally {
            jdbc.execute("ALTER TABLE refresh_tokens DROP CONSTRAINT fixture_reject_replacement");
        }
        assertThat(post("/auth/refresh", Map.of("refreshToken", session.refreshToken())).getStatusCode().value()).isEqualTo(200);
    }
    @Test void expiredRefreshIsRejectedAndLogoutIsIdempotent() {
        fixture("student@fixture.local", Role.STUDENT);
        var expired = login("student@fixture.local");
        jdbc.update("UPDATE refresh_tokens SET expires_at=now()-interval '1 second'");
        assertThat(post("/auth/refresh", Map.of("refreshToken", expired.refreshToken())).getStatusCode().value()).isEqualTo(401);
        var current = login("student@fixture.local");
        for (int i = 0; i < 2; i++) assertThat(post("/auth/logout", Map.of("refreshToken", current.refreshToken())).getStatusCode().value()).isEqualTo(204);
        assertThat(post("/auth/refresh", Map.of("refreshToken", current.refreshToken())).getStatusCode().value()).isEqualTo(401);
        // Logout revokes the refresh token; already-issued access tokens retain their short lifetime.
        assertThat(get("/auth/me", current.accessToken()).getStatusCode().value()).isEqualTo(200);
    }
    @Test void rejectsMissingMalformedTamperedAndExpiredAccess() {
        var user = fixture("student@fixture.local", Role.STUDENT);
        assertThat(get("/auth/me", null).getStatusCode().value()).isEqualTo(401);
        assertThat(get("/auth/me", "bad.token").getStatusCode().value()).isEqualTo(401);
        String token = tokens.issue(user);
        String[] parts = token.split("\\.");
        String signature = (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        assertThat(get("/auth/me", parts[0] + "." + parts[1] + "." + signature).getStatusCode().value()).isEqualTo(401);
        var past = java.time.Clock.offset(java.time.Clock.systemUTC(), java.time.Duration.ofHours(-2));
        String expired = new TokenService("fixture-secret-".repeat(6), 60, 7, past).issue(user);
        assertThat(get("/auth/me", expired).getStatusCode().value()).isEqualTo(401);
    }
    @Test void deletedUsersAndChangedRolesInvalidateAccess() {
        var user = fixture("teacher@fixture.local", Role.INSTRUCTOR);
        String token = tokens.issue(user);
        jdbc.update("UPDATE users SET role='STUDENT' WHERE id=?", user.id());
        assertThat(get("/auth/me", token).getStatusCode().value()).isEqualTo(401);
        jdbc.update("DELETE FROM users WHERE id=?", user.id());
        assertThat(get("/auth/me", token).getStatusCode().value()).isEqualTo(401);
    }
    @Test void studentCannotUseTeacherOrAdminEndpoints() {
        String token = tokens.issue(fixture("student@fixture.local", Role.STUDENT));
        assertThat(get("/cohorts/mine", token).getStatusCode().value()).isEqualTo(403);
        assertThat(request(HttpMethod.POST, "/cohorts", Map.of(), token).getStatusCode().value()).isEqualTo(403);
        assertThat(request(HttpMethod.POST, "/admin/users", registration("teacher@fixture.local", "INSTRUCTOR"), token).getStatusCode().value()).isEqualTo(403);
    }
    @Test void teacherCanOnlyReadOwnCohorts() {
        var owner = fixture("owner@fixture.local", Role.INSTRUCTOR);
        var other = fixture("other@fixture.local", Role.INSTRUCTOR);
        long id = jdbc.queryForObject("INSERT INTO cohorts(name,instructor_id,start_date,end_date) VALUES ('Private class',?,'2026-10-01','2027-03-31') RETURNING id", Long.class, owner.id());
        assertThat(get("/cohorts/mine", tokens.issue(owner)).getBody()).contains("Private class");
        assertThat(get("/cohorts/mine", tokens.issue(other)).getBody()).isEqualTo("[]");
        assertThat(get("/cohorts/" + id, tokens.issue(owner)).getStatusCode().value()).isEqualTo(200);
        assertThat(get("/cohorts/" + id, tokens.issue(other)).getStatusCode().value()).isEqualTo(403);
        assertThat(get("/cohorts/" + id, tokens.issue(fixture("admin@fixture.local", Role.ADMIN))).getStatusCode().value()).isEqualTo(200);
    }
    @Test void reusableCohortAccessChecksStudentMembership() {
        var owner = fixture("owner@fixture.local", Role.INSTRUCTOR);
        var member = fixture("member@fixture.local", Role.STUDENT);
        var outsider = fixture("outsider@fixture.local", Role.STUDENT);
        long id = jdbc.queryForObject("INSERT INTO cohorts(name,instructor_id,start_date,end_date) VALUES ('Class',?,'2026-10-01','2027-03-31') RETURNING id", Long.class, owner.id());
        jdbc.update("INSERT INTO cohort_members(cohort_id,student_id) VALUES (?,?)", id, member.id());
        assertThat(access.canRead(id, member.profile())).isTrue();
        assertThat(access.canRead(id, outsider.profile())).isFalse();
        assertThat(access.canRead(-1, member.profile())).isFalse();
    }
    @Test void adminCanProvisionInstructorButNotAnotherAdmin() {
        String token = tokens.issue(fixture("admin@fixture.local", Role.ADMIN));
        assertThat(request(HttpMethod.POST, "/admin/users", registration("teacher@fixture.local", "INSTRUCTOR"), token).getStatusCode().value()).isEqualTo(201);
        assertThat(login("teacher@fixture.local").role()).isEqualTo(Role.INSTRUCTOR);
        assertThat(request(HttpMethod.POST, "/admin/users", registration("other@fixture.local", "ADMIN"), token).getStatusCode().value()).isEqualTo(403);
    }
    @Test void corsAllowsConfiguredOriginAndRejectsOthers() {
        var headers = new HttpHeaders();
        headers.setOrigin("http://127.0.0.1:5176");
        headers.setAccessControlRequestMethod(HttpMethod.POST);
        headers.setAccessControlRequestHeaders(List.of("content-type", "authorization"));
        var allowed = http.exchange("/api/v1/auth/login", HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class);
        assertThat(allowed.getStatusCode().value()).isEqualTo(200);
        assertThat(allowed.getHeaders().getAccessControlAllowOrigin()).isEqualTo("http://127.0.0.1:5176");
        headers.setOrigin("https://untrusted.example");
        var denied = http.exchange("/api/v1/auth/login", HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class);
        assertThat(denied.getStatusCode().value()).isEqualTo(403);
        assertThat(denied.getHeaders().getAccessControlAllowOrigin()).isNull();
    }
    @Test void teacherCreatesCohortEnrollsStudentsAndPublishesAssignment() throws Exception {
        String teacher = tokens.issue(fixture("teacher@fixture.local", Role.INSTRUCTOR));
        var student = fixture("student@fixture.local", Role.STUDENT);
        var created = request(HttpMethod.POST,"/cohorts",Map.of("name","Java","startDate","2026-10-01","endDate","2027-03-31"),teacher);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        long cohort = new com.fasterxml.jackson.databind.ObjectMapper().readTree(created.getBody()).get("id").asLong();
        for (int i=0;i<2;i++) assertThat(request(HttpMethod.POST,"/cohorts/"+cohort+"/members",Map.of("studentIds",List.of(student.id(),student.id())),teacher).getStatusCode().value()).isEqualTo(204);
        assertThat(get("/cohorts/mine",teacher).getBody()).contains("\"studentCount\":1");
        assertThat(get("/cohorts/"+cohort,teacher).getBody()).contains(student.email()).doesNotContain("password");
        var assignment = request(HttpMethod.POST,"/assignments",assignmentBody(cohort,60),teacher);
        assertThat(assignment.getStatusCode().value()).isEqualTo(201);
        long id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(assignment.getBody()).get("id").asLong();
        assertThat(get("/assignments/"+id,tokens.issue(student)).getStatusCode().value()).isEqualTo(200);
        assertThat(get("/assignments?cohortId="+cohort,tokens.issue(student)).getBody()).contains("Exercise");
        var outsider = tokens.issue(fixture("outsider@fixture.local",Role.STUDENT));
        assertThat(get("/assignments/"+id,outsider).getStatusCode().value()).isEqualTo(403);
        assertThat(request(HttpMethod.POST,"/assignments",assignmentBody(cohort,60),tokens.issue(student)).getStatusCode().value()).isEqualTo(403);
    }
    @Test void teacherWritesEnforceOwnershipValidationAndAtomicMembership() {
        var owner=fixture("owner@fixture.local",Role.INSTRUCTOR);
        var student=fixture("student@fixture.local",Role.STUDENT);
        var other=fixture("other@fixture.local",Role.INSTRUCTOR);
        long cohort=jdbc.queryForObject("INSERT INTO cohorts(name,instructor_id,start_date,end_date) VALUES ('Fixture',?,'2026-10-01','2027-03-31') RETURNING id",Long.class,owner.id());
        String token=tokens.issue(owner);
        assertThat(request(HttpMethod.POST,"/cohorts",Map.of("name","Bad","startDate","2027-01-01","endDate","2026-01-01"),token).getStatusCode().value()).isEqualTo(400);
        assertThat(request(HttpMethod.POST,"/cohorts/"+cohort+"/members",Map.of("studentIds",List.of(student.id(),other.id())),token).getStatusCode().value()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cohort_members",Integer.class)).isZero();
        assertThat(request(HttpMethod.POST,"/cohorts/"+cohort+"/members",Map.of("studentIds",List.of(student.id())),tokens.issue(other)).getStatusCode().value()).isEqualTo(403);
        assertThat(request(HttpMethod.POST,"/assignments",assignmentBody(cohort,50),token).getStatusCode().value()).isEqualTo(400);
        assertThat(request(HttpMethod.POST,"/assignments",assignmentBody(cohort,60),tokens.issue(other)).getStatusCode().value()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM assignments",Integer.class)).isZero();
    }
    @Test void openApiDocumentsTeacherAndAuthEndpoints() {
        var response=http.getForEntity("/v3/api-docs",String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("/api/v1/auth/login","/api/v1/cohorts/{id}/members","/api/v1/assignments","bearerAuth");
        assertThat(http.getForEntity("/swagger-ui/index.html",String.class).getStatusCode().value()).isEqualTo(200);
    }
    private Map<String,Object> assignmentBody(long cohort,int tests) {
        return Map.of("cohortId",cohort,"title","Exercise","rubricJson",Map.of("weights",Map.of("tests",tests,"coverage",20,"codeQuality",20),"coverageThreshold",70,"qualityChecks",List.of("checkstyle","spotbugs")));
    }
    private User fixture(String email, Role role) { return users.create(email, passwords.encode(PASSWORD), "Fixture", role); }
    private Map<String, String> registration(String email, String role) { return Map.of("email", email, "password", PASSWORD, "fullName", "Fixture", "role", role); }
    private AuthService.Tokens login(String email) {
        var response = http.postForEntity("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD), AuthService.Tokens.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return response.getBody();
    }
    private ResponseEntity<String> post(String path, Object body) { return request(HttpMethod.POST, path, body, null); }
    private ResponseEntity<String> get(String path, String token) { return request(HttpMethod.GET, path, null, token); }
    private ResponseEntity<String> request(HttpMethod method, String path, Object body, String token) {
        var headers = new HttpHeaders();
        if (token != null) headers.setBearerAuth(token);
        return http.exchange("/api/v1" + path, method, new HttpEntity<>(body, headers), String.class);
    }
}
