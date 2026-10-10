package com.gradely;

import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties="gradely.dispatch.enabled=false")
class DatabaseIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");
    @DynamicPropertySource static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("DB_URL", POSTGRES::getJdbcUrl);
        properties.add("DB_USERNAME", POSTGRES::getUsername);
        properties.add("DB_PASSWORD", POSTGRES::getPassword);
        properties.add("JWT_SECRET", () -> "test-only-".repeat(8));
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired TestRestTemplate http;
    @BeforeEach void clearFixtures() { jdbc.execute("TRUNCATE users CASCADE"); }

    @Test void createsAllTablesAndMigrationIsRepeatable() {
        assertThat(jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'", String.class))
                .containsExactlyInAnyOrder("users", "cohorts", "cohort_members", "assignments", "submissions",
                        "grading_results", "test_case_results", "security_findings", "hints", "instructor_overrides", "refresh_tokens", "submission_outbox", "security_outbox");
        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE schemaname = 'public'", String.class))
                .contains("idx_submissions_assignment_student", "idx_security_findings_submission",
                        "idx_test_case_results_grading", "idx_cohort_members_student");
    }
    @Test void healthEndpointUsesRealDatabase() {
        var response = http.getForEntity("/api/v1/health", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("{\"status\":\"UP\"}");
    }
    @Test void unknownEndpointsRequireAuthentication() {
        assertThat(http.getForEntity("/api/v1/users", String.class).getStatusCode().value()).isEqualTo(401);
    }
    @Test void rejectsInvalidRole() {
        assertThatThrownBy(() -> user("student@fixture.local", "SUPERUSER")).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void rejectsDuplicateEmail() {
        user("student@fixture.local", "STUDENT");
        assertThatThrownBy(() -> user("student@fixture.local", "STUDENT")).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void rejectsCaseVariantEmail() {
        user("Student@fixture.local", "STUDENT");
        assertThatThrownBy(() -> user("student@fixture.local", "STUDENT")).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void rejectsOrphanCohort() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO cohorts(name,instructor_id,start_date,end_date) VALUES ('Fixture', -1, '2026-10-01','2027-03-31')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void persistsCohortAssignmentSubmissionAndEnforcesUniqueAttempt() {
        long student = user("student@fixture.local", "STUDENT");
        long teacher = user("teacher@fixture.local", "INSTRUCTOR");
        long cohort = jdbc.queryForObject("INSERT INTO cohorts(name,instructor_id,start_date,end_date) VALUES ('Fixture', ?, '2026-10-01','2027-03-31') RETURNING id", Long.class, teacher);
        jdbc.update("INSERT INTO cohort_members(cohort_id,student_id) VALUES (?,?)", cohort, student);
        long assignment = jdbc.queryForObject("INSERT INTO assignments(cohort_id,title,rubric_json) VALUES (?, 'Fixture', '{\"weights\":{\"tests\":60,\"coverage\":20,\"codeQuality\":20}}'::jsonb) RETURNING id", Long.class, cohort);
        long submission = jdbc.queryForObject("INSERT INTO submissions(assignment_id,student_id,attempt_number,storage_path) VALUES (?,?,1,'fixture.zip') RETURNING id", Long.class, assignment, student);
        assertThat(jdbc.queryForObject("SELECT status FROM submissions WHERE id=?", String.class, submission)).isEqualTo("QUEUED");
        assertThat(jdbc.queryForObject("SELECT rubric_json->'weights'->>'tests' FROM assignments WHERE id=?", String.class, assignment)).isEqualTo("60");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO submissions(assignment_id,student_id,attempt_number,storage_path) VALUES (?,?,1,'duplicate.zip')", assignment, student))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void refreshTokensFollowUserDeletion() {
        long student = user("student@fixture.local", "STUDENT");
        jdbc.update("INSERT INTO refresh_tokens(user_id,token_hash,expires_at) VALUES (?, ?, now() + interval '7 days')", student, "a".repeat(64));
        jdbc.update("DELETE FROM users WHERE id=?", student);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens", Integer.class)).isZero();
    }
    private long user(String email, String role) {
        return jdbc.queryForObject("INSERT INTO users(email,password_hash,full_name,role) VALUES (?, 'test-hash-not-login-capable','Fixture',?) RETURNING id", Long.class, email, role);
    }
}
