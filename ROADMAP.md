# Roadmap

Phase status for Digital Grader. Detailed reasoning lives in [DECISIONS](DECISIONS.md); setup and verification steps live in [README](README.md).

| Phase | Scope | Status |
|---|---|---|
| 1. Foundation | Repository, local infrastructure (PostgreSQL, RabbitMQ, MinIO), health endpoint, migrations, CI | Done |
| 2. Authentication | Registration, login, refresh token rotation, roles, cohort permissions, admin provisioning | Done |
| 3. Classroom workflows | Cohorts, enrollment, assignments, rubrics, validated ZIP intake, storage, outbox dispatch | Done |
| 4. Grading worker | gVisor sandbox execution, report parsing, scoring, artifact storage, result APIs | Done |
| 5. Security feedback | Offline vulnerability scanning, stored findings, scan status tracking | Implemented; live snapshot refresh pending |
| 6. AI hints | Configurable provider, bounded evidence, cached hints, output limits | Implemented; provider evaluation pending |
| 7. Connected workspace | Frontend API integration, uploads, results, instructor review history | Implemented locally; release acceptance pending |
| 8. Release readiness | Account recovery, email verification, abuse protection, production storage selection, deployment hardening, teacher-controlled test harness | Not started |

## Acceptance notes

- Phase 4 acceptance was verified with real containment on 2026-10-15 in a pilot environment: a submission uploaded through the public HTTPS API was graded by a worker on an Ubuntu laptop (Docker 29.1.3 with the `runsc` runtime) inside the gVisor sandbox, and the score, per-test results, and coverage were persisted and served back through the result API. Phases 5 and 6 are validated by the standard test suite with a substituted execution boundary.
- Student-controlled Maven projects can falsify their own reports. Authoritative grading requires a teacher-controlled test harness; this is release work.
- The hint provider output checks limit, but do not guarantee, tutoring quality or prevent solution disclosure.
