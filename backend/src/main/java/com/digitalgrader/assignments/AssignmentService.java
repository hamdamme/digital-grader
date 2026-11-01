package com.digitalgrader.assignments;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.digitalgrader.cohorts.CohortAccess;
import com.digitalgrader.common.ApiException;
import com.digitalgrader.users.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class AssignmentService {
    public record Create(@Positive long cohortId, @NotBlank @Size(max=255) String title, @Size(max=20000) String description,
            @NotNull @Valid Rubric rubricJson, Instant dueDate, @NotNull @Min(1) @Max(20) Integer maxAttempts,
            @NotNull @Pattern(regexp="mvn -B test") String buildCommand) {
        public Create { if (maxAttempts == null) maxAttempts=3; if (buildCommand == null) buildCommand="mvn -B test"; }
    }
    public record Harness(Instant uploadedAt,long sizeBytes) {}
    public record Assignment(long id, long cohortId, String title, String description, Rubric rubricJson, Instant dueDate, int maxAttempts, String buildCommand, Harness harness) {}
    private final JdbcTemplate jdbc;
    private final CohortAccess access;
    private final ObjectMapper json;
    private final com.digitalgrader.storage.StorageService storage;
    private final RowMapper<Assignment> mapper;
    public AssignmentService(JdbcTemplate jdbc, CohortAccess access, ObjectMapper json, com.digitalgrader.storage.StorageService storage) {
        this.jdbc=jdbc; this.access=access; this.json=json; this.storage=storage;
        mapper=(row,i) -> {
            try {
                var uploaded=row.getTimestamp("harness_uploaded_at");
                var harness=row.getString("harness_path")==null ? null : new Harness(uploaded.toInstant(),((Number)row.getObject("harness_size",Long.class)).longValue());
                return new Assignment(row.getLong("id"),row.getLong("cohort_id"),row.getString("title"),row.getString("description"),
                    json.readValue(row.getString("rubric_json"),Rubric.class), row.getTimestamp("due_date") == null ? null : row.getTimestamp("due_date").toInstant(), row.getInt("max_attempts"),row.getString("build_command"),harness); }
            catch (java.io.IOException error) { throw new java.sql.SQLException("Invalid stored rubric",error); }
        };
    }
    public Assignment create(Create request, User.Profile user) {
        access.requireManage(request.cohortId(),user);
        try {
            long id=jdbc.queryForObject("INSERT INTO assignments(cohort_id,title,description,rubric_json,due_date,max_attempts,build_command) VALUES (?,?,?,?::jsonb,?,?,?) RETURNING id",
                    Long.class,request.cohortId(),request.title().strip(),request.description(),json.writeValueAsString(request.rubricJson()),
                    request.dueDate() == null ? null : Timestamp.from(request.dueDate()),request.maxAttempts(),request.buildCommand());
            return get(id,user);
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) { throw new IllegalStateException(error); }
    }
    public List<Assignment> list(long cohortId, User.Profile user) {
        requireRead(cohortId,user);
        return jdbc.query("SELECT * FROM assignments WHERE cohort_id=? ORDER BY id",mapper,cohortId);
    }
    public Assignment get(long id, User.Profile user) {
        var values=jdbc.query("SELECT * FROM assignments WHERE id=?",mapper,id);
        if (values.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"Assignment not found");
        requireRead(values.get(0).cohortId(),user);
        return values.get(0);
    }
    @org.springframework.transaction.annotation.Transactional
    public Harness attachHarness(long id, User.Profile user, java.nio.file.Path file) {
        var values=jdbc.queryForList("SELECT cohort_id,harness_path FROM assignments WHERE id=?",id);
        if (values.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"Assignment not found");
        access.requireManage((Long)values.get(0).get("cohort_id"),user);
        final String key="harness/"+id+"/"+java.util.UUID.randomUUID()+".zip";
        final String previous=(String)values.get(0).get("harness_path");
        final long size;
        try { size=java.nio.file.Files.size(file); } catch (java.io.IOException error) { throw new IllegalStateException(error); }
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status==STATUS_ROLLED_BACK) try { storage.delete(key); }
                catch (RuntimeException ignored) { org.slf4j.LoggerFactory.getLogger(AssignmentService.class).warn("Harness object cleanup needs reconciliation: {}",key); }
                if (status==STATUS_COMMITTED && previous!=null) try { storage.delete(previous); }
                catch (RuntimeException ignored) { org.slf4j.LoggerFactory.getLogger(AssignmentService.class).warn("Previous harness object cleanup needs reconciliation: {}",previous); }
            }
        });
        storage.upload(key,file);
        jdbc.update("UPDATE assignments SET harness_path=?,harness_uploaded_at=now(),harness_uploaded_by=?,harness_size=? WHERE id=?",key,user.id(),size,id);
        return new Harness(java.time.Instant.now(),size);
    }
    @org.springframework.transaction.annotation.Transactional
    public void removeHarness(long id, User.Profile user) {
        var values=jdbc.queryForList("SELECT cohort_id,harness_path FROM assignments WHERE id=?",id);
        if (values.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"Assignment not found");
        access.requireManage((Long)values.get(0).get("cohort_id"),user);
        final String key=(String)values.get(0).get("harness_path");
        if (key==null) return;
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status==STATUS_COMMITTED) try { storage.delete(key); }
                catch (RuntimeException ignored) { org.slf4j.LoggerFactory.getLogger(AssignmentService.class).warn("Harness object cleanup needs reconciliation: {}",key); }
            }
        });
        jdbc.update("UPDATE assignments SET harness_path=NULL,harness_uploaded_at=NULL,harness_uploaded_by=NULL,harness_size=NULL WHERE id=?",id);
    }
    private void requireRead(long cohortId, User.Profile user) {
        if (!access.canRead(cohortId,user)) throw new ApiException(HttpStatus.FORBIDDEN,"Access denied");
    }
}
