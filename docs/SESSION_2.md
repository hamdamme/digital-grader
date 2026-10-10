# Session 2 — backend foundation

Date: October 4, 2026 (America/Chicago).

## Scope

Infrastructure, startup configuration, database migrations, health endpoint and backend tests. Frontend design is unchanged. Authentication, application entities/repositories, cohort APIs, storage integration, queues and grading are later sessions.

## Start

Requirements: Docker with Compose v2 and BuildKit, Java 17, Maven 3.9, Python 3 (environment generation only).

From the Digital Grader repository root:

```sh
python3 scripts/init-dev-env.py
docker compose up -d --build --wait --wait-timeout 180
./scripts/backend.sh spring-boot:run
```

The first command creates a mode-0600 `.env` with independent random local secrets. It refuses to overwrite an existing file. Do not source `.env.example` or use its placeholders as credentials. If `.env` already exists, preserve it and ensure its keys match the example.

`backend.sh` loads `.env`, selects Homebrew Java 17 on Apple Silicon when JAVA_HOME is unset, and stores Maven downloads under ignored `.data/maven`. On other systems set JAVA_HOME to JDK 17. The build enforces Java 17 and Maven 3.9.x. The backend runs on the host; Compose runs only infrastructure.

```sh
curl --fail http://127.0.0.1:8080/api/v1/health
./scripts/backend.sh --batch-mode --no-transfer-progress verify
```

Health returns `{"status":"UP"}` only if PostgreSQL responds. Database failure produces HTTP 503 with `{"status":"DOWN"}` and no connection details. The endpoint does not yet test RabbitMQ or MinIO: those are independently checked by Compose. All other API routes return 401 until authentication is implemented.

## Local services

| Service | Local endpoint | Purpose |
|---|---|---|
| Backend | http://127.0.0.1:8080/api/v1/health | Application/database health |
| PostgreSQL | 127.0.0.1:15432 | Dedicated Digital Grader database |
| RabbitMQ AMQP | 127.0.0.1:5673 | Future grading queue |
| RabbitMQ console | http://127.0.0.1:15673 | Queue administration |
| MinIO S3 | http://127.0.0.1:19000 | Future submission storage |
| MinIO console | http://127.0.0.1:19001 | Object administration |

All published ports bind only to loopback. Console credentials come from `.env`: RABBIT_USERNAME/RABBIT_PASSWORD and MINIO_ACCESS_KEY/MINIO_SECRET_KEY. No credentials are printed by setup. Infrastructure data persists in digital-grader-prefixed named Docker volumes. Existing unrelated containers and their data are not modified.

## Configuration

| Variables | Notes |
|---|---|
| DB_NAME, DB_USERNAME, DB_PASSWORD | PostgreSQL initialization settings; changing these does not rotate an existing database automatically |
| DB_PORT, DB_URL | Host port and matching JDBC URL; update both together |
| RABBIT_HOST, RABBIT_PORT, RABBIT_MANAGEMENT_PORT | Local broker addresses; application integration is deferred |
| RABBIT_USERNAME, RABBIT_PASSWORD | Broker initialization credentials |
| MINIO_ENDPOINT, MINIO_PORT, MINIO_CONSOLE_PORT | Storage endpoints; update endpoint if its port changes |
| MINIO_ACCESS_KEY, MINIO_SECRET_KEY, MINIO_BUCKET | Local storage credentials and future bucket name; bucket creation is deferred |
| JWT_SECRET | Required, at least 64 characters; tokens are implemented in Session 3 |
| JWT_ACCESS_TTL_MINUTES, JWT_REFRESH_TTL_DAYS | Reserved for Session 3 |
| SERVER_ADDRESS, SERVER_PORT | Default 127.0.0.1:8080 |
| CORS_ALLOWED_ORIGINS | Reserved until frontend/API integration; not enabled in this checkpoint |

Missing DB_URL, DB_USERNAME, DB_PASSWORD, or JWT_SECRET fails startup before creating a datasource. Secret values are not included in validation errors.

## Database

Flyway V1 preserves the source specification's DDL and adds its separately specified refresh_tokens table. There are 11 application tables plus Flyway's own schema-history table. The four requested indexes are included, along with refresh-token lookup/index constraints. Migrations are validated on startup and Flyway clean is disabled. No production users or default-password accounts are seeded yet.

Tests start a disposable PostgreSQL 16 container and do not touch the Compose database. They cover schema and indexes, migration idempotence, role/email/foreign-key/attempt constraints, JSON rubric round-trip, refresh-token deletion, endpoint access, missing config and health failure behavior. Docker is required; database tests are not silently skipped.

## MinIO source build

The original prebuilt community-image references failed during this session. Upstream now documents source-only distribution, and the repository is archived. `infra/minio/Dockerfile` builds official release `RELEASE.2025-10-15T17-29-55Z`, pinned to commit `9e49d5e7a648f00e26f2246f4dc28e6b07f8c84a` and a checked archive SHA-256. The first build downloads Go dependencies and can take several minutes; subsequent builds reuse Docker cache. The runtime runs as non-root UID 10001.

This is a local compatibility setup, not a production storage recommendation. Select a maintained S3-compatible storage deployment before production. MinIO retains its AGPLv3 license; the image includes its upstream LICENSE and source metadata. Digital Grader's MIT license does not replace third-party licenses.

Sources: https://github.com/minio/minio#source-only-distribution and the source pinned in the Dockerfile.

## Docker credential-helper limitation in agent sessions

If public image pulls report a macOS Keychain error, use your normal authenticated Terminal, or an isolated config for anonymous public pulls. Do not copy credentials into the project or change the global Docker login. During this session a local ignored `.data/docker-public/config.json` was used with an empty Docker Hub auth entry and the installed Docker CLI plugin directory. `DOCKER_CONFIG` and `DOCKER_HOST` were set only for those commands. Standard Terminal use should not need this workaround.

## Stop safely

Stop the backend with Ctrl+C in its terminal. `docker compose stop` stops Digital Grader services while preserving data. `docker compose start --wait` restarts existing containers; use `up -d --build --wait` after infrastructure changes. Do not use `down -v` unless intentionally deleting all local Digital Grader data.

## Next checkpoint

Session 3: entities/repositories, student registration, login/refresh and role/cohort authorization. No grading worker or execution of student code belongs in Session 2.
