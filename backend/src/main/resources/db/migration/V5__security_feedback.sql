ALTER TABLE security_outbox
    ADD COLUMN status VARCHAR(12) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','RUNNING','COMPLETE','FAILED')),
    ADD COLUMN claim_token UUID,
    ADD COLUMN started_at TIMESTAMPTZ,
    ADD COLUMN attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN completed_at TIMESTAMPTZ,
    ADD COLUMN failure_reason VARCHAR(100);
CREATE INDEX idx_security_pending ON security_outbox(next_attempt_at) WHERE status='PENDING';
CREATE UNIQUE INDEX idx_hints_submission ON hints(submission_id);
ALTER TABLE submissions
    ADD COLUMN hint_token UUID,
    ADD COLUMN hint_started_at TIMESTAMPTZ,
    ADD COLUMN hint_retry_at TIMESTAMPTZ;
