package com.digitalgrader.scanning;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name="digital-grader.security.enabled",havingValue="true")
public class ScanWorker implements ApplicationRunner {
    private final SecurityScanner scanner;
    private final ScanService service;
    private final ScanRepository jobs;
    private volatile boolean ready;
    public ScanWorker(SecurityScanner scanner,ScanService service,ScanRepository jobs) { this.scanner=scanner;this.service=service;this.jobs=jobs; }
    public void run(ApplicationArguments args) { scanner.verifyReady(); ready=true; }
    @Scheduled(fixedDelay=2000,initialDelay=2000)
    public void poll() {
        if (!ready) return;
        jobs.recoverStale();
        service.scanNext();
    }
}
