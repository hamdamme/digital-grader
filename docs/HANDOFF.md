# Project status

Updated October 12, 2026. This file is the current checkpoint; session documents describe individual milestones.

## Completed work

| Session | Scope | Status |
|---|---|---|
| 1 | React prototype, repository, license and frontend tests | Complete |
| 2 | Infrastructure, health endpoint and database foundation | Complete; merged through PR #1 |
| 3 | Authentication and permissions | Complete; merged through PR #3 |
| 4 | Teacher cohorts, enrollment and assignments | Complete; merged through PR #3 |
| 5 | ZIP uploads, private storage and queue dispatch | Complete; merged through PR #3 |
| 6 | Grading worker, report parsing, scoring and result APIs | Implemented locally; Linux acceptance pending |
| 7 | Security scans and AI guidance | Implemented locally; runtime and provider acceptance pending |
| 8 | Frontend integration and release preparation | Planned |

Digital Grader is the current project name. The repository and local folder rename are complete. Existing data compatibility settings are described in [Renaming](RENAMING.md).

## Verification

Validation on October 12 passed 94 backend tests, three ZIP extraction tests and three scanner smoke tests. Maven verify and the security image build succeeded. Backend integration tests used disposable PostgreSQL, RabbitMQ and MinIO containers; provider tests used a local HTTP fixture. Grading orchestration substituted the sandbox. The previous frontend validation passed 13 tests and a production build on October 10; frontend code was unchanged this session.

The three real gVisor acceptance tests (two grading and one security) have not run. Ubuntu setup is deferred until a suitable host is ready. Session 6 remains incomplete; successful ordinary tests do not establish containment or real grading behavior.

## Current behavior

The frontend uses sample data and is not connected to the API. Backend uploads are real, but remain queued unless a worker is running on a supported host. The worker requires runsc and refuses to substitute another runtime. Security processing and an optional hint provider are implemented locally; runtime acceptance is pending and hint generation is disabled by default. Student reports are not yet authoritative assessment evidence; a test harness controlled by instructors remains a release requirement.

## Next work

Session 7 adds a security image, durable scan claims, findings in result responses and cached tutoring hints. See [Session 7](SESSION_7.md) for configuration, data transfer behavior and acceptance limits. The Ubuntu host, vulnerability snapshot and live provider checks remain deferred. Frontend integration is still planned for Session 8.

The current branch is `session-7/security-feedback`, based on the pushed Session 6 branch. The previous [backend CI run](https://github.com/hamdamme/digital-grader/actions/runs/38173723914) passed at commit b52db27. Session 7 changes have not been pushed or checked by remote CI. Five sessions are complete; Sessions 6 and 7 retain outstanding acceptance work.
