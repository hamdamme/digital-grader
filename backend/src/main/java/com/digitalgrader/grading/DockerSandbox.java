package com.digitalgrader.grading;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DockerSandbox implements Sandbox {
    private final DockerCommand docker;
    private final ObjectMapper json;
    private final String image;
    private final int timeout,memory;
    private final double cpu;
    private volatile String imageId;
    private Path referenceData;
    @org.springframework.beans.factory.annotation.Autowired
    public DockerSandbox(DockerCommand docker,ObjectMapper json,@Value("${SANDBOX_IMAGE:digital-grader-student:session6}") String image,
            @Value("${SANDBOX_TIMEOUT_SECONDS:600}") int timeout,@Value("${SANDBOX_MEMORY_MB:1536}") int memory,
            @Value("${SANDBOX_CPU_LIMIT:1}") double cpu) {
        if (!image.matches("[a-zA-Z0-9][a-zA-Z0-9._/@:-]*") || timeout<5 || timeout>600 || memory<512 || memory>4096 || !Double.isFinite(cpu) || cpu<0.25 || cpu>4)
            throw new IllegalArgumentException("Invalid sandbox limits/image");
        this.docker=docker; this.json=json; this.image=image; this.timeout=timeout; this.memory=memory; this.cpu=cpu;
    }
    public DockerSandbox(DockerCommand docker,ObjectMapper json,String image,int timeout,int memory,double cpu,Path referenceData) {
        this(docker,json,image,timeout,memory,cpu);
        this.referenceData=referenceData;
    }
    @Override public synchronized void verifyReady() {
        try {
            if (referenceData!=null && (!referenceData.isAbsolute() || !java.nio.file.Files.isDirectory(referenceData) || referenceData.toString().contains(",")))
                throw new IllegalStateException("A local reference database directory is required");
            var runtimes=docker.run(List.of("docker","info","--format","{{json .Runtimes}}"),Duration.ofSeconds(10),65536);
            if (runtimes.code()!=0 || !json.readTree(runtimes.out()).has("runsc")) throw new IllegalStateException("A Linux Docker engine with the runsc runtime is required");
            var inspect=docker.run(List.of("docker","image","inspect","--format","{{.Id}}",image),Duration.ofSeconds(10),1024);
            if (inspect.code()!=0 || !inspect.out().strip().matches("sha256:[a-f0-9]{64}")) throw new IllegalStateException("Build the Digital Grader sandbox image first");
            imageId=inspect.out().strip();
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot verify sandbox runtime",e); }
    }
    List<String> command(String name,Path archive) {
        String input=archive.toAbsolutePath().toString();
        if (input.contains(",")) throw new IllegalArgumentException("Unsupported input path");
        var args=new ArrayList<>(List.of("docker","create","--name",name,"--runtime=runsc","--network=none","--read-only",
                "--user=10001:10001","--cap-drop=ALL","--security-opt=no-new-privileges","--pids-limit=256",
                "--memory="+memory+"m","--memory-swap="+memory+"m","--cpus="+cpu,
                "--ulimit=nofile=1024:1024","--ulimit=fsize=33554432:33554432","--log-driver=local","--log-opt=max-size=20m","--log-opt=max-file=1",
                "--tmpfs=/code:rw,nosuid,nodev,size="+(referenceData==null?805306368L:4294967296L)+",uid=10001,gid=10001,mode=0700",
                "--tmpfs=/output:rw,noexec,nosuid,nodev,size=33554432,uid=10001,gid=10001,mode=0700",
                "--tmpfs=/tmp:rw,nosuid,nodev,size=67108864,uid=10001,gid=10001,mode=0700",
                "--mount","type=bind,src="+input+",dst=/input/submission.zip,readonly",
                "--env","GRADE_TIMEOUT="+timeout,"--label=digital-grader.sandbox=true"));
        if (referenceData!=null) args.addAll(List.of("--mount","type=bind,src="+referenceData+",dst=/input/reference-data,readonly"));
        args.add(imageId);
        return List.copyOf(args);
    }
    @Override public Result run(Path archive) {
        if (imageId==null) throw new IllegalStateException("Sandbox readiness must be verified first");
        String name="digital-grader-grade-"+UUID.randomUUID();
        try {
            require(docker.run(command(name,archive),Duration.ofSeconds(15),1024));
            require(docker.run(List.of("docker","start",name),Duration.ofSeconds(15),1024));
            DockerCommand.Output waited;
            try { waited=docker.run(List.of("docker","wait",name),Duration.ofSeconds(timeout+30),1024); }
            catch (DockerCommand.DeadlineExceeded error) { return new Result(124,"Sandbox execution exceeded its deadline",List.of(),null,Map.of()); }
            require(waited);
            int exit=Integer.parseInt(waited.out().strip());
            if (exit==124 || exit==137) return new Result(exit,"Sandbox timed out or exhausted its memory limit",List.of(),null,Map.of());
            if (exit!=0 && exit!=1 && exit!=2) throw new IllegalStateException("Sandbox exited unexpectedly");
            var logs=docker.run(List.of("docker","logs",name),Duration.ofSeconds(15),12_000_000); require(logs);
            Result result=json.readValue(logs.out(),Result.class);
            if (result.exitCode()!=exit || result.log()==null || result.log().length()>200000 || result.junit()==null || result.junit().size()>1000 || result.quality()==null)
                throw new IllegalStateException("Invalid sandbox output");
            return result;
        } catch (java.io.IOException e) { throw new IllegalStateException("Invalid sandbox output",e); }
        finally {
            var removed=docker.run(List.of("docker","rm","--force",name),Duration.ofSeconds(15),1024);
            if (removed.code()!=0) throw new IllegalStateException("Sandbox cleanup failed; operator intervention required");
        }
    }
    private static void require(DockerCommand.Output result) { if (result.code()!=0) throw new IllegalStateException("Docker command failed"); }
}
