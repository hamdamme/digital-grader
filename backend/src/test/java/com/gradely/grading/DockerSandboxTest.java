package com.gradely.grading;

import java.nio.file.Path;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DockerSandboxTest {
    @Test void enforcesIsolationAndCleansUpAfterTimeout() {
        var docker=mock(DockerCommand.class);
        when(docker.run(anyList(),any(),anyInt())).thenAnswer(call -> {
            List<String> args=call.getArgument(0);
            return switch(args.get(1)) {
                case "info" -> new DockerCommand.Output(0,"{\"runsc\":{}}");
                case "image" -> new DockerCommand.Output(0,"sha256:"+"a".repeat(64));
                case "wait" -> throw new DockerCommand.DeadlineExceeded();
                default -> new DockerCommand.Output(0,"");
            };
        });
        var sandbox=new DockerSandbox(docker,new ObjectMapper(),"gradely-student:session6",30,1536,1);
        sandbox.verifyReady();
        var args=sandbox.command("fixture",Path.of("/tmp/fixture.zip"));
        assertThat(args).contains("--runtime=runsc","--network=none","--read-only","--cap-drop=ALL","--security-opt=no-new-privileges","--user=10001:10001","--pids-limit=256","--memory=1536m","--memory-swap=1536m","--cpus=1.0");
        assertThat(args).anyMatch(a -> a.startsWith("--tmpfs=/output:")).contains("type=bind,src=/tmp/fixture.zip,dst=/input/submission.zip,readonly");
        assertThat(args).doesNotContain("--privileged","--network=host","/var/run/docker.sock");
        assertThat(sandbox.run(Path.of("/tmp/fixture.zip")).exitCode()).isEqualTo(124);
        verify(docker).run(argThat(a -> a.get(1).equals("rm") && a.contains("--force")),any(),anyInt());
    }
    @Test void infrastructureFailureIsNotReportedAsStudentTimeout() {
        var docker=mock(DockerCommand.class);
        when(docker.run(anyList(),any(),anyInt())).thenAnswer(call -> {
            List<String> args=call.getArgument(0);
            return switch(args.get(1)) {
                case "info" -> new DockerCommand.Output(0,"{\"runsc\":{}}");
                case "image" -> new DockerCommand.Output(0,"sha256:"+"a".repeat(64));
                case "wait" -> throw new IllegalStateException("Docker unavailable");
                default -> new DockerCommand.Output(0,"");
            };
        });
        var sandbox=new DockerSandbox(docker,new ObjectMapper(),"gradely-student:session6",30,1536,1);
        sandbox.verifyReady();
        assertThatThrownBy(() -> sandbox.run(Path.of("/tmp/fixture.zip"))).hasMessage("Docker unavailable");
        verify(docker).run(argThat(a -> a.get(1).equals("rm") && a.contains("--force")),any(),anyInt());
    }
    @Test void rejectsUnsafeLimits() {
        var docker=mock(DockerCommand.class);
        assertThatThrownBy(() -> new DockerSandbox(docker,new ObjectMapper(),"--privileged",30,1536,1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DockerSandbox(docker,new ObjectMapper(),"image",601,1536,1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DockerSandbox(docker,new ObjectMapper(),"image",30,1536,Double.NaN)).isInstanceOf(IllegalArgumentException.class);
    }
}
