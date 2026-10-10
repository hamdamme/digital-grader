package com.digitalgrader.submissions;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.zip.CRC32;
import com.digitalgrader.common.ApiException;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ZipValidator {
    public static final long MAX_ARCHIVE=50L*1024*1024;
    public static final long MAX_EXPANDED=200L*1024*1024;
    public record Validated(Path path) implements AutoCloseable {
        @Override public void close() {
            try { Files.deleteIfExists(path); }
            catch (IOException error) { org.slf4j.LoggerFactory.getLogger(ZipValidator.class).warn("Temporary upload cleanup failed: {}",path); }
        }
    }
    public Validated validate(MultipartFile file) {
        if (file.isEmpty() || file.getSize()>MAX_ARCHIVE) throw invalid("ZIP must be non-empty and at most 50 MiB");
        Path path=null;
        try {
            path=Files.createTempFile("digital-grader-upload-", ".zip");
            try (var input=file.getInputStream(); var output=Files.newOutputStream(path)) {
                byte[] buffer=new byte[8192]; long copied=0; int n;
                while ((n=input.read(buffer))!=-1) { copied+=n; if (copied>MAX_ARCHIVE) throw invalid("ZIP exceeds 50 MiB"); output.write(buffer,0,n); }
            }
            inspect(path);
            return new Validated(path);
        } catch (IOException | RuntimeException error) {
            if (path != null) try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            if (error instanceof ApiException api) throw api;
            throw invalid("Invalid ZIP archive");
        }
    }
    private void inspect(Path path) throws IOException {
        try (var zip=ZipFile.builder().setPath(path).get()) {
            var entries=zip.getEntries(); var names=new HashSet<String>();
            int count=0, files=0; long expanded=0;
            while (entries.hasMoreElements()) {
                var entry=entries.nextElement(); String name=entry.getName();
                // Commons Compress normalizes DOS backslashes; reject them in the original name too.
                String rawName=new String(entry.getRawName(),java.nio.charset.StandardCharsets.UTF_8);
                if (rawName.contains("\\")) throw invalid("ZIP contains unsafe paths");
                if (++count>2000) throw invalid("ZIP has too many entries");
                String clean=entry.isDirectory() && name.endsWith("/") ? name.substring(0,name.length()-1) : name;
                if (clean.isBlank() || name.startsWith("/") || name.contains("\\") || name.contains(":")
                        || name.chars().anyMatch(c -> c<32) || Arrays.stream(clean.split("/",-1)).anyMatch(p -> p.isEmpty() || p.equals(".") || p.equals(".."))
                        || !names.add(clean.toLowerCase(Locale.ROOT))) throw invalid("ZIP contains unsafe or duplicate paths");
                int kind=entry.getUnixMode() & 0170000;
                if (entry.isUnixSymlink() || (kind!=0 && kind!=0100000 && kind!=0040000) || !zip.canReadEntryData(entry))
                    throw invalid("ZIP contains unsupported entries");
                if (entry.isDirectory()) continue;
                files++;
                var crc=new CRC32(); long size=0;
                try (var stream=zip.getInputStream(entry)) {
                    byte[] buffer=new byte[8192]; int n;
                    while ((n=stream.read(buffer))!=-1) {
                        size+=n; expanded+=n;
                        if (expanded>MAX_EXPANDED || size>MAX_ARCHIVE || size>Math.max(1,entry.getCompressedSize())*100)
                            throw invalid("ZIP expands beyond permitted limits");
                        crc.update(buffer,0,n);
                    }
                }
                if (size!=entry.getSize() || crc.getValue()!=entry.getCrc()) throw invalid("ZIP data is corrupt");
            }
            if (files==0) throw invalid("ZIP must contain files");
        }
    }
    private ApiException invalid(String message) { return new ApiException(HttpStatus.BAD_REQUEST,message); }
}
