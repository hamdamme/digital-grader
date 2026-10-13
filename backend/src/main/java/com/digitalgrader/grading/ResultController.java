package com.digitalgrader.grading;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.digitalgrader.common.ApiException;
import com.digitalgrader.storage.StorageService;
import com.digitalgrader.submissions.SubmissionService;
import com.digitalgrader.users.User;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/submissions")
public class ResultController {
    private final SubmissionService submissions;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final StorageService storage;
    private final com.digitalgrader.review.ReviewService reviews;
    private final com.digitalgrader.scanning.ScanRepository scans;
    public ResultController(SubmissionService submissions,JdbcTemplate jdbc,ObjectMapper json,StorageService storage,com.digitalgrader.scanning.ScanRepository scans,com.digitalgrader.review.ReviewService reviews) { this.submissions=submissions; this.jdbc=jdbc; this.json=json; this.storage=storage; this.scans=scans; this.reviews=reviews; }
    @GetMapping("/{id}/result")
    public Map<String,Object> result(@PathVariable long id,@AuthenticationPrincipal User.Profile user) {
        submissions.get(id,user);
        var rows=jdbc.queryForList("SELECT * FROM grading_results WHERE submission_id=?",id);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.CONFLICT,"Grading result is not available yet");
        var row=rows.get(0); long result=((Number)row.get("id")).longValue();
        var response=new LinkedHashMap<String,Object>();
        response.put("submissionId",id); response.put("buildSuccess",row.get("build_success"));
        response.put("testsTotal",row.get("tests_total")); response.put("testsPassed",row.get("tests_passed")); response.put("testsFailed",row.get("tests_failed")); response.put("testsFlaky",row.get("tests_flaky"));
        response.put("coverageLinePct",row.get("coverage_line_pct")); response.put("coverageBranchPct",row.get("coverage_branch_pct")); response.put("score",row.get("score")); response.put("gradedAt",row.get("graded_at").toString());
        try { response.put("rubricBreakdown",json.readTree(row.get("rubric_breakdown").toString())); }
        catch (java.io.IOException e) { throw new IllegalStateException("Invalid stored grading result",e); }
        response.put("testCaseResults",jdbc.queryForList("SELECT test_name AS \"testName\",status,duration_ms AS \"durationMs\",error_message AS \"errorMessage\",stack_trace AS \"stackTrace\" FROM test_case_results WHERE grading_result_id=? ORDER BY id",result));
        var overrides=reviews.history(id);
        response.put("overrides",overrides);
        response.put("effectiveScore",overrides.isEmpty()?row.get("score"):overrides.get(0).get("score"));
        response.put("securityStatus",scans.status(id));
        response.put("securityFindings",scans.findings(id));
        response.put("hints",jdbc.queryForList("SELECT content,model,created_at AS \"createdAt\" FROM hints WHERE submission_id=?",id));
        return response;
    }
    @GetMapping("/{id}/build-log")
    public Map<String,Object> buildLog(@PathVariable long id,@AuthenticationPrincipal User.Profile user) {
        submissions.get(id,user);
        var paths=jdbc.queryForList("SELECT build_log_path FROM grading_results WHERE submission_id=?",String.class,id);
        if (paths.isEmpty() || paths.get(0)==null) throw new ApiException(HttpStatus.CONFLICT,"Build log is not available yet");
        return Map.of("url",storage.downloadUrl(paths.get(0)),"expiresInSeconds",300);
    }
}
