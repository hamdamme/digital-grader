ALTER TABLE assignments ADD COLUMN harness_path TEXT;
ALTER TABLE assignments ADD COLUMN harness_uploaded_at TIMESTAMPTZ;
ALTER TABLE assignments ADD COLUMN harness_uploaded_by BIGINT REFERENCES users(id);
ALTER TABLE assignments ADD COLUMN harness_size BIGINT;
ALTER TABLE submissions ADD COLUMN graded_with_harness BOOLEAN NOT NULL DEFAULT FALSE;
