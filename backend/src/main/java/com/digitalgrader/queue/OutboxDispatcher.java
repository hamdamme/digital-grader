package com.digitalgrader.queue;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxDispatcher {
    private final JdbcTemplate jdbc;
    private final RabbitPublisher publisher;
    public OutboxDispatcher(JdbcTemplate jdbc, RabbitPublisher publisher) { this.jdbc=jdbc; this.publisher=publisher; }
    @Transactional
    public boolean dispatchOne() {
        var ids=jdbc.queryForList("SELECT submission_id FROM submission_outbox WHERE published_at IS NULL AND next_attempt_at<=now() ORDER BY next_attempt_at LIMIT 1 FOR UPDATE SKIP LOCKED",Long.class);
        if (ids.isEmpty()) return false;
        long id=ids.get(0);
        try {
            publisher.publish(id);
            jdbc.update("UPDATE submission_outbox SET published_at=now(),attempts=attempts+1 WHERE submission_id=?",id);
        } catch (RuntimeException error) {
            jdbc.update("UPDATE submission_outbox SET attempts=attempts+1,next_attempt_at=now()+interval '10 seconds' WHERE submission_id=?",id);
            org.slf4j.LoggerFactory.getLogger(OutboxDispatcher.class).warn("Submission {} remains pending dispatch; retry scheduled",id);
        }
        return true;
    }
}
