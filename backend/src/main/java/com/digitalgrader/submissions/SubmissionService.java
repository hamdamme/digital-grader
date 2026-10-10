package com.digitalgrader.submissions;

import java.time.Clock;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;
import com.digitalgrader.assignments.AssignmentService;
import com.digitalgrader.common.ApiException;
import com.digitalgrader.storage.StorageService;
import com.digitalgrader.users.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class SubmissionService {
    public record Accepted(long submissionId, SubmissionStatus status) {}
    public record Submission(long id, long assignmentId, long studentId, int attemptNumber, SubmissionStatus status, Instant submittedAt, BigDecimal score, String dispatchStatus, String failureReason) {}
    private static final String SELECT="SELECT s.*, r.score, CASE WHEN o.published_at IS NULL THEN 'PENDING' ELSE 'PUBLISHED' END AS dispatch_status FROM submissions s LEFT JOIN grading_results r ON r.submission_id=s.id LEFT JOIN submission_outbox o ON o.submission_id=s.id ";
    private static final RowMapper<Submission> MAPPER=(row,i) -> new Submission(row.getLong("id"),row.getLong("assignment_id"),row.getLong("student_id"),row.getInt("attempt_number"),SubmissionStatus.valueOf(row.getString("status")),row.getTimestamp("submitted_at").toInstant(),row.getBigDecimal("score"),row.getString("dispatch_status"),row.getString("grading_error"));
    private final JdbcTemplate jdbc;
    private final AssignmentService assignments;
    private final StorageService storage;
    private final Clock clock;
    public SubmissionService(JdbcTemplate jdbc, AssignmentService assignments, StorageService storage, Clock clock) { this.jdbc=jdbc; this.assignments=assignments; this.storage=storage; this.clock=clock; }
    public void requireStudentAssignment(long id, User.Profile user) {
        if (user.role()!=Role.STUDENT) throw new ApiException(HttpStatus.FORBIDDEN,"Only students can submit");
        assignments.get(id,user);
    }
    @Transactional
    public Accepted submit(long id, User.Profile user, ZipValidator.Validated file) {
        requireStudentAssignment(id,user);
        // One transaction per student serializes attempts even across multiple application instances.
        jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE",Long.class,user.id());
        var assignment=assignments.get(id,user);
        if (assignment.dueDate()!=null && !clock.instant().isBefore(assignment.dueDate())) throw new ApiException(HttpStatus.CONFLICT,"Assignment deadline has passed");
        int attempt=jdbc.queryForObject("SELECT coalesce(max(attempt_number),0)+1 FROM submissions WHERE assignment_id=? AND student_id=?",Integer.class,id,user.id());
        if (attempt>assignment.maxAttempts()) throw new ApiException(HttpStatus.CONFLICT,"Maximum submission attempts reached");
        String key="submissions/"+UUID.randomUUID()+".zip";
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                // Preserve the object if the database commit outcome is unknown.
                if (status==STATUS_ROLLED_BACK) try { storage.delete(key); }
                catch (RuntimeException ignored) { org.slf4j.LoggerFactory.getLogger(SubmissionService.class).warn("Submission object cleanup needs reconciliation: {}",key); }
            }
        });
        storage.upload(key,file.path());
        long submission=jdbc.queryForObject("INSERT INTO submissions(assignment_id,student_id,attempt_number,storage_path) VALUES (?,?,?,?) RETURNING id",Long.class,id,user.id(),attempt,key);
        jdbc.update("INSERT INTO submission_outbox(submission_id) VALUES (?)",submission);
        return new Accepted(submission,SubmissionStatus.QUEUED);
    }
    public Submission get(long id, User.Profile user) {
        var found=jdbc.query(SELECT+"WHERE s.id=?",MAPPER,id);
        if (found.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"Submission not found");
        var submission=found.get(0);
        if (user.role()==Role.STUDENT && user.id()!=submission.studentId()) throw new ApiException(HttpStatus.FORBIDDEN,"Access denied");
        assignments.get(submission.assignmentId(),user);
        return submission;
    }
    public List<Submission> mine(long assignmentId, User.Profile user) {
        requireStudentAssignment(assignmentId,user);
        return jdbc.query(SELECT+"WHERE s.assignment_id=? AND s.student_id=? ORDER BY s.attempt_number",MAPPER,assignmentId,user.id());
    }
    public String download(long id, User.Profile user) {
        get(id,user);
        return storage.downloadUrl(jdbc.queryForObject("SELECT storage_path FROM submissions WHERE id=?",String.class,id));
    }
    public boolean transition(long id, SubmissionStatus expected, SubmissionStatus next) {
        if (!expected.allows(next)) throw new IllegalArgumentException("Invalid submission transition");
        return jdbc.update("UPDATE submissions SET status=? WHERE id=? AND status=?",next.name(),id,expected.name())==1;
    }
}
