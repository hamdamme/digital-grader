# Sessions 4 and 5 — teacher APIs and real submissions

Completed locally October 9, 2026 (America/Chicago), on `session-4-5/teacher-submissions`, based on the completed Session 3 branch. All 62 backend tests pass. The frontend remains the approved sample-data prototype; these are backend milestones.

## Session 4: teacher workflows

Authenticated instructors/admins can create cohorts and enroll existing students. Enrollment validates the entire batch before writing, rejects instructor/admin members, ignores duplicates, and requires cohort ownership (or ADMIN). Cohort detail includes public member profiles, and `/cohorts/mine` includes student counts.

Assignments belong to a cohort. Owners/admins create them; enrolled students, owners and admins can read them. Rubric weights must be integer percentages in [0,100] totaling 100; coverage threshold is [0,100]; quality checks are unique entries from checkstyle/spotbugs. Attempts default to 3 and range from 1 to 20. The supported build command is `mvn -B test`; arbitrary shell commands are rejected. This milestone implements the specification's create/enroll/read contracts; editing/deleting cohorts and assignments is not included.

Swagger UI is available at `/swagger-ui/index.html`; its schema is `/v3/api-docs`. Use Authorize to enter an access token from the Session 3 login endpoint. Documentation is readable without login, while application endpoints still enforce authentication/permissions. Do not paste tokens into shared screenshots.

| Method | Path under `/api/v1` | Request / result |
|---|---|---|
| POST | `/cohorts` | `{name,startDate,endDate}` → 201 cohort; end must not precede start |
| POST | `/cohorts/{id}/members` | `{studentIds:[...]}` (1–500) → 204 |
| GET | `/cohorts/mine` | Owned cohorts with studentCount |
| GET | `/cohorts/{id}` | Cohort and public member profiles |
| POST | `/assignments` | `{cohortId,title,description,rubricJson,dueDate,maxAttempts,buildCommand}` → 201 |
| GET | `/assignments?cohortId=...` | Authorized cohort assignments |
| GET | `/assignments/{id}` | Authorized assignment |

## Session 5: uploads and dispatch

| Method | Path under `/api/v1` | Request / result |
|---|---|---|
| POST | `/assignments/{id}/submissions` | Multipart `file` → 202 `{submissionId,status:"QUEUED"}` |
| GET | `/assignments/{id}/mysubmissions` | Student's attempts, statuses and nullable score |
| GET | `/submissions/{id}` | Authorized submission status and dispatchStatus |
| GET | `/submissions/{id}/download` | Authorized, five-minute signed download URL |

Only enrolled students can upload. Requests after a deadline or after maxAttempts return 409. Attempts are serialized by locking the student's row within the transaction; concurrent requests cannot consume the same last attempt. Students see only their own submissions; the owning instructor and admins can read/download them. Student membership is rechecked on reads.

ZIP validation happens before storage: maximum archive size 50 MiB; 2,000 entries; 200 MiB total expanded data; 50 MiB per expanded file; maximum 100:1 per-entry expansion ratio. Unsafe paths, duplicate case-folded names, symlinks/special entries, unsupported encryption/compression, empty archives and corrupt/truncated ZIP data are rejected where detected. Central directory names and original DOS backslashes are checked; size and CRC are verified while reading entries. Files are never extracted or executed by the API. The Session 6 worker must independently validate and contain extraction. Highly compressible legitimate files can exceed the conservative ratio and be rejected.

Uploads use temporary files, then stream to a private MinIO object under a server-generated UUID key. Bucket creation is lazy and idempotent on first upload. No public bucket policy or presigned PUT upload endpoint is added; direct client uploads would bypass validation and need a separate staging/finalization design. Git URL ingestion remains deferred; this milestone supports ZIP multipart requests only. Temporary file cleanup is attempted after processing; cleanup failures are logged without changing an already-accepted response.

The database transaction inserts both the submission and its dispatch row (Flyway V3 `submission_outbox`). A scheduled dispatcher polls every two seconds, publishes a persistent `{submissionId}` message to durable queue `digital-grader.submissions`, and marks dispatch complete only after a broker confirm without a return. Failed publishes remain durable and retry after ten seconds. `dispatchStatus` is PENDING or PUBLISHED; 202 means accepted into durable application work, not that a grading result exists.

Delivery is at least once: a crash after RabbitMQ acknowledges but before the database commit may publish twice. The worker must claim with the conditional QUEUED → RUNNING transition and handle recovery/idempotency. A test verifies only one claim succeeds. Terminal transitions are RUNNING → GRADED/FAILED/TIMEOUT and cannot be reversed through this service. There is no public status-write endpoint and no worker yet, so real uploads remain QUEUED.

Confirmed database rollback performs best-effort removal of the object; an unknown commit outcome preserves it for reconciliation. That cleanup is tested, but object storage and PostgreSQL cannot share an atomic commit: a process crash or storage outage can leave an orphan object. Production reconciliation/lifecycle cleanup is still needed. Do not configure automatic deletion of all objects while submissions reference them.

## Run and verify

From the repository root, with Docker Desktop running and the generated private `.env` from Session 2:

```sh
docker compose up -d --build --wait --wait-timeout 180
./scripts/backend.sh -B -ntp verify
./scripts/backend.sh spring-boot:run
```

Stop an older backend in its own terminal before starting the new build on port 8080. Restart applies migrations; a previously running process is not automatically updated. Use the optional first-admin setup in SESSION_3.md, then provision an instructor and students through the API. No accounts or real submissions were seeded during development.

Integration tests require local image `digital-grader-minio:2025-10-15` (Compose builds it). To build only that prerequisite:

```sh
docker build -t digital-grader-minio:2025-10-15 infra/minio
```

Tests start disposable PostgreSQL, MinIO and RabbitMQ containers with isolated fixture credentials. The backend CI workflow now builds that pinned MinIO image before running tests. This source build adds CI time and requires upstream download access; remote CI has not yet been run for this branch.

Storage uses MINIO_ENDPOINT, MINIO_ACCESS_KEY, MINIO_SECRET_KEY and MINIO_BUCKET; queue connectivity uses RABBIT_HOST/PORT/USERNAME/PASSWORD from the existing private environment. Broker confirms and returns are enabled. Multipart files spill to disk, with 50 MiB file / 51 MiB whole-request limits. For tests or diagnostics, `--digital-grader.dispatch.enabled=false` disables scheduled publishing without deleting pending work.

Verification: 62 tests, no skips; real HTTP upload → exact MinIO download bytes → confirmed persistent RabbitMQ message; unauthorized reads/uploads; invalid archive paths/symlinks/bombs; deadline/attempt rules; concurrent last-attempt requests; failed storage and DB rollback; object cleanup; failed dispatch retry; status transitions; and existing auth/database tests. Failure injection uses spies at service boundaries; the successful storage/broker path uses real containers.

## Stop point

Sessions 3–5 were merged through PR #3 with successful backend CI. Intake itself executes no student code. Session 6 adds grading and worker recovery; its Linux acceptance remains pending. Security scans, AI guidance and frontend integration follow. See [project status](HANDOFF.md).
