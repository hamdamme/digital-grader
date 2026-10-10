package com.digitalgrader.grading;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.digitalgrader.assignments.Rubric;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class GradingRepository {
    public record Job(long id,UUID token,String storagePath,Rubric rubric) {}
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public GradingRepository(JdbcTemplate jdbc,ObjectMapper json) { this.jdbc=jdbc; this.json=json; }
    @Transactional
    public Optional<Job> claim(long id) {
        UUID token=UUID.randomUUID();
        var rows=jdbc.queryForList("UPDATE submissions SET status='RUNNING',grading_token=?,grading_started_at=now(),grading_attempts=grading_attempts+1,grading_error=NULL WHERE id=? AND status='QUEUED' AND grading_attempts<3 RETURNING storage_path,assignment_id",token,id);
        if (rows.isEmpty()) return Optional.empty();
        try {
            var row=rows.get(0);
            String rubric=jdbc.queryForObject("SELECT rubric_json::text FROM assignments WHERE id=?",String.class,row.get("assignment_id"));
            return Optional.of(new Job(id,token,(String)row.get("storage_path"),json.readValue(rubric,Rubric.class)));
        } catch (java.io.IOException e) { throw new IllegalStateException("Invalid assignment rubric",e); }
    }
    @Transactional
    public boolean finish(Job job,List<ReportParser.TestCase> tests,ReportParser.Coverage coverage,Scoring.Score score,boolean buildSuccess,String logKey) {
        var ids=jdbc.queryForList("SELECT id FROM submissions WHERE id=? AND grading_token=? AND status='RUNNING' FOR UPDATE",Long.class,job.id(),job.token());
        if (ids.isEmpty()) return false;
        try {
            long result=jdbc.queryForObject("INSERT INTO grading_results(submission_id,build_success,build_log_path,tests_total,tests_passed,tests_failed,tests_flaky,coverage_line_pct,coverage_branch_pct,score,rubric_breakdown) VALUES (?,?,?,?,?,?,?,?,?,?,?::jsonb) RETURNING id",
                    Long.class,job.id(),buildSuccess,logKey,tests.size(),count(tests,"PASSED"),count(tests,"FAILED"),count(tests,"FLAKY"),coverage.line(),coverage.branch(),score.score(),json.writeValueAsString(score));
            for (var test:tests) jdbc.update("INSERT INTO test_case_results(grading_result_id,test_name,status,duration_ms,error_message,stack_trace) VALUES (?,?,?,?,?,?)",result,test.name(),test.status(),test.durationMs(),test.message(),test.stackTrace());
            jdbc.update("UPDATE submissions SET status='GRADED',grading_token=NULL WHERE id=?",job.id());
            jdbc.update("INSERT INTO security_outbox(submission_id) VALUES (?) ON CONFLICT DO NOTHING",job.id());
            return true;
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    @Transactional
    public void fail(Job job,boolean timeout) {
        int changed=jdbc.update("UPDATE submissions SET status=CASE WHEN ? THEN 'TIMEOUT' WHEN grading_attempts>=3 THEN 'FAILED' ELSE 'QUEUED' END,grading_token=NULL,grading_error=? WHERE id=? AND grading_token=? AND status='RUNNING'",
                timeout,timeout ? "Sandbox time limit exceeded" : "Grading infrastructure/report error",job.id(),job.token());
        if (changed==1) jdbc.update("UPDATE submission_outbox SET published_at=NULL,next_attempt_at=now()+interval '10 seconds' WHERE submission_id=? AND EXISTS(SELECT 1 FROM submissions WHERE id=? AND status='QUEUED')",job.id(),job.id());
    }
    @Transactional
    public int recoverStale(int ageSeconds) {
        var ids=jdbc.queryForList("UPDATE submissions SET status=CASE WHEN grading_attempts>=3 THEN 'FAILED' ELSE 'QUEUED' END,grading_token=NULL,grading_error='Worker lease expired' WHERE status='RUNNING' AND grading_started_at < now()-(? * interval '1 second') RETURNING id",Long.class,ageSeconds);
        for (long id:ids) jdbc.update("UPDATE submission_outbox SET published_at=NULL,next_attempt_at=now() WHERE submission_id=? AND EXISTS(SELECT 1 FROM submissions WHERE id=? AND status='QUEUED')",id,id);
        return ids.size();
    }
    private int count(List<ReportParser.TestCase> tests,String status) { return (int)tests.stream().filter(t -> t.status().equals(status)).count(); }
}
