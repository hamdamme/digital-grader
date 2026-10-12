package com.digitalgrader.feedback;

import java.util.*;
import com.digitalgrader.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class HintRepository {
    public record Hint(String content,String model) {}
    private final JdbcTemplate jdbc;
    public HintRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Optional<Hint> cached(long id) {
        return jdbc.query("SELECT content,model FROM hints WHERE submission_id=?",(row,n) -> new Hint(row.getString(1),row.getString(2)),id).stream().findFirst();
    }
    public UUID claim(long id) {
        UUID token=UUID.randomUUID();
        int updated=jdbc.update("UPDATE submissions s SET hint_token=?,hint_started_at=now() WHERE id=? AND status='GRADED' AND EXISTS (SELECT 1 FROM security_outbox q WHERE q.submission_id=s.id AND q.status='COMPLETE') AND NOT EXISTS (SELECT 1 FROM hints h WHERE h.submission_id=s.id) AND (hint_token IS NULL OR hint_started_at<now()-interval '2 minutes') AND (hint_retry_at IS NULL OR hint_retry_at<=now())",token,id);
        if (updated==0) throw new ApiException(HttpStatus.CONFLICT,"Hint is pending; grading and security checks must finish first");
        return token;
    }
    @Transactional
    public Hint finish(long id,UUID token,HintProvider.Reply reply) {
        var rows=jdbc.queryForList("SELECT id FROM submissions WHERE id=? AND hint_token=? FOR UPDATE",Long.class,id,token);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.CONFLICT,"Hint request expired; retry the request");
        jdbc.update("INSERT INTO hints(submission_id,content,model) VALUES (?,?,?)",id,reply.content(),reply.model());
        jdbc.update("UPDATE submissions SET hint_token=NULL,hint_retry_at=NULL WHERE id=?",id);
        return new Hint(reply.content(),reply.model());
    }
    public void release(long id,UUID token) {
        jdbc.update("UPDATE submissions SET hint_token=NULL,hint_retry_at=now()+interval '10 seconds' WHERE id=? AND hint_token=?",id,token);
    }
    public Map<String,Object> context(long id) {
        var tests=jdbc.queryForList("SELECT left(test_name,160) AS name,status,left(coalesce(error_message,''),200) AS error FROM test_case_results WHERE grading_result_id=(SELECT id FROM grading_results WHERE submission_id=?) AND status IN ('FAILED','FLAKY') ORDER BY id LIMIT 8",id);
        var findings=jdbc.queryForList("SELECT source,cwe_id AS cwe,severity,left(file_path,160) AS file,line_number AS line FROM security_findings WHERE submission_id=? ORDER BY CASE severity WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END,id LIMIT 10",id);
        return Map.of("failingTests",tests,"findings",findings,"codeExcerpt","");
    }
}
