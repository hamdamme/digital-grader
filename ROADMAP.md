# Roadmap

Phase status for Digital Grader. Detailed reasoning lives in [DECISIONS](DECISIONS.md); setup and verification steps live in [README](README.md).

| Phase | Scope | Status |
|---|---|---|
| 1. Foundation | Repository, local infrastructure (PostgreSQL, RabbitMQ, MinIO), health endpoint, migrations, CI | Done |
| 2. Authentication | Registration, login, refresh token rotation, roles, cohort permissions, admin provisioning | Done |
| 3. Classroom workflows | Cohorts, enrollment, assignments, rubrics, validated ZIP intake, storage, outbox dispatch | Done |
| 4. Grading worker | gVisor sandbox execution, report parsing, scoring, artifact storage, result APIs | Implemented; Linux gVisor acceptance pending |
| 5. Security feedback | Offline vulnerability scanning, stored findings, scan status tracking | Implemented; live snapshot refresh pending |
| 6. AI hints | Configurable provider, bounded evidence, cached hints, output limits | Implemented; provider evaluation pending |
| 7. Connected workspace | Frontend API integration, uploads, results, instructor review history | Implemented locally; release acceptance pending |
| 8. Release readiness | Account recovery, email verification, abuse protection, production storage selection, deployment hardening, teacher-controlled test harness | Not started |

## Acceptance notes

- Phases 4 and 5 are validated by the standard test suite with a substituted execution boundary. Real containment acceptance requires a Linux host with `runsc` and the `sandbox` Maven profile.
- Student-controlled Maven projects can falsify their own reports. Authoritative grading requires a teacher-controlled test harness; this is release work.
- The hint provider output checks limit, but do not guarantee, tutoring quality or prevent solution disclosure.
