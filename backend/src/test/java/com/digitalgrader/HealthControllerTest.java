package com.digitalgrader;

import com.digitalgrader.common.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HealthControllerTest {
    @Test void returnsUnavailableWithoutDatabaseDetails() {
        JdbcTemplate database = mock(JdbcTemplate.class);
        when(database.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new DataAccessResourceFailureException("sensitive connection detail"));
        var response = new HealthController(database).health();
        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody()).containsExactlyEntriesOf(java.util.Map.of("status", "DOWN"));
    }
}
