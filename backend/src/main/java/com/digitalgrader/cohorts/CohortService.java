package com.digitalgrader.cohorts;

import java.time.LocalDate;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.digitalgrader.common.ApiException;
import com.digitalgrader.users.User;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CohortService {
    public record Create(@NotBlank @Size(max=255) String name, @NotNull LocalDate startDate, @NotNull LocalDate endDate) {
        @JsonIgnore @AssertTrue public boolean isDateRangeValid() { return startDate == null || endDate == null || !endDate.isBefore(startDate); }
    }
    public record Members(@NotEmpty @Size(max=500) List<@NotNull @Positive Long> studentIds) {}
    public record Summary(long id, String name, LocalDate startDate, LocalDate endDate, int studentCount) {}
    public record Detail(long id, String name, long instructorId, LocalDate startDate, LocalDate endDate, List<User.Profile> members) {}
    private final JdbcTemplate jdbc;
    private final CohortRepository cohorts;
    private final CohortAccess access;
    public CohortService(JdbcTemplate jdbc, CohortRepository cohorts, CohortAccess access) { this.jdbc=jdbc; this.cohorts=cohorts; this.access=access; }
    public CohortRepository.Cohort create(Create request, User.Profile user) {
        long id = jdbc.queryForObject("INSERT INTO cohorts(name,instructor_id,start_date,end_date) VALUES (?,?,?,?) RETURNING id",
                Long.class, request.name().strip(), user.id(), request.startDate(), request.endDate());
        return cohorts.find(id).orElseThrow();
    }
    @Transactional
    public void addMembers(long id, Members request, User.Profile user) {
        access.requireManage(id, user);
        var ids = request.studentIds().stream().distinct().sorted().toList();
        for (long studentId : ids) {
            var roles = jdbc.queryForList("SELECT role FROM users WHERE id=? FOR SHARE", String.class, studentId);
            if (!roles.equals(List.of("STUDENT"))) throw new ApiException(HttpStatus.BAD_REQUEST, "Every member must be an existing student");
        }
        for (long studentId : ids) jdbc.update("INSERT INTO cohort_members(cohort_id,student_id) VALUES (?,?) ON CONFLICT DO NOTHING", id, studentId);
    }
    public List<Summary> mine(User.Profile user) {
        return jdbc.query("SELECT c.*, (SELECT count(*) FROM cohort_members m WHERE m.cohort_id=c.id) AS student_count FROM cohorts c WHERE instructor_id=? ORDER BY c.id",
                (row, i) -> new Summary(row.getLong("id"), row.getString("name"), row.getObject("start_date", LocalDate.class), row.getObject("end_date", LocalDate.class), row.getInt("student_count")), user.id());
    }
    @Transactional(readOnly=true)
    public Detail detail(long id, User.Profile user) {
        access.requireManage(id, user);
        var c = cohorts.find(id).orElseThrow();
        var members = jdbc.query("SELECT u.* FROM users u JOIN cohort_members m ON m.student_id=u.id WHERE m.cohort_id=? ORDER BY u.id",
                (row, i) -> new User.Profile(row.getLong("id"), row.getString("email"), row.getString("full_name"), com.digitalgrader.users.Role.valueOf(row.getString("role"))), id);
        return new Detail(c.id(), c.name(), c.instructorId(), c.startDate(), c.endDate(), members);
    }
}
