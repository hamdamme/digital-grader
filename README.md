# Digital Grader

Automated grading and security feedback for students learning Java test automation.

## Status

Implemented: account registration and authentication, cohort and assignment management, validated ZIP submission intake, private object storage with queue dispatch, a sandboxed grading worker with report parsing and scoring, security scanning with optional AI hints, and a React workspace connected to the API. A separate demo area with labeled sample data is included for design review.

Pending acceptance: a live vulnerability snapshot refresh and an evaluated hint provider. Real sandbox execution under gVisor on Linux is verified (see [ROADMAP](ROADMAP.md) acceptance notes). See [ROADMAP](ROADMAP.md) for phase status and [DECISIONS](DECISIONS.md) for the reasoning behind the main design choices.

## Requirements

- Docker with Compose v2
- Java 17 and Maven 3.9
- Python 3 (environment generation and sandbox helpers only)
- Node.js 24 LTS (minimum 22.12) for the frontend

## Backend and local services

From the repository root:

```sh
python3 scripts/init-dev-env.py   # first setup only; preserves an existing .env
docker compose up -d --build --wait --wait-timeout 180
./scripts/backend.sh spring-boot:run
```

`init-dev-env.py` creates a mode-0600 `.env` with random local secrets and refuses to overwrite an existing file. Do not use `.env.example` placeholders as credentials. `backend.sh` loads `.env` and stores Maven downloads under the ignored `.data/` directory; set `JAVA_HOME` to a JDK 17 if it is not detected.

| Service | Local endpoint | Purpose |
|---|---|---|
| Backend | http://127.0.0.1:8080/api/v1/health | Application and database health |
| PostgreSQL | 127.0.0.1:15432 | Application database |
| RabbitMQ AMQP | 127.0.0.1:5673 | Submission and scan queues |
| RabbitMQ console | http://127.0.0.1:15673 | Queue administration |
| MinIO S3 | http://127.0.0.1:19000 | Private submission and report storage |
| MinIO console | http://127.0.0.1:19001 | Object administration |

All published ports bind to loopback only. Console credentials come from `.env`. The first MinIO build can take several minutes; the local image is built from pinned official source (see `infra/minio/Dockerfile`) and is a development convenience, not a production storage choice.

The health endpoint returns `{"status":"UP"}` only when PostgreSQL responds. API documentation is at http://127.0.0.1:8080/swagger-ui/index.html.

### First administrator

Public registration creates student accounts only. To provision the first administrator, set `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_NAME` and `BOOTSTRAP_ADMIN_PASSWORD` in `.env` and start the backend once; remove those values after the first successful startup. Administrators then create instructor accounts through the API.

### Configuration

`.env.example` lists every setting with notes. Notable groups: database (`DB_*`), broker (`RABBIT_*`), storage (`MINIO_*`), token signing and lifetimes (`JWT_*`), sandbox limits (`SANDBOX_*`) and the optional hint provider (`AI_*`, disabled by default). Missing database settings or a short `JWT_SECRET` fail startup before a connection pool is created.

## Grading worker

The worker is a separate process of the same application that consumes queued submissions and executes them in a gVisor sandbox container. It requires a Linux Docker host with `runsc` installed; the worker and the Docker daemon must share a host filesystem for the input mount.

```sh
docker build -f sandbox/Dockerfile.student -t digital-grader-student:local sandbox
./scripts/check-sandbox.sh          # verifies runsc and resolves the image ID
./scripts/backend.sh spring-boot:run   # API, terminal one
./scripts/worker.sh                    # worker, terminal two
```

Sandbox behavior: no network, read-only root filesystem, bounded writable tmpfs, dropped capabilities, no-new-privileges, and PID, CPU, memory and runtime limits. Only the submitted ZIP is mounted, read-only. Maven runs offline against dependencies cached in the image. The API never executes student code.

Optional environment settings: `SANDBOX_IMAGE` (default `digital-grader-student:local`), `SANDBOX_TIMEOUT_SECONDS` (default 600), `SANDBOX_MEMORY_MB` (default 1536), `SANDBOX_CPU_LIMIT` (default 1).

gVisor setup: https://gvisor.dev/docs/user_guide/quick_start/docker/

## Security worker and hints

A second worker consumes completed grades and runs offline security scans against a pinned vulnerability snapshot:

```sh
docker build -f sandbox/Dockerfile.security -t digital-grader-security:local sandbox
scripts/update-security-db.sh /path/to/vulnerability-snapshot   # refresh pinned data
./scripts/security-worker.sh
```

AI hints are disabled by default. When enabled (`AI_ENABLED=true`), hint requests send bounded test evidence and finding locations to the configured Chat Completions compatible provider; source excerpts are not transmitted. See `.env.example` for provider settings.

## Frontend

```sh
cd frontend
npm ci
npm run dev        # http://127.0.0.1:5176
npm test           # component and lifecycle checks
npm run build      # strict type check and production build
npm run preview    # preview the build at http://127.0.0.1:5175
```

Sign in to use the connected workspace, or open `/#/demo/studio` for the sample design. Browser tokens are held in memory, so reloading requires sign-in. The demo area is labeled sample data and performs no real uploads or grading.

## Verification

```sh
./scripts/backend.sh -B -ntp verify   # unit and integration tests, needs Docker
python3 sandbox/test_extraction.py
python3 sandbox/test_security_rules.py
cd frontend && npm test && npm run build
```

Backend integration tests run against disposable PostgreSQL, MinIO and RabbitMQ containers and substitute the sandbox execution boundary; they validate orchestration, not gVisor containment. Real sandbox acceptance uses the `sandbox` Maven profile on a Linux host with `runsc`:

```sh
./scripts/backend.sh -B -ntp -Psandbox test
```

GitHub Actions check backend, frontend and browser workflows on changes. A successful local run does not establish remote CI success.

## Project structure

| Path | Contents |
|---|---|
| `backend/` | Spring Boot API, worker entry points, migrations and tests |
| `frontend/` | React pages, shared components and demonstration fixtures |
| `sandbox/` | Grading and security images, bounded runners, sample project |
| `infra/minio/` | Pinned MinIO source build |
| `scripts/` | Environment setup, launch and verification scripts |
| `.github/workflows/` | Backend, frontend and browser checks |

## Documentation

- [Roadmap and phase status](ROADMAP.md)
- [Technical decisions](DECISIONS.md)
- [Third-party licenses](THIRD_PARTY.md)

## Development

Use focused branches and commits that describe concrete changes. Run checks relevant to each change and record unresolved acceptance work. Keep credentials, dependencies, generated output and student submissions out of Git.

Public repository: [hamdamme/digital-grader](https://github.com/hamdamme/digital-grader).

## License

MIT — see [LICENSE](LICENSE). Dependencies retain their own licenses; see [third party notices](THIRD_PARTY.md).
