package com.gradely.grading;

import java.nio.file.*;
import java.util.*;
import com.gradely.storage.StorageService;
import org.springframework.stereotype.Service;

@Service
public class GradingService {
    private final GradingRepository jobs;
    private final Sandbox sandbox;
    private final StorageService storage;
    public GradingService(GradingRepository jobs,Sandbox sandbox,StorageService storage) { this.jobs=jobs; this.sandbox=sandbox; this.storage=storage; }
    public void grade(long id) {
        var claimed=jobs.claim(id);
        if (claimed.isEmpty()) return;
        var job=claimed.get(); Path input=null; Path directory=null;
        try {
            directory=Files.createTempDirectory("gradely-grade-");
            input=Files.createFile(directory.resolve("submission.zip"));
            storage.download(job.storagePath(),input);
            // The non-root container must be able to read this single, read-only mounted archive.
            Files.setPosixFilePermissions(input,java.nio.file.attribute.PosixFilePermissions.fromString("r--r--r--"));
            var first=sandbox.run(input);
            if (first.exitCode()==124) { jobs.fail(job,true); return; }
            if (first.exitCode()==137) throw new IllegalStateException("Sandbox memory limit exceeded");
            var tests=ReportParser.junit(first.junit());
            String prefix="results/"+id+"/"+job.token()+"/";
            persistArtifacts(prefix+"first/",first);
            if (tests.stream().anyMatch(t -> t.status().equals("FAILED"))) {
                var retry=sandbox.run(input);
                persistArtifacts(prefix+"retry/",retry);
                if (retry.exitCode()==0 || retry.exitCode()==1) tests=Scoring.markFlaky(tests,ReportParser.junit(retry.junit()));
            }
            var coverage=ReportParser.jacoco(first.jacoco());
            var score=Scoring.calculate(job.rubric(),tests,coverage,first.quality(),first.exitCode()==0 || first.exitCode()==1);
            jobs.finish(job,tests,coverage,score,first.exitCode()==0,prefix+"first/build.log");
        } catch (Exception error) {
            jobs.fail(job,false);
            org.slf4j.LoggerFactory.getLogger(GradingService.class).warn("Grading attempt failed for submission {} ({})",id,error.getClass().getSimpleName());
        } finally {
            if (input!=null) try { Files.deleteIfExists(input); } catch (java.io.IOException ignored) { }
            if (directory!=null) try { Files.deleteIfExists(directory); } catch (java.io.IOException ignored) { }
        }
    }
    private void persistArtifacts(String prefix,Sandbox.Result result) {
        long deadline=System.nanoTime()+java.time.Duration.ofSeconds(60).toNanos();
        upload(prefix+"build.log",result.log(),deadline);
        for (int i=0;i<result.junit().size();i++) upload(prefix+"junit/"+i+".xml",result.junit().get(i),deadline);
        if (result.jacoco()!=null) upload(prefix+"jacoco.xml",result.jacoco(),deadline);
        for (String tool:List.of("checkstyle","spotbugs")) if (result.quality().containsKey(tool)) upload(prefix+tool+".xml",result.quality().get(tool),deadline);
    }
    private void upload(String key,String content,long deadline) {
        if (System.nanoTime()>deadline) throw new IllegalStateException("Artifact publication deadline exceeded");
        storage.uploadReport(key,content);
    }
}
