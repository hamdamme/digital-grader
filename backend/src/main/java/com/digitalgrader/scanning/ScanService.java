package com.digitalgrader.scanning;

import java.nio.file.*;
import com.digitalgrader.storage.StorageService;
import org.springframework.stereotype.Service;

@Service
public class ScanService {
    private final ScanRepository jobs;
    private final SecurityScanner scanner;
    private final StorageService storage;
    public ScanService(ScanRepository jobs,SecurityScanner scanner,StorageService storage) { this.jobs=jobs;this.scanner=scanner;this.storage=storage; }
    public boolean scanNext() {
        var claimed=jobs.claimNext();
        if (claimed.isEmpty()) return false;
        var job=claimed.get(); Path directory=null,input=null;
        try {
            directory=Files.createTempDirectory("digital-grader-scan-");
            input=Files.createFile(directory.resolve("submission.zip"));
            storage.download(job.storagePath(),input);
            Files.setPosixFilePermissions(input,java.nio.file.attribute.PosixFilePermissions.fromString("r--r--r--"));
            jobs.finish(job,scanner.scan(input));
        } catch (Exception error) {
            jobs.fail(job);
            org.slf4j.LoggerFactory.getLogger(ScanService.class).warn("Security attempt failed for submission {} ({})",job.id(),error.getClass().getSimpleName());
        } finally {
            if (input!=null) try { Files.deleteIfExists(input); } catch (java.io.IOException ignored) { }
            if (directory!=null) try { Files.deleteIfExists(directory); } catch (java.io.IOException ignored) { }
        }
        return true;
    }
}
