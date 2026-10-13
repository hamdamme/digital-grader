package com.digitalgrader.scanning;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ScanRepository {
    public record Job(long id,UUID token,String storagePath) {}
    private final JdbcTemplate jdbc;
    public ScanRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Transactional
    public Optional<Job> claimNext() {
        var ids=jdbc.queryForList("SELECT submission_id FROM security_outbox WHERE status='PENDING' AND next_attempt_at<=now() AND attempts<3 ORDER BY next_attempt_at LIMIT 1 FOR UPDATE SKIP LOCKED",Long.class);
        if (ids.isEmpty()) return Optional.empty();
        long id=ids.get(0); UUID token=UUID.randomUUID();
        jdbc.update("UPDATE security_outbox SET status='RUNNING',claim_token=?,started_at=now(),attempts=attempts+1,failure_reason=NULL WHERE submission_id=?",token,id);
        return Optional.of(new Job(id,token,jdbc.queryForObject("SELECT storage_path FROM submissions WHERE id=?",String.class,id)));
    }
    @Transactional
    public boolean finish(Job job,List<ScanReports.Finding> findings) {
        var rows=jdbc.queryForList("SELECT submission_id FROM security_outbox WHERE submission_id=? AND claim_token=? AND status='RUNNING' FOR UPDATE",Long.class,job.id(),job.token());
        if (rows.isEmpty()) return false;
        for (var f:findings) jdbc.update("INSERT INTO security_findings(submission_id,source,rule_id,cwe_id,severity,file_path,line_number,message,fix_guidance,dependency_name,current_version,fixed_version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                job.id(),f.source(),f.ruleId(),f.cweId(),f.severity(),f.filePath(),f.lineNumber(),f.message(),f.fixGuidance(),f.dependencyName(),f.currentVersion(),f.fixedVersion());
        jdbc.update("UPDATE security_outbox SET status='COMPLETE',claim_token=NULL,completed_at=now() WHERE submission_id=?",job.id());
        return true;
    }
    public void fail(Job job) {
        jdbc.update("UPDATE security_outbox SET status=CASE WHEN attempts>=3 THEN 'FAILED' ELSE 'PENDING' END,claim_token=NULL,next_attempt_at=now()+interval '30 seconds',failure_reason='Security scan unavailable or incomplete' WHERE submission_id=? AND claim_token=? AND status='RUNNING'",job.id(),job.token());
    }
    public int recoverStale() {
        return jdbc.update("UPDATE security_outbox SET status=CASE WHEN attempts>=3 THEN 'FAILED' ELSE 'PENDING' END,claim_token=NULL,next_attempt_at=now(),failure_reason='Security worker lease expired' WHERE status='RUNNING' AND started_at<now()-interval '20 minutes'");
    }
    public String status(long id) {
        var rows=jdbc.queryForList("SELECT status FROM security_outbox WHERE submission_id=?",String.class,id);
        return rows.isEmpty()?"PENDING":rows.get(0);
    }
    public List<Map<String,Object>> findings(long id) {
        return jdbc.queryForList("SELECT source,rule_id AS \"ruleId\",cwe_id AS \"cweId\",severity,file_path AS \"filePath\",line_number AS \"lineNumber\",message,fix_guidance AS \"fixGuidance\",dependency_name AS \"dependencyName\",current_version AS \"currentVersion\",fixed_version AS \"fixedVersion\" FROM security_findings WHERE submission_id=? ORDER BY CASE severity WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END,id",id);
    }
}
