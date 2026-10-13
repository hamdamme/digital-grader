package com.digitalgrader.queue;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name="digital-grader.dispatch.enabled",havingValue="true",matchIfMissing=true)
public class OutboxSchedule {
    private final OutboxDispatcher dispatcher;
    public OutboxSchedule(OutboxDispatcher dispatcher) { this.dispatcher=dispatcher; }
    @Scheduled(fixedDelayString="${digital-grader.dispatch.delay-ms:2000}",initialDelayString="${digital-grader.dispatch.delay-ms:2000}")
    public void dispatch() { dispatcher.dispatchOne(); }
}
