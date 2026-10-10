# Gradely handoff

Current status is the Session 6 checkpoint below; older dated sections are historical.

## Delivered
- Local repository at Desktop/gradely on main, with MIT license, ignore rules and EditorConfig.
- Source build specification preserved alongside decisions and a four-day plan.
- Student assignments, active/completed filtering, file selection and simulated submission states.
- Demonstration results with test failure details, a security finding, sample guidance and progress view.
- Responsive styling, visible prototype labeling, skip navigation and keyboard controls for result tabs.

## Verification
- JavaScript syntax check passed with Node.
- Browser confirmed assignment filtering, results rendering, security tab, hint reveal and missing-file validation.
- Narrow viewport visually inspected.
- Source formatted with Prettier 3.3.3.
- Full file-selection simulation was not verified: the browser file chooser lost its tab session. No real grading is implemented.
- Production acceptance tests T01–T32 have not been run or satisfied.

## Remaining setup
The owner successfully authenticated as hamdamme in Terminal. The agent command environment cannot use that authentication, and Terminal UI access is blocked. No remote repository or push has been confirmed. From the authenticated Terminal, run: `gh repo create gradely --public --source=/Users/nazar/Desktop/gradely --remote=origin --push`. The original Desktop/grader folder is retained as a backup; continue work in Desktop/gradely.

## Stop point
Stop implementation here for today. Next session begins with design feedback, the GitHub setup if still pending, and the React/TypeScript port. Instructor flows, login and backend features remain planned work. Do not create artificial revisions to fill the schedule.

## Design iteration
Replaced the original green cards with a light developer workspace. Checked assignment filters, result/security navigation and progress values in the browser; no browser errors were reported during these checks. Narrow layout visually checked; desktop content checked with a viewport override, though screenshot capture clipped to the app panel. Corrected malformed attempt/score pairs so progress shows 68 and 86. JavaScript syntax and Git whitespace checks pass.

## React foundation checkpoint
- Replaced imperative HTML rendering with typed React pages, shared components and hash routing; retained the approved CSS.
- All 11 tests pass, including file limits, queued/running/results transitions, timer cancellation, assignment filtering, keyboard tabs and missing routes. These tests use jsdom, not a native file chooser.
- TypeScript check and Vite production build pass; npm audit reports zero vulnerabilities.
- Frontend CI workflow is committed but has not run on GitHub.
- The React preview rendered assignment content in the browser; subsequent screenshot/interaction checks were interrupted when the browser session lost the tab.
- Use `cd frontend && npm ci && npm run dev`; new preview is http://127.0.0.1:5176. Python preview instructions are obsolete.
- GitHub authentication is still inaccessible to the agent process; no remote or push is confirmed.

Stop here. Next implementation checkpoint is the Spring Boot service and local infrastructure, beginning with health/configuration and database migrations. Authentication and real submissions follow only after those checks pass.

## Teacher prototype checkpoint
Added cohort overview and split source/results review. Refined the design after owner feedback: charcoal sidebar, blue controls, plain sans-serif type and simpler copy. Production preview: http://127.0.0.1:5175/#/studio and http://127.0.0.1:5175/#/review/demo-003. Existing student preview remains available via navigation. All 13 tests pass; build passes. Browser confirmed updated navigation labels and review styling. No backend or real grade changes are implemented. Notes are never sent to students.

## Session 2 — October 4, 2026
Current checkout: /Users/nazar/Desktop/PROJECTS/gradely. Branch: session-2/backend-foundation. See SESSION_2.md for current setup instructions.

Verified: frontend GitHub Actions succeeded before this session; PostgreSQL/RabbitMQ/MinIO all healthy; RabbitMQ and MinIO consoles return HTTP 200; RabbitMQ authenticated health returns ok; backend health returns HTTP 200/UP; protected routes return 401; startup without JWT_SECRET fails with the variable named; Flyway creates 11 application tables plus its history table; all 17 backend tests pass without skips. Initial MinIO source build succeeded. Production image/provider selection remains open because community MinIO is archived.

New backend CI workflow is added but has not run remotely. Frontend files were not modified. .env, caches and build output are ignored. Untracked gradely_mindtek contains user files and was left untouched. Stop at this checkpoint; next is authentication and access controls. Services are left running for inspection.

## Session 3 — October 9, 2026
Session 2 was merged through PR #1; branch and PR backend checks passed. Session 3 is now complete locally on session-3/authentication. See SESSION_3.md for endpoints, optional first-admin configuration, token limitations and the restart procedure. All 45 backend tests and Maven verify pass. No real accounts were created; integration fixtures used disposable databases. Frontend and user-owned gradely_mindtek files were untouched. Session 3 has not been pushed or checked by remote CI. Stop here; Session 4 is teacher cohort/member/assignment management.

## Sessions 4 and 5 — October 9, 2026
Branch session-4-5/teacher-submissions continues Session 3 history. Delivered teacher cohorts/enrollment, assignment/rubric APIs, Swagger UI, bounded ZIP validation, real MinIO storage, durable RabbitMQ outbox dispatch, submission reads and conditional status transitions. All 62 tests pass against disposable services. See SESSION_4_5.md for limits, setup and failure semantics. No grading worker or frontend API integration exists yet; submissions stay QUEUED. Local commits only; push/remote CI remain pending. Stop here before Session 6.


## Session 6 — October 10, 2026 (in progress)

PR #3 is confirmed merged at a940c4cfbe406f506a54ba918b6fc6327f0aa858. Sessions 1–5 are complete. Current branch: session-6/grading-worker, created from origin/main. Eight focused local commits cover the grading image, parsers/scoring, Docker execution, claim persistence, results, listener, acceptance tests and documentation. No Session 6 push or remote CI run has been verified.

Delivered: standalone non-HTTP worker, mandatory runsc readiness gate, offline sandbox image, bounded reports, JUnit/JaCoCo/quality parsing, one flaky-test rerun, weighted scoring, private artifacts, authorized result/log endpoints, bounded retries, stale-claim token fencing and transactional security handoff. The image builds. All 76 standard backend tests pass without failures/skips, Maven verify succeeds, and three Python extraction checks pass. Frontend unchanged.

Docker Desktop is running (29.0.1), but only runc/containerd runtimes are registered. No student submission was executed using them. The two real sandbox acceptance tests are still unrun; Session 6 is not complete. The owner has another computer running Linux and was asked to identify its distribution with cat /etc/os-release. Next: prepare that host with Docker and gVisor, build the image, run the sandbox profile, then verify an end-to-end real submission. Full commands and prototype limits are in SESSION_6.md.

Preserved unrelated untracked gradely_mindtek/ and docs/.Rhistory. No real accounts or student submissions were created. Tests used disposable containers. Do not treat student-supplied reports as trusted grades; teacher-owned test execution remains a release requirement.
