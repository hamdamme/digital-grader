package com.digitalgrader.cohorts;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class CohortRepository {
    public record Cohort(long id, String name, long instructorId, LocalDate startDate, LocalDate endDate) {}
    private static final RowMapper<Cohort> MAPPER = (row, index) -> new Cohort(row.getLong("id"), row.getString("name"),
            row.getLong("instructor_id"), row.getObject("start_date", LocalDate.class), row.getObject("end_date", LocalDate.class));
    private final JdbcTemplate jdbc;
    public CohortRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public List<Cohort> forInstructor(long id) { return jdbc.query("SELECT * FROM cohorts WHERE instructor_id=? ORDER BY id", MAPPER, id); }
    public Optional<Cohort> find(long id) { return jdbc.query("SELECT * FROM cohorts WHERE id=?", MAPPER, id).stream().findFirst(); }
    public boolean hasStudent(long cohortId, long userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM cohort_members WHERE cohort_id=? AND student_id=?)",
                Boolean.class, cohortId, userId));
    }
}
