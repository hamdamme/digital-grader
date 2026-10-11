# Project status

Updated October 11, 2026. This file is the current checkpoint; session documents describe individual milestones.

## Completed work

| Session | Scope | Status |
|---|---|---|
| 1 | React prototype, repository, license and frontend tests | Complete |
| 2 | Infrastructure, health endpoint and database foundation | Complete; merged through PR #1 |
| 3 | Authentication and permissions | Complete; merged through PR #3 |
| 4 | Teacher cohorts, enrollment and assignments | Complete; merged through PR #3 |
| 5 | ZIP uploads, private storage and queue dispatch | Complete; merged through PR #3 |
| 6 | Grading worker, report parsing, scoring and result APIs | Implemented locally; Linux acceptance pending |
| 7 | Security scans and AI guidance | Planned |
| 8 | Frontend integration and release preparation | Planned |

Digital Grader is the current project name. The repository and local folder rename are complete. Existing data compatibility settings are described in [Renaming](RENAMING.md).

## Verification

The latest recorded validation on October 10 passed 76 backend tests, 13 frontend tests and three ZIP extraction tests. Maven verify, the frontend production build and the renamed sandbox image build succeeded. Backend integration tests used disposable PostgreSQL, RabbitMQ and MinIO containers. Grading orchestration tests substituted the sandbox.

The two real gVisor acceptance tests have not run. Ubuntu setup is deferred until a suitable host is ready. Session 6 remains incomplete; successful ordinary tests do not establish containment or real grading behavior.

## Current behavior

The frontend uses sample data and is not connected to the API. Backend uploads are real, but remain queued unless a worker is running on a supported host. The worker requires runsc and refuses to substitute another runtime. Security processing and AI guidance are not implemented. Student reports are not yet authoritative assessment evidence; a test harness controlled by instructors remains a release requirement.

## Next work

Independent Session 7 work can proceed while Ubuntu setup is deferred. Resume Session 6 acceptance by preparing Linux with Docker and gVisor, running the sandbox profile, and verifying an actual submission through the full pipeline. See [Worker setup](SESSION_6.md).

The current local branch is `session-6/grading-worker`. Its worker and rename commits were last recorded as unpublished; a subsequent push and remote CI result have not been verified. Check the remote before treating local work as released.
