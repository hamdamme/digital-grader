package com.digitalgrader;

import java.io.*;
import java.util.zip.*;
import com.digitalgrader.submissions.ZipValidator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;

class ZipValidatorTest {
    private final ZipValidator validator=new ZipValidator();
    static byte[] zip(String... names) throws IOException {
        var bytes=new ByteArrayOutputStream();
        try (var zip=new ZipOutputStream(bytes)) {
            for (String name:names) { zip.putNextEntry(new ZipEntry(name)); zip.write("<project>fixture</project>".getBytes()); zip.closeEntry(); }
        }
        return bytes.toByteArray();
    }
    @Test void acceptsNormalZipAndDeletesTemporaryFile() throws Exception {
        java.nio.file.Path path;
        try (var accepted=validator.validate(new MockMultipartFile("file","project.zip","application/zip",zip("pom.xml","src/Test.java")))) {
            path=accepted.path(); assertThat(path).exists();
        }
        assertThat(path).doesNotExist();
    }
    @Test void rejectsUnsafePathsAndCaseCollisions() throws Exception {
        for (String name:new String[]{"../outside","/absolute","dir/../../outside","C:/outside","dir\\outside","dir//file"}) {
            byte[] bytes=zip(name);
            assertThatThrownBy(() -> validate(bytes)).hasMessageContaining("unsafe");
        }
        assertThatThrownBy(() -> validate(zip("pom.xml","POM.XML"))).hasMessageContaining("duplicate");
    }
    @Test void rejectsNonZipEmptyAndTruncatedArchives() throws Exception {
        assertThatThrownBy(() -> validate("not a zip".getBytes())).hasMessageContaining("Invalid ZIP");
        assertThatThrownBy(() -> validate(zip())).hasMessageContaining("contain files");
        byte[] bytes=zip("pom.xml");
        assertThatThrownBy(() -> validate(java.util.Arrays.copyOf(bytes,bytes.length-30))).isInstanceOf(com.digitalgrader.common.ApiException.class);
    }
    @Test void rejectsSymlinks() throws Exception {
        var bytes=new ByteArrayOutputStream();
        try (var zip=new org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream(bytes)) {
            var entry=new org.apache.commons.compress.archivers.zip.ZipArchiveEntry("link"); entry.setUnixMode(0120777);
            zip.putArchiveEntry(entry); zip.write("../outside".getBytes()); zip.closeArchiveEntry();
        }
        assertThatThrownBy(() -> validate(bytes.toByteArray())).hasMessageContaining("unsupported");
    }
    @Test void boundsDecompressionAndEntryCount() throws Exception {
        var bytes=new ByteArrayOutputStream();
        try (var zip=new ZipOutputStream(bytes)) { zip.putNextEntry(new ZipEntry("bomb")); zip.write(new byte[1024*1024]); zip.closeEntry(); }
        assertThatThrownBy(() -> validate(bytes.toByteArray())).hasMessageContaining("expands");
        String[] names=java.util.stream.IntStream.range(0,2001).mapToObj(i -> "file"+i).toArray(String[]::new);
        assertThatThrownBy(() -> validate(zip(names))).hasMessageContaining("too many");
    }
    @Test void checksCompressedUploadLimitBeforeReading() {
        var file=org.mockito.Mockito.mock(org.springframework.web.multipart.MultipartFile.class);
        org.mockito.Mockito.when(file.getSize()).thenReturn(ZipValidator.MAX_ARCHIVE+1);
        assertThatThrownBy(() -> validator.validate(file)).hasMessageContaining("50 MiB");
    }
    private void validate(byte[] bytes) throws Exception {
        try (var accepted=validator.validate(new MockMultipartFile("file","project.zip","application/zip",bytes))) { }
    }
}
