# Roadmap

Phase status for Digital Grader. Detailed reasoning lives in [DECISIONS](DECISIONS.md); setup and verification steps live in [README](README.md).

| Phase | Scope | Status |
|---|---|---|
| 1. Foundation | Repository, local infrastructure (PostgreSQL, RabbitMQ, MinIO), health endpoint, migrations, CI | Done |
| 2. Authentication | Registration, login, refresh token rotation, roles, cohort permissions, admin provisioning | Done |
| 3. Classroom workflows | Cohorts, enrollment, assignments, rubrics, validated ZIP intake, storage, outbox dispatch | Done |
| 4. Grading worker | gVisor sandbox execution, report parsing, scoring, artifact storage, result APIs | Done |
| 5. Security feedback | Offline vulnerability scanning, stored findings, scan status tracking | Done; verified against a live NVD snapshot |
| 6. AI hints | Configurable provider, bounded evidence, cached hints, output limits | Done; provider proven on the live deployment |
| 7. Connected workspace | Frontend API integration, uploads, results, instructor review history | Done; release acceptance verified on the live deployment |
| 8. Release readiness | Account recovery, email verification, abuse protection, production storage selection, deployment hardening, teacher-controlled test harness | Done: all items implemented and verified; a live end-to-end pass of account recovery on the deployed system is the only outstanding verification, scheduled with the pilot |

## Acceptance notes

- Phase 4 acceptance was verified with real containment on 2026-10-15 in a pilot environment: a submission uploaded through the public HTTPS API was graded by a worker on an Ubuntu laptop (Docker 29.1.3 with the `runsc` runtime) inside the gVisor sandbox, and the score, per-test results, and coverage were persisted and served back through the result API.
- Phase 5 acceptance was verified on 2026-11-01 against a current NVD snapshot: a submission uploaded through the public HTTPS API was scanned by the security worker and the stored report came back complete with zero findings for the sample project.
- Phase 6 acceptance was verified on 2026-11-01 on the live deployment: a hint was generated for a graded submission through an OpenAI-compatible provider with strict output validation.
- Phase 7 release acceptance was verified on 2026-11-01 end to end on the live deployment at grader.hamdam.me: a new student registered, verified through the real emailed link, submitted a project, received a 65/100 rubric score from the remote worker, got a completed dependency scan, and received an AI hint.
- Account recovery (forgot and reset password with single-use emailed tokens) is implemented and covered by tests; a live end-to-end pass is still pending.
- Student-controlled Maven projects can falsify their own reports. Authoritative grading requires a teacher-controlled test harness; this is implemented and merged.
- The hint provider output checks limit, but do not guarantee, tutoring quality or prevent solution disclosure.
