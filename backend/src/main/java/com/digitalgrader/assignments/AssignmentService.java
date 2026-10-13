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
    public record Assignment(long id, long cohortId, String title, String description, Rubric rubricJson, Instant dueDate, int maxAttempts, String buildCommand) {}
    private final JdbcTemplate jdbc;
    private final CohortAccess access;
    private final ObjectMapper json;
    private final RowMapper<Assignment> mapper;
    public AssignmentService(JdbcTemplate jdbc, CohortAccess access, ObjectMapper json) {
        this.jdbc=jdbc; this.access=access; this.json=json;
        mapper=(row,i) -> {
            try { return new Assignment(row.getLong("id"),row.getLong("cohort_id"),row.getString("title"),row.getString("description"),
                    json.readValue(row.getString("rubric_json"),Rubric.class), row.getTimestamp("due_date") == null ? null : row.getTimestamp("due_date").toInstant(), row.getInt("max_attempts"),row.getString("build_command")); }
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
    private void requireRead(long cohortId, User.Profile user) {
        if (!access.canRead(cohortId,user)) throw new ApiException(HttpStatus.FORBIDDEN,"Access denied");
    }
}
