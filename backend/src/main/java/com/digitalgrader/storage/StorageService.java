package com.digitalgrader.storage;

import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import com.digitalgrader.common.ApiException;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.Http.Method;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class StorageService {
    private final MinioClient client;
    private final String bucket;
    public StorageService(@Value("${MINIO_ENDPOINT:http://127.0.0.1:19000}") String endpoint,
            @Value("${MINIO_ACCESS_KEY:}") String access, @Value("${MINIO_SECRET_KEY:}") String secret,
            @Value("${MINIO_BUCKET:submissions}") String bucket) {
        this.bucket=bucket;
        var http=new okhttp3.OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS)
                .writeTimeout(30,TimeUnit.SECONDS).callTimeout(60,TimeUnit.SECONDS).build();
        var builder=MinioClient.builder().endpoint(endpoint).httpClient(http);
        if (!access.isBlank() && !secret.isBlank()) builder.credentials(access,secret);
        client=builder.build();
    }
    public void ensureBucket() throws Exception {
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            try { client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build()); }
            catch (ErrorResponseException error) {
                if (!"BucketAlreadyOwnedByYou".equals(error.errorResponse().code())) throw error;
            }
        }
    }
    public void upload(String key, Path file) {
        try {
            ensureBucket();
            try (var input=Files.newInputStream(file)) {
                client.putObject(PutObjectArgs.builder().bucket(bucket).object(key).stream(input,Files.size(file),-1L).contentType("application/zip").build());
            }
        } catch (Exception error) { throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Submission storage is unavailable"); }
    }
    public void download(String key, Path destination) {
        try (var input=client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
                var output=Files.newOutputStream(destination)) {
            byte[] buffer=new byte[8192]; int n; long total=0;
            while ((n=input.read(buffer))!=-1) {
                total+=n;
                if (total>50L*1024*1024) throw new java.io.IOException("Stored archive exceeds limit");
                output.write(buffer,0,n);
            }
        } catch (Exception error) { throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Submission download failed"); }
    }
    public void uploadReport(String key, String content) {
        byte[] bytes=content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length>12_000_000) throw new IllegalArgumentException("Report exceeds limit");
        try (var input=new java.io.ByteArrayInputStream(bytes)) {
            ensureBucket();
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(key).stream(input,(long)bytes.length,-1L).contentType("text/plain; charset=utf-8").build());
        } catch (Exception error) { throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Report upload failed"); }
    }
    public void delete(String key) {
        try { client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build()); }
        catch (Exception error) { throw new IllegalStateException("Object cleanup failed",error); }
    }
    public String downloadUrl(String key) {
        try { return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().bucket(bucket).object(key).method(Method.GET).expiry(5,TimeUnit.MINUTES).build()); }
        catch (Exception error) { throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Submission storage is unavailable"); }
    }
}
