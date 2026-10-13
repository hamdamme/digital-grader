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
| 8 | Frontend integration and release preparation | Connected workspace verified locally; release acceptance pending |

Digital Grader is the current project name. The repository and local folder rename are complete. Existing data compatibility settings are described in [Renaming](RENAMING.md).

## Verification

Validation on October 12 passed 99 backend tests and 24 frontend tests. Maven verify and the frontend production build succeeded. The browser workflow passed against disposable services for registration, classroom setup, uploads, lists and score overrides. Browser result review used stored report fixtures; no submitted code ran. The previous three ZIP extraction and three scanner smoke tests passed during Session 7.

The three real gVisor acceptance tests (two grading and one security) have not run. Ubuntu setup is deferred until a suitable host is ready. Session 6 remains incomplete; successful ordinary tests do not establish containment or real grading behavior.

## Current behavior

The default frontend connects to the API for accounts, classroom setup, uploads, result views and instructor overrides. Sample data is isolated under the demo routes. Uploads remain queued unless a worker is running on a supported host. The worker requires runsc and refuses to substitute another runtime. Security processing and an optional hint provider are implemented locally; runtime acceptance is pending and hint generation is disabled by default. Student reports are not yet authoritative assessment evidence; a test harness controlled by instructors remains a release requirement.

## Next work

Session 8 adds the connected workspace and persistent score reviews. See [Session 8](SESSION_8.md) for setup and verification, and [Project Overview](PROJECT_OVERVIEW.md) for presentation material. Ubuntu, vulnerability snapshot and live provider acceptance remain deferred.

The current branch is `session-8/frontend-integration`, based on the pushed Session 7 branch. [Session 7 backend and scanner CI](https://github.com/hamdamme/digital-grader/actions/runs/38298745556) passed at commit 9870535. Session 8 has not been pushed or checked by remote CI. Five sessions are fully complete; Sessions 6–8 retain runtime or release acceptance work. Next: push Session 8, verify CI, and review the branch before merging.
