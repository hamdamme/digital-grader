package com.digitalgrader.grading;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;

@Component
public class DockerCommand {
    public record Output(int code,String out) {}
    public static class DeadlineExceeded extends IllegalStateException {
        public DeadlineExceeded() { super("Docker operation timed out"); }
    }
    public Output run(List<String> args,Duration timeout,int limit) {
        var readers=Executors.newFixedThreadPool(2);
        Process process=null;
        try {
            process=new ProcessBuilder(args).start(); final Process running=process;
            var out=readers.submit(() -> read(running.getInputStream(),limit,running));
            var err=readers.submit(() -> read(running.getErrorStream(),65536,running));
            if (!process.waitFor(timeout.toMillis(),TimeUnit.MILLISECONDS)) throw new DeadlineExceeded();
            String output=out.get(5,TimeUnit.SECONDS); err.get(5,TimeUnit.SECONDS);
            return new Output(process.exitValue(),output);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Docker operation interrupted",e); }
        catch (DeadlineExceeded e) { throw e; }
        catch (Exception e) { throw new IllegalStateException("Docker operation failed",e); }
        finally { if (process!=null && process.isAlive()) process.destroyForcibly(); readers.shutdownNow(); }
    }
    private String read(InputStream stream,int limit,Process process) throws IOException {
        try (stream; var bytes=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192]; int n;
            while ((n=stream.read(buffer))!=-1) {
                if (bytes.size()+n>limit) { process.destroyForcibly(); throw new IOException("Docker output exceeded limit"); }
                bytes.write(buffer,0,n);
            }
            return bytes.toString(StandardCharsets.UTF_8);
        }
    }
}
