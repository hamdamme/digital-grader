# Gradely

Automated grading and security feedback for Java test-automation students.

## Current milestone

Sessions 1–5 are complete and published; authentication, teacher workflows and submission intake were merged through PR #3. Session 6 adds a standalone grading worker, isolated sandbox configuration, report parsing, scoring and result APIs. The implementation is under local verification; actual gVisor execution on Linux remains pending.

The approved React prototype remains unchanged and uses sample data. UI/API integration and AI guidance remain future work. On this Mac, real submissions stay QUEUED because the required runsc runtime is unavailable. See [Session 6](docs/SESSION_6.md) for worker setup and limitations, [Sessions 4–5](docs/SESSION_4_5.md) for upload APIs, and [Session 3](docs/SESSION_3.md) for admin setup.

## Backend and infrastructure

Requires Docker with Compose v2, Java 17, Maven 3.9 and Python 3. From the repository root:

```sh
python3 scripts/init-dev-env.py   # once; preserves any existing .env
docker compose up -d --build --wait --wait-timeout 180
./scripts/backend.sh spring-boot:run
```

Then open http://127.0.0.1:8080/api/v1/health. Run `./scripts/backend.sh -B -ntp verify` for backend tests. See [Session 2 setup](docs/SESSION_2.md) for service ports, configuration, source-built MinIO, troubleshooting and stop/restart instructions. The first MinIO build can take several minutes. MinIO is a local compatibility dependency; upstream is archived, so production storage selection remains open.

## Run the frontend

Use Node.js 24 LTS (minimum 22.12) and npm.

```sh
cd frontend
npm ci
npm run dev
```

Open http://127.0.0.1:5176. The server binds to loopback and fails clearly if its port is occupied. The earlier Python preview is obsolete; it cannot serve React source files.

```sh
npm test          # component and submission lifecycle checks
npm run build    # strict TypeScript check plus production build
npm run preview  # preview the build at http://127.0.0.1:5175
```

All file selection is local. No ZIP bytes are uploaded or executed. Simulated submissions show the same labeled Java Fundamentals sample report, not an analysis of the selected project. Reloading discards local interaction state.

## Structure

```text
backend/             Spring Boot application, Flyway migrations and tests
scripts/             Environment generation and Java 17 Maven launcher
infra/minio/         Pinned official MinIO source build
sandbox/             Grading image, bounded runner and sample project
docker-compose.yml   Local PostgreSQL, RabbitMQ and MinIO
frontend/
  src/
    components/   Shared shell and visual components
    data/         Typed demo fixtures
    lib/          File-selection validation
    pages/        Assignments, submission, results, progress
    App.tsx       Routes, including not-found handling
    App.test.tsx  Submission lifecycle and interaction checks
  public/         Static assets
  package-lock.json
.github/workflows/frontend.yml
  npm ci -> tests -> typecheck + build -> dependency audit

docs/BUILD_SPEC.md  Original requirements, preserved verbatim
DECISIONS.md       Scope and technology decisions
docs/PLAN.md       Working schedule
docs/HANDOFF.md    Verified status and next session
```

## Development workflow

Use short-lived feature branches and commits that represent coherent changes. Record meaningful decisions, run relevant checks, and keep the lockfile committed. Do not commit credentials, dependency folders, build output, or student submissions. CI is configured for frontend changes; a successful local run does not imply a GitHub Actions run has occurred.

The prototype keeps React 18. Patched Vite, Router, Vitest, and compatible TypeScript versions supersede the older build-spec versions; exact installed versions are pinned in package.json and package-lock.json. The visual design and Java/Spring target remain unchanged. Tailwind, charts and frontend API/authentication integration are deferred until needed for the next implementation slices.

## GitHub

Public repository: https://github.com/hamdamme/gradely. Session 1 is published on main and its frontend workflow passed. Session 2 was merged through PR #1 and its backend CI passed. Sessions 3–5 were merged through [PR #3](https://github.com/hamdamme/gradely/pull/3), with successful backend CI. Session 6 is local work on `session-6/grading-worker`; its remote CI remains pending.

[Frontend Actions run verified at session start](https://github.com/hamdamme/gradely/actions/runs/37161397951).

## License

MIT — see LICENSE.
