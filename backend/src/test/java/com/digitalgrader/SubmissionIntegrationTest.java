package com.digitalgrader;

import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.digitalgrader.auth.TokenService;
import com.digitalgrader.queue.*;
import com.digitalgrader.storage.StorageService;
import com.digitalgrader.submissions.*;
import com.digitalgrader.users.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.util.LinkedMultiValueMap;
import org.testcontainers.containers.*;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"digital-grader.dispatch.enabled=false","SUBMISSION_QUEUE=digital-grader.submissions"})
class SubmissionIntegrationTest {
    @Container static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:16");
    @Container static final GenericContainer<?> MINIO=new GenericContainer<>("digital-grader-minio:2025-10-15")
            .withEnv("MINIO_ROOT_USER","fixture-access").withEnv("MINIO_ROOT_PASSWORD","fixture-secret-only")
            .withCommand("server","/data").withExposedPorts(9000).waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));
    @Container static final GenericContainer<?> RABBIT=new GenericContainer<>("rabbitmq:3.13-management")
            .withEnv("RABBITMQ_DEFAULT_USER","fixture").withEnv("RABBITMQ_DEFAULT_PASS","fixture-password")
            .withExposedPorts(5672).waitingFor(Wait.forLogMessage(".*Server startup complete.*",1));
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("DB_URL",DB::getJdbcUrl); p.add("DB_USERNAME",DB::getUsername); p.add("DB_PASSWORD",DB::getPassword);
        p.add("JWT_SECRET",() -> "fixture-secret-".repeat(6));
        p.add("MINIO_ENDPOINT",() -> "http://"+MINIO.getHost()+":"+MINIO.getMappedPort(9000));
        p.add("MINIO_ACCESS_KEY",() -> "fixture-access"); p.add("MINIO_SECRET_KEY",() -> "fixture-secret-only");
        p.add("MINIO_BUCKET",() -> "fixture-submissions");
        p.add("RABBIT_HOST",RABBIT::getHost); p.add("RABBIT_PORT",() -> RABBIT.getMappedPort(5672));
        p.add("RABBIT_USERNAME",() -> "fixture"); p.add("RABBIT_PASSWORD",() -> "fixture-password");
        p.add("BOOTSTRAP_ADMIN_EMAIL",() -> ""); p.add("BOOTSTRAP_ADMIN_NAME",() -> ""); p.add("BOOTSTRAP_ADMIN_PASSWORD",() -> "");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate http;
    @Autowired UserRepository users;
    @Autowired TokenService tokens;
    @Autowired ObjectMapper json;
    @Autowired SubmissionService submissions;
    @Autowired OutboxDispatcher dispatcher;
    @Autowired com.digitalgrader.grading.GradingService grading;
    @Autowired com.digitalgrader.grading.GradingRepository jobs;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.digitalgrader.grading.Sandbox sandbox;
    @Autowired org.springframework.amqp.rabbit.core.RabbitTemplate rabbit;
    @MockitoSpyBean StorageService storage;
    @MockitoSpyBean RabbitPublisher publisher;
    User teacher,student,other;
    long assignment;
    byte[] archive;
    @BeforeEach void setup() throws Exception {
        jdbc.execute("TRUNCATE users CASCADE");
        teacher=users.create("teacher@fixture.local","unused","Teacher",Role.INSTRUCTOR);
        student=users.create("student@fixture.local","unused","Student",Role.STUDENT);
        other=users.create("other@fixture.local","unused","Other",Role.STUDENT);
        long cohort=jdbc.queryForObject("INSERT INTO cohorts(name,instructor_id,start_date,end_date) VALUES ('Java',?,'2026-10-01','2027-03-31') RETURNING id",Long.class,teacher.id());
        jdbc.update("INSERT INTO cohort_members(cohort_id,student_id) VALUES (?,?),(?,?)",cohort,student.id(),cohort,other.id());
        assignment=jdbc.queryForObject("INSERT INTO assignments(cohort_id,title,rubric_json) VALUES (?,'Fixture','{\"weights\":{\"tests\":60,\"coverage\":20,\"codeQuality\":20},\"coverageThreshold\":70,\"qualityChecks\":[]}'::jsonb) RETURNING id",Long.class,cohort);
        archive=ZipValidatorTest.zip("pom.xml","src/Test.java");
        new org.springframework.amqp.rabbit.core.RabbitAdmin(rabbit).declareQueue(new org.springframework.amqp.core.Queue(RabbitPublisher.QUEUE,true));
        while (rabbit.receive(RabbitPublisher.QUEUE)!=null) { }
    }
    @Test void uploadStoresExactBytesAndPublishesConfirmedPersistentMessage() throws Exception {
        long id=accepted(upload(student,archive));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM submission_outbox WHERE published_at IS NULL",Integer.class)).isEqualTo(1);
        var response=get("/submissions/"+id,student);
        assertThat(response.getBody()).contains("QUEUED","PENDING").doesNotContain("storagePath");
        String url=json.readTree(get("/submissions/"+id+"/download",student).getBody()).get("url").asText();
        var downloaded=java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofByteArray());
        assertThat(downloaded.statusCode()).isEqualTo(200); assertThat(downloaded.body()).isEqualTo(archive);
        assertThat(dispatcher.dispatchOne()).isTrue();
        var message=rabbit.receive(RabbitPublisher.QUEUE,5000);
        assertThat(message).isNotNull();
        assertThat(json.readTree(message.getBody()).get("submissionId").asLong()).isEqualTo(id);
        assertThat(message.getMessageProperties().getReceivedDeliveryMode()).isEqualTo(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
        assertThat(get("/submissions/"+id,student).getBody()).contains("PUBLISHED","QUEUED");
        assertThat(dispatcher.dispatchOne()).isFalse();
    }
    @Test void accessSeparatesStudentsAndCohorts() throws Exception {
        long id=accepted(upload(student,archive));
        assertThat(get("/submissions/"+id,other).getStatusCode().value()).isEqualTo(403);
        assertThat(get("/submissions/"+id+"/download",other).getStatusCode().value()).isEqualTo(403);
        assertThat(get("/submissions/"+id,teacher).getStatusCode().value()).isEqualTo(200);
        var stranger=users.create("stranger@fixture.local","unused","Stranger",Role.INSTRUCTOR);
        assertThat(get("/submissions/"+id,stranger).getStatusCode().value()).isEqualTo(403);
        assertThat(upload(teacher,archive).getStatusCode().value()).isEqualTo(403);
        assertThat(get("/assignments/"+assignment+"/mysubmissions",other).getBody()).isEqualTo("[]");
        jdbc.update("DELETE FROM cohort_members WHERE student_id=?",other.id());
        assertThat(upload(other,archive).getStatusCode().value()).isEqualTo(403);
    }
    @Test void rejectsUnsafeZipAndPastDeadlineWithoutCreatingJobs() throws Exception {
        assertThat(upload(student,ZipValidatorTest.zip("../outside")).getStatusCode().value()).isEqualTo(400);
        assertThat(upload(student,"not-a-zip".getBytes()).getStatusCode().value()).isEqualTo(400);
        jdbc.update("UPDATE assignments SET due_date=now()-interval '1 second'");
        assertThat(upload(student,archive).getStatusCode().value()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM submissions",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM submission_outbox",Integer.class)).isZero();
    }
    @Test void concurrentLastAttemptsProduceOneSubmission() throws Exception {
        jdbc.update("UPDATE assignments SET max_attempts=1");
        var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        Callable<Integer> call=() -> { start.await(); return upload(student,archive).getStatusCode().value(); };
        try {
            var a=pool.submit(call); var b=pool.submit(call); start.countDown();
            assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(202,409);
        } finally { pool.shutdownNow(); }
        assertThat(jdbc.queryForObject("SELECT attempt_number FROM submissions",Integer.class)).isEqualTo(1);
    }
    @Test void storageFailureRollsBackSubmissionAndOutbox() {
        doThrow(new com.digitalgrader.common.ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Submission storage is unavailable")).when(storage).upload(anyString(),any());
        assertThat(upload(student,archive).getStatusCode().value()).isEqualTo(503);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM submissions",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM submission_outbox",Integer.class)).isZero();
        verify(storage).delete(anyString());
    }
    @Test void databaseFailureCleansStoredObjectAndRollsBackAttempt() throws Exception {
        jdbc.execute("ALTER TABLE submission_outbox ADD CONSTRAINT fixture_reject_outbox CHECK (submission_id<0)");
        try (var file=new ZipValidator().validate(new org.springframework.mock.web.MockMultipartFile("file","project.zip","application/zip",archive))) {
            assertThatThrownBy(() -> submissions.submit(assignment,student.profile(),file)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        } finally { jdbc.execute("ALTER TABLE submission_outbox DROP CONSTRAINT fixture_reject_outbox"); }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM submissions",Integer.class)).isZero();
        var key=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(storage).delete(key.capture());
        var result=java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(storage.downloadUrl(key.getValue()))).GET().build(),java.net.http.HttpResponse.BodyHandlers.discarding());
        assertThat(result.statusCode()).isEqualTo(404);
        accepted(upload(student,archive));
        assertThat(jdbc.queryForObject("SELECT attempt_number FROM submissions",Integer.class)).isEqualTo(1);
    }
    @Test void failedDispatchRemainsDurableAndCanRetry() throws Exception {
        long id=accepted(upload(student,archive));
        doThrow(new IllegalStateException("fixture broker outage")).when(publisher).publish(id);
        dispatcher.dispatchOne();
        assertThat(jdbc.queryForObject("SELECT published_at IS NULL FROM submission_outbox",Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT attempts FROM submission_outbox",Integer.class)).isEqualTo(1);
        assertThat(dispatcher.dispatchOne()).isFalse();
        doCallRealMethod().when(publisher).publish(id);
        jdbc.update("UPDATE submission_outbox SET next_attempt_at=now()");
        dispatcher.dispatchOne();
        assertThat(rabbit.receive(RabbitPublisher.QUEUE,5000)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT published_at IS NOT NULL FROM submission_outbox",Boolean.class)).isTrue();
    }
    @Test void lifecycleTransitionsAreConditionalAndTerminal() throws Exception {
        long id=accepted(upload(student,archive));
        assertThat(submissions.transition(id,SubmissionStatus.QUEUED,SubmissionStatus.RUNNING)).isTrue();
        assertThat(submissions.transition(id,SubmissionStatus.QUEUED,SubmissionStatus.RUNNING)).isFalse();
        assertThat(submissions.transition(id,SubmissionStatus.RUNNING,SubmissionStatus.TIMEOUT)).isTrue();
        assertThatThrownBy(() -> submissions.transition(id,SubmissionStatus.TIMEOUT,SubmissionStatus.RUNNING)).isInstanceOf(IllegalArgumentException.class);
        assertThat(get("/submissions/"+id,student).getBody()).contains("TIMEOUT");
    }
    @Test void workerPersistsResultsArtifactsAndSecurityHandoffOnce() throws Exception {
        long id=accepted(upload(student,archive));
        String first="<testsuite><testcase classname='C' name='pass'/><testcase classname='C' name='flaky'><failure message='fixture'/></testcase></testsuite>";
        String retry="<testsuite><testcase classname='C' name='pass'/><testcase classname='C' name='flaky'/></testsuite>";
        var coverage="<report><counter type='LINE' covered='3' missed='1'/></report>";
        when(sandbox.run(any())).thenReturn(new com.digitalgrader.grading.Sandbox.Result(1,"first log",List.of(first),coverage,Map.of()),new com.digitalgrader.grading.Sandbox.Result(0,"retry log",List.of(retry),coverage,Map.of()));
        grading.grade(id); grading.grade(id);
        verify(sandbox,times(2)).run(any());
        assertThat(jdbc.queryForObject("SELECT status FROM submissions WHERE id=?",String.class,id)).isEqualTo("GRADED");
        assertThat(jdbc.queryForObject("SELECT tests_flaky FROM grading_results WHERE submission_id=?",Integer.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM security_outbox WHERE submission_id=?",Integer.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM grading_results WHERE submission_id=?",Integer.class,id)).isEqualTo(1);
        var result=get("/submissions/"+id+"/result",student);
        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).contains("FLAKY","rubricBreakdown");
        assertThat(get("/submissions/"+id+"/result",other).getStatusCode().value()).isEqualTo(403);
        assertThat(get("/submissions/"+id+"/result",teacher).getStatusCode().value()).isEqualTo(200);
        String url=json.readTree(get("/submissions/"+id+"/build-log",teacher).getBody()).get("url").asText();
        var log=java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(log.body()).isEqualTo("first log");
    }
    @Test void workerTimeoutIsTerminalAndInfrastructureFailuresAreBounded() throws Exception {
        long timeoutId=accepted(upload(student,archive));
        when(sandbox.run(any())).thenReturn(new com.digitalgrader.grading.Sandbox.Result(124,"timeout",List.of(),null,Map.of()));
        grading.grade(timeoutId);
        assertThat(jdbc.queryForObject("SELECT status FROM submissions WHERE id=?",String.class,timeoutId)).isEqualTo("TIMEOUT");
        assertThat(get("/submissions/"+timeoutId+"/result",student).getStatusCode().value()).isEqualTo(409);
        long failedId=accepted(upload(other,archive));
        when(sandbox.run(any())).thenThrow(new IllegalStateException("fixture sandbox unavailable"));
        for (int i=0;i<4;i++) grading.grade(failedId);
        assertThat(jdbc.queryForObject("SELECT status FROM submissions WHERE id=?",String.class,failedId)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT grading_attempts FROM submissions WHERE id=?",Integer.class,failedId)).isEqualTo(3);
    }
    @Test void staleClaimsAreRecoveredAndCannotWriteLateResults() throws Exception {
        long id=accepted(upload(student,archive));
        var stale=jobs.claim(id).orElseThrow();
        assertThat(jobs.claim(id)).isEmpty();
        jdbc.update("UPDATE submissions SET grading_started_at=now()-interval '1 hour' WHERE id=?",id);
        assertThat(jobs.recoverStale(1800)).isEqualTo(1);
        var current=jobs.claim(id).orElseThrow();
        var zero=new com.digitalgrader.grading.Scoring.Score(java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO);
        assertThat(jobs.finish(stale,List.of(),com.digitalgrader.grading.ReportParser.jacoco(null),zero,false,"fixture-log")).isFalse();
        assertThat(jobs.finish(current,List.of(),com.digitalgrader.grading.ReportParser.jacoco(null),zero,false,"fixture-log")).isTrue();
    }
    private long accepted(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(202);
        return json.readTree(response.getBody()).get("submissionId").asLong();
    }
    private ResponseEntity<String> upload(User user,byte[] bytes) {
        var headers=new HttpHeaders(); headers.setBearerAuth(tokens.issue(user)); headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        var body=new LinkedMultiValueMap<String,Object>();
        body.add("file",new ByteArrayResource(bytes) { @Override public String getFilename() { return "project.zip"; } });
        return http.postForEntity("/api/v1/assignments/"+assignment+"/submissions",new HttpEntity<>(body,headers),String.class);
    }
    private ResponseEntity<String> get(String path,User user) {
        var headers=new HttpHeaders(); headers.setBearerAuth(tokens.issue(user));
        return http.exchange("/api/v1"+path,HttpMethod.GET,new HttpEntity<>(headers),String.class);
    }
}
