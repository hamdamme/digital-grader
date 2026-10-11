# Session 6 — grading worker (in progress)

October 10, 2026. Branch: `session-6/grading-worker`, based on merged PR #3.

## Implemented

The same Spring Boot codebase now supports a separate, non-HTTP worker process. It verifies Docker's `runsc` registration and resolves the sandbox image to an immutable image ID before starting its RabbitMQ listener. The API does not enable the worker by default.

The worker claims queued submissions atomically, downloads their ZIP into a private temporary directory, and mounts only that file read-only into a non-root gVisor container. Containers have no network, a read-only root filesystem, bounded writable tmpfs directories, dropped capabilities, no-new-privileges, PID/CPU/memory/file limits and a deadline. No student code or ZIP extraction runs on the worker host. Cleanup forcibly removes each container and deletes the temporary input.

The image contains Java 17, Maven, Chromium/ChromeDriver and cached dependencies for the sample TestNG/Selenium project. Maven runs offline. ZIP extraction is bounded and rejects traversal, symlinks and excessive expansion. Existing target directories are removed before building. JaCoCo produces XML; Checkstyle and SpotBugs supply quality reports.

The container emits bounded JSON containing reports before exit; Docker logs retain it until explicit cleanup. This replaces the specification's `docker cp` approach because writable tmpfs output does not survive container shutdown. Nothing copies an untrusted output directory onto the host.

JUnit results, JaCoCo coverage and rubric scores are persisted transactionally. Failed tests are rerun once in a fresh container; a subsequent pass is marked FLAKY and earns no pass credit. Skipped tests also earn no pass credit. Missing requested quality reports earn zero quality credit. A build failure is a completed grade with zero score. A deadline becomes TIMEOUT; infrastructure failures retry up to three attempts before FAILED. Expired claims are recovered, and claim tokens prevent a late worker from overwriting a newer attempt.

Logs and reports are stored privately in MinIO. The completed grade transaction also inserts a `security_outbox` row; publishing and consuming that security work belongs to Session 7.

## API additions

- `GET /api/v1/submissions/{id}/result`: persisted score, coverage, breakdown and individual test results; 409 until available.
- `GET /api/v1/submissions/{id}/build-log`: authorized five-minute download URL; 409 until available.
- Submission responses include `failureReason` for unsuccessful attempts.

These reuse submission ownership/cohort access checks. Security findings and hints remain empty until Session 7. The frontend remains unchanged and uses sample data.

## Run on a Linux host with gVisor

Prerequisites: Docker with Compose, registered `runsc`, Java 17, Maven 3.9 and Python 3. The worker and Docker daemon must use the same host filesystem for the input bind mount. A remote Docker socket alone is insufficient.

From the repository root:

```sh
docker build -f sandbox/Dockerfile.student -t digital-grader-student:session6 sandbox
./scripts/check-sandbox.sh
./scripts/backend.sh -B -ntp -Psandbox test
```

The sandbox profile runs two real runtime acceptance tests: a sample grade with failing/passing tests, coverage and quality reports, and a hanging test that must time out. Missing `runsc` is a failure, never a silent skip or fallback.

After those pass, initialize the local services as described in Session 2, then launch the API and worker in separate terminals:

```sh
./scripts/backend.sh spring-boot:run
# separate terminal, same checkout and .env
./scripts/worker.sh
```

Optional environment settings: `SANDBOX_IMAGE` (default `digital-grader-student:session6`), `SANDBOX_TIMEOUT_SECONDS` (default 600, range 5–600), `SANDBOX_MEMORY_MB` (default 1536, range 512–4096), `SANDBOX_CPU_LIMIT` (default 1, range 0.25–4). Java and Maven each use a bounded heap inside the sandbox.

Official runtime setup: https://gvisor.dev/docs/user_guide/quick_start/docker/. Ubuntu setup and acceptance are deferred until a suitable host is ready.

## Verification and stop point

The sandbox image built on Docker Desktop. All 76 standard backend tests pass with zero failures or skips; Maven verify succeeds. All three Python extraction checks pass. Standard backend tests exercise real PostgreSQL, MinIO and RabbitMQ, with a substituted sandbox for pipeline tests. They verify claims, retry limits, stale-claim fencing, stored artifacts/results, access controls and standalone listener startup. Default CI runs these checks; it does **not** validate gVisor execution.

The validation host had no `runsc`; the readiness script correctly rejected it. The two real sandbox acceptance tests remain unrun. Session 6 is therefore **in progress**, with five of eight sessions complete. New Session 6 changes have not been pushed or verified by remote CI.

## Prototype limits

- Only dependencies preloaded into the image are available offline. Assignments needing other dependencies require a reviewed image update.
- Student-controlled Maven projects can falsify their own reports. This is a prototype grading pipeline, not trustworthy assessment evidence. A teacher-controlled test harness and report provenance are release requirements.
- Full hostile-code containment, Selenium behavior under gVisor, real deadline/resource enforcement and end-to-end submission grading remain acceptance work on Linux.
- Worker crashes may leave containers or unpublished report objects. Containers carry `digital-grader.sandbox=true` for operator inspection; crash cleanup/reconciliation requires further work. Claim recovery prevents stale database writes but does not prove container cleanup after host failure.
- Security scans, AI guidance and real frontend integration remain Sessions 7–8.
