package com.gradely.grading;

import java.nio.file.*;
import java.util.zip.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

/** Explicit Linux/gVisor gate: ./scripts/backend.sh -Psandbox test. Never silently skips. */
@Tag("sandbox")
class SandboxAcceptanceTest {
    @TempDir Path temp;
    @Test void runsSampleOfflineWithCoverageAndFailureReports() throws Exception {
        var sandbox=new DockerSandbox(new DockerCommand(),new ObjectMapper(),"gradely-student:session6",180,1536,1);
        sandbox.verifyReady();
        var result=sandbox.run(archive(false));
        assertThat(result.exitCode()).as(result.log()).isEqualTo(1);
        var tests=ReportParser.junit(result.junit());
        assertThat(tests).hasSize(2);
        assertThat(tests).extracting(ReportParser.TestCase::status).containsExactlyInAnyOrder("PASSED","FAILED");
        assertThat(ReportParser.jacoco(result.jacoco()).line()).isGreaterThan(java.math.BigDecimal.ZERO);
        assertThat(result.quality()).containsKeys("checkstyle","spotbugs");
    }
    @Test void terminatesHangingProject() throws Exception {
        var sandbox=new DockerSandbox(new DockerCommand(),new ObjectMapper(),"gradely-student:session6",15,1536,1);
        sandbox.verifyReady();
        assertThat(sandbox.run(archive(true)).exitCode()).isEqualTo(124);
    }
    private Path archive(boolean hang) throws Exception {
        Path root=Path.of("../sandbox/sample-project").toAbsolutePath().normalize();
        Path zip=temp.resolve("fixture.zip");
        try (var out=new ZipOutputStream(Files.newOutputStream(zip)); var paths=Files.walk(root)) {
            for (Path file:paths.filter(Files::isRegularFile).toList()) {
                String name=root.relativize(file).toString().replace('\\','/');
                out.putNextEntry(new ZipEntry(name));
                byte[] content=Files.readAllBytes(file);
                if (hang && name.endsWith("CalculatorTest.java")) content="package example; import org.testng.annotations.Test; public class CalculatorTest { @Test public void hangs() throws Exception { Thread.sleep(Long.MAX_VALUE); } }".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                out.write(content); out.closeEntry();
            }
        }
        Files.setPosixFilePermissions(zip,java.nio.file.attribute.PosixFilePermissions.fromString("r--r--r--"));
        return zip;
    }
}
