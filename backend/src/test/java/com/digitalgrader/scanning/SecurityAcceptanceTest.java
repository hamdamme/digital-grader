package com.digitalgrader.scanning;

import com.digitalgrader.grading.DockerCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.zip.*;
import static org.assertj.core.api.Assertions.*;

@Tag("sandbox")
class SecurityAcceptanceTest {
    @TempDir Path temp;
    @Test void scansARealArchiveWithoutExecutingItsCode() throws Exception {
        String database=System.getenv("SECURITY_DATABASE_PATH");
        assertThat(database).as("Set SECURITY_DATABASE_PATH to a current vulnerability snapshot").isNotBlank();
        var scanner=new DockerSecurityScanner(new DockerCommand(),new ObjectMapper(),"digital-grader-security:local",database);
        scanner.verifyReady();
        Path zip=temp.resolve("fixture.zip");
        try (var out=new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("Fixture.java"));
            out.write("class Fixture { String password = \"fixture-not-real\"; }".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.closeEntry();
        }
        Files.setPosixFilePermissions(zip,java.nio.file.attribute.PosixFilePermissions.fromString("r--r--r--"));
        assertThat(scanner.scan(zip)).anyMatch(f -> f.cweId().equals("CWE-798") && f.severity().equals("CRITICAL"));
    }
}
