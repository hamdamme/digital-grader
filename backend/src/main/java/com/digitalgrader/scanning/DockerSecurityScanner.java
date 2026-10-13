package com.digitalgrader.scanning;

import com.digitalgrader.grading.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.file.Path;
import java.util.List;

@Component
public class DockerSecurityScanner implements SecurityScanner {
    private final DockerSandbox sandbox;
    private final ObjectMapper json;
    private final Path database;
    public DockerSecurityScanner(DockerCommand command,ObjectMapper json,
            @Value("${SECURITY_IMAGE:digital-grader-security:local}") String image,
            @Value("${SECURITY_DATABASE_PATH:}") String database) {
        this.json=json;
        this.database=Path.of(database.isBlank()?"missing-security-database":database);
        this.sandbox=new DockerSandbox(command,json,image,600,4096,1,this.database);
    }
    public void verifyReady() {
        sandbox.verifyReady();
        try {
            double timestamp=Double.parseDouble(java.nio.file.Files.readString(database.resolve("updated-at")).strip());
            double age=java.time.Instant.now().getEpochSecond()-timestamp;
            if (!Double.isFinite(age) || age<0 || age>604800 || !java.nio.file.Files.isRegularFile(database.resolve("odc.mv.db")))
                throw new IllegalStateException("Refresh the security database before starting the worker");
        } catch (java.io.IOException | NumberFormatException e) { throw new IllegalStateException("A current security database is required",e); }
    }
    public List<ScanReports.Finding> scan(Path archive) {
        var result=sandbox.run(archive);
        if (result.exitCode()!=0) throw new IllegalStateException("Security scan did not complete");
        return ScanReports.parse(json,result.quality().get("semgrep"),result.quality().get("dependency-check"));
    }
}
