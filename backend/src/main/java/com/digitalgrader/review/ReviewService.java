package com.digitalgrader.review;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import com.digitalgrader.cohorts.CohortAccess;
import com.digitalgrader.common.ApiException;
import com.digitalgrader.submissions.SubmissionService;
import com.digitalgrader.users.*;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {
    public record OverrideRequest(@NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=2) BigDecimal score,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal expectedScore,
            @NotBlank @Size(max=2000) String reason) {}
    private final JdbcTemplate jdbc;
    private final CohortAccess access;
    private final SubmissionService submissions;
    public ReviewService(JdbcTemplate jdbc,CohortAccess access,SubmissionService submissions) {
        this.jdbc=jdbc; this.access=access; this.submissions=submissions;
    }
    public List<Map<String,Object>> list(long cohortId,User.Profile user) {
        if (!access.canRead(cohortId,user)) throw new ApiException(HttpStatus.FORBIDDEN,"Access denied");
        String scope=user.role()==Role.STUDENT ? " AND s.student_id=?" : "";
        String sql="""
            SELECT s.id, s.assignment_id AS "assignmentId", a.title AS "assignmentTitle",
                s.student_id AS "studentId", u.full_name AS "studentName", s.attempt_number AS "attemptNumber",
                s.status, s.submitted_at AS "submittedAt", r.score AS "automatedScore",
                r.coverage_line_pct AS "coverageLinePct", coalesce(q.status,'PENDING') AS "securityStatus",
                CASE WHEN q.status='COMPLETE' THEN (SELECT count(*) FROM security_findings f WHERE f.submission_id=s.id) ELSE NULL END AS "findingCount",
                coalesce((SELECT o.new_score FROM instructor_overrides o WHERE o.grading_result_id=r.id ORDER BY o.id DESC LIMIT 1),r.score) AS score
            FROM submissions s JOIN assignments a ON a.id=s.assignment_id JOIN users u ON u.id=s.student_id
                LEFT JOIN grading_results r ON r.submission_id=s.id LEFT JOIN security_outbox q ON q.submission_id=s.id WHERE a.cohort_id=?
            """;
        return jdbc.queryForList(sql+scope+" ORDER BY s.submitted_at DESC,s.id DESC",user.role()==Role.STUDENT ? new Object[]{cohortId,user.id()} : new Object[]{cohortId});
    }
    public List<Map<String,Object>> dashboard(long cohortId,User.Profile user) {
        access.requireManage(cohortId,user);
        return jdbc.queryForList("""
            SELECT u.id AS "studentId", u.full_name AS "fullName", count(s.id) AS "totalSubmissions",
                avg(coalesce((SELECT o.new_score FROM instructor_overrides o WHERE o.grading_result_id=r.id ORDER BY o.id DESC LIMIT 1),r.score)) AS "avgScore",
                avg(r.coverage_line_pct) AS "avgCoverage", max(s.submitted_at) AS "lastSubmissionAt",
                count(s.id) FILTER (WHERE q.status='COMPLETE') AS "completedScans",
                sum(CASE WHEN q.status='COMPLETE' THEN (SELECT count(*) FROM security_findings f WHERE f.submission_id=s.id) ELSE NULL END) AS "totalFindings"
            FROM cohort_members m JOIN users u ON u.id=m.student_id
                LEFT JOIN submissions s ON s.student_id=m.student_id AND s.assignment_id IN (SELECT id FROM assignments WHERE cohort_id=?)
                LEFT JOIN grading_results r ON r.submission_id=s.id LEFT JOIN security_outbox q ON q.submission_id=s.id
            WHERE m.cohort_id=? GROUP BY u.id,u.full_name ORDER BY u.full_name,u.id
            """,cohortId,cohortId);
    }
    public List<Map<String,Object>> heatmap(long cohortId,User.Profile user) {
        access.requireManage(cohortId,user);
        return jdbc.queryForList("""
            SELECT coalesce(f.cwe_id,'Unclassified') AS "cweId", count(*) AS count
            FROM security_findings f JOIN submissions s ON s.id=f.submission_id JOIN assignments a ON a.id=s.assignment_id
                JOIN security_outbox q ON q.submission_id=s.id AND q.status='COMPLETE'
            WHERE a.cohort_id=? GROUP BY f.cwe_id ORDER BY count(*) DESC,f.cwe_id
            """,cohortId);
    }
    public List<Map<String,Object>> history(long submissionId) {
        return jdbc.queryForList("""
            SELECT o.id, o.original_score AS "originalScore", o.new_score AS score, o.reason,
                u.full_name AS "instructorName", o.created_at AS "createdAt"
            FROM instructor_overrides o JOIN grading_results r ON r.id=o.grading_result_id
                JOIN users u ON u.id=o.instructor_id WHERE r.submission_id=? ORDER BY o.id DESC
            """,submissionId);
    }
    @Transactional
    public void override(long id,OverrideRequest request,User.Profile user) {
        if (user.role()==Role.STUDENT) throw new ApiException(HttpStatus.FORBIDDEN,"Access denied");
        var submission=submissions.get(id,user);
        if (submission.status()!=com.digitalgrader.submissions.SubmissionStatus.GRADED)
            throw new ApiException(HttpStatus.CONFLICT,"Only completed grades can be reviewed");
        var rows=jdbc.queryForList("SELECT id,score FROM grading_results WHERE submission_id=? FOR UPDATE",id);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.CONFLICT,"Grading result is not available yet");
        var row=rows.get(0);
        var previous=history(id);
        BigDecimal current=(BigDecimal)(previous.isEmpty()?row.get("score"):previous.get(0).get("score"));
        if (request.expectedScore().compareTo(current)!=0) throw new ApiException(HttpStatus.CONFLICT,"The score changed. Reload the result before saving");
        jdbc.update("INSERT INTO instructor_overrides(grading_result_id,original_score,new_score,reason,instructor_id) VALUES (?,?,?,?,?)",
                row.get("id"),current,request.score(),request.reason().strip(),user.id());
    }
}
