ALTER TABLE submissions ADD COLUMN grading_token UUID;
ALTER TABLE submissions ADD COLUMN grading_started_at TIMESTAMPTZ;
ALTER TABLE submissions ADD COLUMN grading_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE submissions ADD COLUMN grading_error VARCHAR(100);
CREATE INDEX idx_submissions_grading_lease ON submissions(grading_started_at) WHERE status='RUNNING';
CREATE TABLE security_outbox (
    submission_id BIGINT PRIMARY KEY REFERENCES submissions(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
