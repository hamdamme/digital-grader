# Session 8 — connected workspace and review

October 12, 2026. Branch: `session-8/frontend-integration`, based on the pushed Session 7 branch. Implementation and local browser verification are complete for the scope below. Release acceptance remains open.

## Connected flows

The default frontend is connected to the API. It supports student registration, sign-in, cohort selection, assignments, ZIP uploads, submission history, progress, and result review. Instructors can create cohorts, enroll existing student IDs, create assignments with rubrics, and record score overrides. Instructor provisioning remains an administrator API operation described in [authentication setup](SESSION_3.md).

The charcoal sidebar and light workspace remain. Loading, empty, denied, unavailable, queued, failed, and completed states use API responses. Sample pages are separate at `/#/demo/studio` and `/#/demo/assignments`; they do not submit or grade projects.

Student progress includes score, coverage and finding trends ordered by submission. Instructor dashboards use database aggregates for student summaries and CWE counts. Scans that have not completed are excluded from security counts and shown as pending. Tracking individual finding fixes remains planned; no fix rate is estimated. Native SVG and meter charts keep the existing interface lightweight, with text values available for accessibility.

Submission lists refresh every five seconds. Result pages show tests, coverage, scan status, stored guidance, and review history. Downloads use the existing authorized API to obtain links valid for five minutes. Source browsing is still unavailable. Guidance requires a configured provider and completed grading and scanning.

Score overrides require an instructor with cohort access or an administrator, a completed grade, a score from 0 to 100, and a reason. Each change appends an audit record. The original automated grade is retained. The API locks the result and checks the expected score to reject concurrent stale changes. Students can read their own override history but cannot write it.

## API additions

| Endpoint | Behavior |
|---|---|
| GET `/api/v1/cohorts/mine` | Student memberships, instructor cohorts, or all cohorts for administrators |
| GET `/api/v1/cohorts/{id}/dashboard` | Student aggregates for owning instructor or administrator |
| GET `/api/v1/cohorts/{id}/vulnerability-heatmap` | CWE counts across completed cohort scans |
| GET `/api/v1/submissions?cohortId=...` | Student sees own submissions; owning instructor and administrator see the cohort |
| POST `/api/v1/submissions/{id}/overrides` | Append a score and reason with `expectedScore`; returns 204 |
| GET `/api/v1/submissions/{id}/result` | Adds `effectiveScore` and `overrides`; `score` remains the automated grade |

Lists currently return the accessible cohort records without pagination. Large cohort performance and pagination remain release work. Dashboard averages include every scored attempt, including overrides, and are labeled accordingly.

## Session and network behavior

Access and refresh tokens stay in browser memory. They are never placed in local storage or session storage. Reloading requires sign-in. Concurrent expired requests share one refresh operation. Logout clears local state immediately and attempts to revoke the refresh token; existing access tokens retain the backend's documented expiry behavior.

API calls have deadlines, and reads are canceled when their view changes. Network failures are not retried automatically for mutations because the request may already have completed. A 401 can trigger one refresh and retry. Error messages render as text, including student supplied test reports.

Development uses the Vite `/api` proxy to `http://127.0.0.1:8080`. See `frontend/.env.example` for overrides. Production hosting must route `/api` to the backend or configure `VITE_API_BASE_URL` with the corresponding backend CORS origin. The Vite development proxy is not a production server.

## Verification

- Maven verify: 99 backend tests passed, with no failures or skips in the default suite.
- Frontend: 24 tests passed; TypeScript and production build passed.
- Browser workflow: passed with disposable PostgreSQL, MinIO, and RabbitMQ, the built API, Vite, and headless Chrome.
- Browser coverage: registration, sign-in, cohort creation, enrollment, assignment creation, ZIP upload, queued state, teacher submission list, persisted score override, mobile account controls, and reload sign-out.
- Report data for browser review is inserted as an explicit fixture in the disposable database. No student code executes in this check. Grading and scanning workers are disabled.

Run ordinary checks:

```sh
./scripts/backend.sh -B -ntp verify
npm --prefix frontend test
npm --prefix frontend run build
```

Run the browser workflow after building the backend JAR:

```sh
docker build -t digital-grader-minio:2025-10-15 infra/minio
cd frontend
npm ci
npx playwright install chromium
cd ..
python3 scripts/browser-smoke.py
```

Alternatively set `SMOKE_CHROME_PATH` to an installed Chrome executable. The script creates unique disposable containers, uses generated credentials and random loopback ports, and removes its services after completion. Logs and screenshots remain in ignored `.data/browser-smoke-*` directories. It does not touch the normal application database. A dedicated GitHub workflow runs this check on relevant changes.

## Stop point

Session 8 is saved locally and has not been pushed or verified by remote CI. Session 7 passed [backend and scanner CI](https://github.com/hamdamme/digital-grader/actions/runs/38298745556).

The complete grading and security flow still needs Linux with runsc, a current vulnerability snapshot, and live provider acceptance. Ubuntu setup remains deferred. Before a production release, also address trusted instructor test harnesses, account recovery, email verification, abuse controls, backups, deployment configuration, and large cohort performance. No production release or merge is claimed.

For a presentation to Mindtek or a portfolio audience, use [Project Overview and Development Report](PROJECT_OVERVIEW.md).

## Remaining specification items

The original build specification is broader than this checkpoint. Individual finding fix rates, full application container packaging (T29), measured backend service coverage at the requested threshold (T30), and staging acceptance of actual sandbox results (T31) remain open. The browser check uses real APIs and report fixtures, not a live grading worker. A clean machine setup review and deployment verification are also outstanding. These items are not represented as completed by the passing local checks.
