package com.gradely.grading;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={"gradely.worker.enabled=true","gradely.dispatch.enabled=false"})
class WorkerContextTest {
    @Container static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:16");
    @Container static final GenericContainer<?> RABBIT=new GenericContainer<>("rabbitmq:3.13-management")
            .withEnv("RABBITMQ_DEFAULT_USER","fixture").withEnv("RABBITMQ_DEFAULT_PASS","fixture-password")
            .withExposedPorts(5672).waitingFor(Wait.forLogMessage(".*Server startup complete.*",1));
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("DB_URL",DB::getJdbcUrl); p.add("DB_USERNAME",DB::getUsername); p.add("DB_PASSWORD",DB::getPassword);
        p.add("JWT_SECRET",() -> "fixture-secret-".repeat(6));
        p.add("RABBIT_HOST",RABBIT::getHost); p.add("RABBIT_PORT",() -> RABBIT.getMappedPort(5672));
        p.add("RABBIT_USERNAME",() -> "fixture"); p.add("RABBIT_PASSWORD",() -> "fixture-password");
        p.add("BOOTSTRAP_ADMIN_EMAIL",() -> ""); p.add("BOOTSTRAP_ADMIN_NAME",() -> ""); p.add("BOOTSTRAP_ADMIN_PASSWORD",() -> "");
    }
    @MockitoBean Sandbox sandbox;
    @MockitoBean GradingService grading;
    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitListenerEndpointRegistry registry;
    @Test void standaloneWorkerStartsAndConsumesOnlyAfterReadinessCheck() {
        verify(sandbox).verifyReady();
        assertThat(registry.getListenerContainer("gradely-grading").isRunning()).isTrue();
        rabbit.convertAndSend("gradely.submissions","{\"submissionId\":42}");
        verify(grading,timeout(5000)).grade(42);
    }
}
