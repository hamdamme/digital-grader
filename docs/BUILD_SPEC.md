# Digital Grader requirements
**Project:** Automated Grading and Security Analysis of Student Code
**Version:** 1.0
**Audience:** Project contributors and reviewers.

---

## 0. SCOPE AND DELIVERY REQUIREMENTS

Digital Grader lets bootcamp students submit Java test automation projects using Selenium, TestNG, Cucumber and Maven. The target system executes submissions in isolated sandboxes, grades results, scans for vulnerabilities and provides AI hints. Instructors manage assignments and review results.

This specification describes the target system, not a claim that every feature is implemented. [Project status](HANDOFF.md) records completed milestones and remaining acceptance work. [Technical decisions](../DECISIONS.md) records implementation choices that supersede the original baseline.

1. Respect task dependencies and record unfinished acceptance requirements.
2. Run relevant acceptance checks for each implemented milestone and record their results.
3. Keep commits focused on coherent changes with concise descriptions.
4. Load secrets and deployment configuration from environment variables.
5. Record significant design choices in `DECISIONS.md`.
6. Keep the implementation buildable and resolve relevant test failures.
7. Maintain setup, architecture and verification documentation alongside the code.

---

## 1. TECH STACK (ORIGINAL PLANNING BASELINE)

| Layer | Technology | Version |
|---|---|---|
| Language (backend) | Java | 17 (LTS) |
| Framework | Spring Boot | 3.3.x |
| Build tool | Maven | 3.9.x |
| Language (frontend) | TypeScript | 5.5.x |
| Framework (frontend) | React | 18.3.x |
| Frontend build | Vite | 5.x |
| UI library | TailwindCSS | 3.4.x + shadcn/ui components |
| Charts | Recharts | 2.x |
| Database | PostgreSQL | 16 |
| Migrations | Flyway | 10.x |
| Queue | RabbitMQ | 3.13 (management plugin enabled) |
| Object storage | MinIO | latest (RELEASE) |
| Sandbox | Docker + gVisor (runsc) | runsc latest |
| Test runner | TestNG | 7.x (inside student projects) |
| Coverage | JaCoCo | 0.8.x (agent attaches to student builds) |
| SAST | Semgrep | latest (OSS) |
| Dependency scanning | OWASP Dependency-Check | 9.x |
| AI | OpenAI-compatible API | GPT-4o-mini or local Ollama (configurable) |
| Auth | Spring Security 6 + JWT (jjwt 0.12.x) | — |
| API docs | springdoc-openapi (Swagger UI) | 2.x |
| Backend tests | JUnit 5 + Mockito + Testcontainers | — |
| E2E tests (optional) | Playwright | — |

---

## 2. REPOSITORY STRUCTURE (MONOREPO)

```
Grader/
├── README.md
├── DECISIONS.md
├── .env.example
├── .gitignore
├── docker-compose.yml
├── docs/                          # the two Word docs live here as PDFs
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│       ├── main/java/com/grader/
│       │   ├── GraderApplication.java
│       │   ├── config/            # SecurityConfig, RabbitConfig, MinioConfig, OpenApiConfig
│       │   ├── auth/              # controller, service, dto, jwt
│       │   ├── user/              # entity, repo, service
│       │   ├── cohort/            # entity, repo, service, controller, dto
│       │   ├── assignment/        # entity, repo, service, controller, dto
│       │   ├── submission/        # entity, repo, service, controller, dto
│       │   ├── grading/           # result entities, grading service, worker listener
│       │   ├── securityscan/      # finding entity, scanner service, semgrep/dependency-check runners
│       │   ├── feedback/          # hint entity, AI service (prompts)
│       │   ├── storage/           # MinIO service
│       │   ├── queue/             # RabbitMQ producer/consumer config
│       │   └── common/            # exceptions, GlobalExceptionHandler, ApiError
│       ├── main/resources/
│       │   ├── application.yml
│       │   ├── application-dev.yml
│       │   └── db/migration/      # Flyway: V1__init.sql, V2__seed.sql
│       └── test/java/com/grader/  # unit + integration tests
├── worker/
│   ├── pom.xml                    # separate Maven module for the grading worker
│   └── src/main/java/com/grader/worker/
│       ├── WorkerApplication.java
│       ├── GradingListener.java   # RabbitMQ consumer
│       ├── SandboxExecutor.java   # docker/gVisor orchestration
│       ├── JunitParser.java       # parse JUnit XML → results
│       ├── JacocoParser.java      # parse jacoco.xml → coverage
│       └── FlakinessChecker.java  # rerun logic
├── sandbox/
│   ├── Dockerfile.student         # image used to run student code
│   ├── run-grading.sh             # entrypoint script inside sandbox
│   └── semgrep-rules/
│       └── grader-rules.yml       # custom Semgrep rules
├── frontend/
│   ├── package.json
│   ├── vite.config.ts
│   ├── tailwind.config.js
│   ├── index.html
│   └── src/
│       ├── main.tsx
│       ├── App.tsx                # router
│       ├── api/                   # axios client + typed API calls
│       ├── auth/                  # AuthContext, login/register pages, route guards
│       ├── pages/
│       │   ├── student/           # AssignmentsPage, SubmitPage, ResultsPage, DashboardPage
│       │   └── instructor/        # CohortsPage, AssignmentBuilderPage, CohortDashboardPage, SubmissionReviewPage
│       ├── components/            # shared UI (shadcn-style)
│   └── Dockerfile
└── e2e/                           # Playwright tests (optional, last task)
```

---

## 3. DEVELOPMENT ENVIRONMENT (docker-compose.yml)

Single root `docker-compose.yml` must bring up everything except the Java services:

```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: grader
      POSTGRES_USER: grader
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    ports: ["5432:5432"]
    volumes: [pgdata:/var/lib/postgresql/data]

  rabbitmq:
    image: rabbitmq:3.13-management
    ports: ["5672:5672", "15672:15672"]   # 15672 = management UI

  minio:
    image: minio/minio
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: ${MINIO_ACCESS_KEY}
      MINIO_ROOT_PASSWORD: ${MINIO_SECRET_KEY}
    ports: ["9000:9000", "9001:9001"]
    volumes: [miniodata:/data]

volumes:
  pgdata:
  miniodata:
```

**Acceptance test (T01):** `docker compose up -d` starts all 3 services healthy; `docker ps` shows them up; RabbitMQ UI reachable at http://localhost:15672; MinIO console at http://localhost:9001.

---

## 4. CONFIGURATION & SECRETS

`.env.example` (repo root) — real values in local `.env` (gitignored):

```bash
# Database
DB_URL=jdbc:postgresql://localhost:5432/grader
DB_USERNAME=grader
DB_PASSWORD=change_me

# RabbitMQ
RABBIT_HOST=localhost
RABBIT_PORT=5672
RABBIT_USERNAME=guest
RABBIT_PASSWORD=guest

# MinIO
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
MINIO_BUCKET=submissions

# JWT
JWT_SECRET=dev_only_change_me_64_chars_min________________________________
JWT_ACCESS_TTL_MINUTES=60
JWT_REFRESH_TTL_DAYS=7

# Sandbox
SANDBOX_CPU_LIMIT=2
SANDBOX_MEMORY_MB=4096
SANDBOX_TIMEOUT_SECONDS=600
SANDBOX_NETWORK_DISABLED=true

# AI
AI_PROVIDER=openai            # or "ollama"
AI_API_KEY=                   # empty for ollama
AI_BASE_URL=http://localhost:11434/v1
AI_MODEL=gpt-4o-mini

# App
CORS_ALLOWED_ORIGINS=http://localhost:5173
```

Spring reads these via `${ENV_NAME}` placeholders in `application.yml`. **Acceptance test (T02):** `application.yml` contains zero hardcoded credentials; app fails fast with clear message if required env var is missing.

---

## 5. DATABASE SCHEMA (Flyway V1__init.sql — EXACT DDL)

```sql
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(100) NOT NULL,
    full_name       VARCHAR(255) NOT NULL,
    role            VARCHAR(20)  NOT NULL CHECK (role IN ('STUDENT','INSTRUCTOR','ADMIN')),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE cohorts (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    instructor_id   BIGINT NOT NULL REFERENCES users(id),
    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE cohort_members (
    cohort_id   BIGINT NOT NULL REFERENCES cohorts(id) ON DELETE CASCADE,
    student_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (cohort_id, student_id)
);

CREATE TABLE assignments (
    id              BIGSERIAL PRIMARY KEY,
    cohort_id       BIGINT NOT NULL REFERENCES cohorts(id) ON DELETE CASCADE,
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    rubric_json     JSONB NOT NULL,          -- see §5.1
    due_date        TIMESTAMPTZ,
    max_attempts    INT NOT NULL DEFAULT 3,
    build_command   VARCHAR(255) NOT NULL DEFAULT 'mvn -B test',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE submissions (
    id              BIGSERIAL PRIMARY KEY,
    assignment_id   BIGINT NOT NULL REFERENCES assignments(id),
    student_id      BIGINT NOT NULL REFERENCES users(id),
    attempt_number  INT NOT NULL,
    submitted_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    status          VARCHAR(20) NOT NULL DEFAULT 'QUEUED'
                    CHECK (status IN ('QUEUED','RUNNING','GRADED','FAILED','TIMEOUT')),
    storage_path    VARCHAR(512) NOT NULL,   -- MinIO object key of zip
    commit_hash     VARCHAR(64),
    UNIQUE (assignment_id, student_id, attempt_number)
);

CREATE TABLE grading_results (
    id                  BIGSERIAL PRIMARY KEY,
    submission_id       BIGINT NOT NULL UNIQUE REFERENCES submissions(id),
    build_success       BOOLEAN NOT NULL,
    build_log_path      VARCHAR(512),
    tests_total         INT NOT NULL DEFAULT 0,
    tests_passed        INT NOT NULL DEFAULT 0,
    tests_failed        INT NOT NULL DEFAULT 0,
    tests_flaky         INT NOT NULL DEFAULT 0,
    coverage_line_pct   NUMERIC(5,2),
    coverage_branch_pct NUMERIC(5,2),
    score               NUMERIC(5,2),
    rubric_breakdown    JSONB,               -- per-criterion scores
    graded_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE test_case_results (
    id              BIGSERIAL PRIMARY KEY,
    grading_result_id BIGINT NOT NULL REFERENCES grading_results(id) ON DELETE CASCADE,
    test_name       VARCHAR(512) NOT NULL,
    status          VARCHAR(10) NOT NULL CHECK (status IN ('PASSED','FAILED','FLAKY','SKIPPED')),
    duration_ms     BIGINT,
    error_message   TEXT,
    stack_trace     TEXT
);

CREATE TABLE security_findings (
    id              BIGSERIAL PRIMARY KEY,
    submission_id   BIGINT NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
    source          VARCHAR(20) NOT NULL CHECK (source IN ('SEMGREP','DEPENDENCY_CHECK')),
    rule_id         VARCHAR(255),
    cwe_id          VARCHAR(20),
    severity        VARCHAR(10) NOT NULL CHECK (severity IN ('CRITICAL','HIGH','MEDIUM','LOW')),
    file_path       VARCHAR(512),
    line_number     INT,
    message         TEXT,
    fix_guidance    TEXT,
    dependency_name VARCHAR(255),            -- only for DEPENDENCY_CHECK
    current_version VARCHAR(50),
    fixed_version   VARCHAR(50),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE hints (
    id              BIGSERIAL PRIMARY KEY,
    submission_id   BIGINT NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
    content         TEXT NOT NULL,
    model           VARCHAR(100),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE instructor_overrides (
    id              BIGSERIAL PRIMARY KEY,
    grading_result_id BIGINT NOT NULL REFERENCES grading_results(id),
    original_score  NUMERIC(5,2) NOT NULL,
    new_score       NUMERIC(5,2) NOT NULL,
    reason          TEXT NOT NULL,
    instructor_id   BIGINT NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

**Indexes (same migration):** `submissions(assignment_id, student_id)`, `security_findings(submission_id)`, `test_case_results(grading_result_id)`, `cohort_members(student_id)`.

### 5.1 rubric_json format (store in assignments.rubric_json)
```json
{
  "weights": { "tests": 60, "coverage": 20, "codeQuality": 20 },
  "coverageThreshold": 70,
  "qualityChecks": ["checkstyle", "spotbugs"]
}
```
Score computation (Phase 2): `score = testsPct*0.60 + min(coveragePct,100)*0.20 + qualityPct*0.20`, quality = 100 − (5 × number of checkstyle/spotbugs violations), floor 0.

---

## 6. API CONTRACTS (all endpoints, JSON in/out)

Base URL: `/api/v1`. Auth header: `Authorization: Bearer <jwt>`.

**Auth**
| Method | Path | Body | Response |
|---|---|---|---|
| POST | /auth/register | `{email, password, fullName, role: "STUDENT"\|"INSTRUCTOR"}` | 201 `{id, email, fullName, role}` |
| POST | /auth/login | `{email, password}` | 200 `{accessToken, refreshToken, role}` |
| POST | /auth/refresh | `{refreshToken}` | 200 `{accessToken, refreshToken}` |

**Cohorts** (INSTRUCTOR/ADMIN)
| Method | Path | Body | Response |
|---|---|---|---|
| POST | /cohorts | `{name, startDate, endDate}` | 201 cohort |
| POST | /cohorts/{id}/members | `{studentIds: [1,2]}` | 204 |
| GET | /cohorts/mine | — | `[{id,name,startDate,endDate, studentCount}]` |
| GET | /cohorts/{id} | — | cohort + members |

**Assignments** (INSTRUCTOR)
| Method | Path | Body | Response |
|---|---|---|---|
| POST | /assignments | `{cohortId, title, description, rubricJson, dueDate, maxAttempts, buildCommand}` | 201 assignment |
| GET | /assignments?cohortId= | — | `[assignment]` |
| GET | /assignments/{id} | — | assignment |

**Submissions** (STUDENT)
| Method | Path | Body | Response |
|---|---|---|---|
| POST | /assignments/{id}/submissions | multipart: `file` (zip ≤ 50 MB) OR `{gitUrl, commitHash}` | 202 `{submissionId, status:"QUEUED"}` |
| GET | /submissions/{id} | — | submission + status |
| GET | /submissions/{id}/result | — | grading_result + testCaseResults + securityFindings + hints |
| GET | /assignments/{id}/mysubmissions | — | `[{id, attemptNumber, status, score, submittedAt}]` |

**Security & hints**
| Method | Path | Body | Response |
|---|---|---|---|
| GET | /submissions/{id}/security-report | — | `{findings: [...], summary: {CRITICAL: n, HIGH: n...}}` |
| POST | /submissions/{id}/hint | — | 201 `{content}` (AI-generated, cached per submission) |

**Instructor dashboard**
| Method | Path | Response |
|---|---|---|
| GET | /cohorts/{id}/dashboard | `[{studentId, fullName, avgScore, avgCoverage, totalFindings, fixedRate, lastSubmissionAt}]` |
| GET | /cohorts/{id}/vulnerability-heatmap | `[{cweId, count}]` aggregated across cohort |
| POST | /grading-results/{id}/override | `{newScore, reason}` → 201 override record |

**Errors:** all errors → `{status, error, message, path, timestamp}` via GlobalExceptionHandler. Validation errors → 400 with field list.

**Acceptance test (T09):** Swagger UI at `/swagger-ui.html` shows all endpoints; each returns documented shape (verify with curl on seeded data).

---

## 7. UI SPECIFICATION (page by page)

Global: React Router v6, route guards by role (401 → /login, wrong role → home). Tailwind styling, shadcn-style components. All data via axios from `/api/v1` (Vite proxy to :8080).

| # | Route | Role | Content |
|---|---|---|---|
| 1 | /login | all | email + password → stores JWT in localStorage, redirects by role |
| 2 | /register | all | same fields as API |
| 3 | /student/assignments | STUDENT | cards: title, due date, attempts used, best score, status; "Submit" button |
| 4 | /student/submit/:assignmentId | STUDENT | file dropzone (zip) OR git URL input; submit → poll status every 5 s until GRADED/FAILED |
| 5 | /student/results/:submissionId | STUDENT | score card; tabs: Tests (table: name, status, duration, expandable stack trace) / Security (severity-filtered findings table, CWE links) / Hints ("Get hint" button, markdown-rendered) |
| 6 | /student/dashboard | STUDENT | Recharts line charts: score over time, coverage over time, vulnerability count over time |
| 7 | /instructor/cohorts | INSTRUCTOR | cohort list, create-cohort form, add-members (email multi-select) |
| 8 | /instructor/assignment-builder/:cohortId | INSTRUCTOR | form: title, description, rubric weights (numeric inputs summing to 100), due date, max attempts, build command |
| 9 | /instructor/dashboard/:cohortId | INSTRUCTOR | cohort table (per §6 dashboard): avgScore, avgCoverage, totalFindings, fixedRate; vulnerability heatmap (bar chart by CWE) |
| 10 | /instructor/review/:submissionId | INSTRUCTOR | same result view as student + score override form with reason |

**Acceptance test (per UI task):** page renders with seeded data; every button/API call works without console errors.

---

## 8. GRADING WORKER & SANDBOX (the critical path)

### 8.1 sandbox/Dockerfile.student
```dockerfile
FROM maven:3.9-eclipse-temurin-17
RUN apt-get update && apt-get install -y --no-install-recommends \
    chromium chromium-driver && rm -rf /var/lib/apt/lists/*
WORKDIR /code
COPY run-grading.sh /run-grading.sh
RUN chmod +x /run-grading.sh
ENTRYPOINT ["/run-grading.sh"]
```

### 8.2 sandbox/run-grading.sh (exact behavior)
```
1. unzip /input/submission.zip into /code
2. mvn -B org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent test > /output/build.log 2>&1
   (timeout enforced externally)
3. copy target/surefire-reports/*.xml → /output/junit/
4. copy target/site/jacoco/jacoco.xml → /output/ (if exists)
5. exit 0 if build ok, 1 if tests fail, 2 if build error
```

### 8.3 SandboxExecutor.java requirements
- `docker run` with: `--runtime=runsc` (gVisor), `--network=none`, `--memory=${SANDBOX_MEMORY_MB}m`, `--cpus=${SANDBOX_CPU_LIMIT}`, `--read-only` root FS except /code, /output (tmpfs), `--pids-limit=256`, `--rm`
- Mount: submission zip read-only at /input, tmpfs /output
- Enforce `SANDBOX_TIMEOUT_SECONDS` via `docker wait` + kill on timeout → status TIMEOUT
- On success: read /output from container (`docker cp`) → upload build.log + junit XMLs to MinIO under `results/{submissionId}/`
- Never run student code on the host. Never enable network.

### 8.4 Worker pipeline (GradingListener)
```
consume GRADING_QUEUE message {submissionId}
→ update status RUNNING
→ download zip from MinIO
→ SandboxExecutor.run()
→ parse JUnit XMLs (JunitParser) → test results
→ parse jacoco.xml (JacocoParser) → line/branch coverage
→ FlakinessChecker: if tests failed, re-run once; tests that pass on rerun = FLAKY
→ compute score from assignment rubric
→ save grading_result + test_case_results, status GRADED
→ publish SECURITY_QUEUE {submissionId}
```

### 8.5 Security scan consumer
```
consume SECURITY_QUEUE {submissionId}
→ download zip
→ semgrep scan: `semgrep --config /rules/grader-rules.yml --json` → map to findings
→ dependency-check: `dependency-check.sh --project x --scan . --format JSON` → map CVE findings (severity from CVSS: >=9 CRITICAL, 7-8.9 HIGH, 4-6.9 MEDIUM, else LOW)
→ save security_findings
```

**Acceptance test (T17):** place `sample-submission.zip` (seed data: a Maven project with 1 passing + 1 failing TestNG test and one hardcoded password) into MinIO manually, publish message → within 3 min: status GRADED, score present, 2 test rows, coverage numbers, ≥1 CRITICAL security finding (CWE-798). Repeat with infinite-loop project → status TIMEOUT, container killed.

---

## 9. SEMGREP RULES (sandbox/semgrep-rules/grader-rules.yml — exact file)

```yaml
rules:
  - id: grader-sql-concat
    severity: ERROR
    languages: [java]
    message: "Possible SQL injection: string concatenation in SQL/JDBC call"
    cwe: "CWE-89"
    metadata: {grader_severity: CRITICAL}
    patterns:
      - pattern-either:
          - pattern: "Statement.execute($X + ...)"
          - pattern: "PreparedStatement ... $CONN.prepareStatement($X + ...)"

  - id: grader-hardcoded-secret
    severity: ERROR
    languages: [java]
    message: "Hardcoded credential or API key"
    cwe: "CWE-798"
    metadata: {grader_severity: CRITICAL}
    patterns:
      - pattern-regex: "(?i)(password|passwd|secret|api[_-]?key|token)\\s*=\\s*\"[^\"]+\""

  - id: grader-xss
    severity: WARNING
    languages: [java]
    message: "Possible XSS: unescaped output"
    cwe: "CWE-79"
    metadata: {grader_severity: HIGH}
    pattern: "out.println($X)"

  - id: grader-cmd-injection
    severity: ERROR
    languages: [java]
    message: "Command injection: Runtime.exec with concatenated input"
    cwe: "CWE-78"
    metadata: {grader_severity: HIGH}
    pattern: "Runtime.getRuntime().exec($X + ...)"

  - id: grader-path-traversal
    severity: WARNING
    languages: [java]
    message: "Path traversal: file path built from user input"
    cwe: "CWE-22"
    metadata: {grader_severity: HIGH}
    pattern: "new File($X + ...)"

  - id: grader-weak-crypto
    severity: WARNING
    languages: [java]
    message: "Weak cryptographic algorithm"
    cwe: "CWE-327"
    metadata: {grader_severity: MEDIUM}
    pattern-either:
      - pattern: "MessageDigest.getInstance(\"MD5\")"
      - pattern: "MessageDigest.getInstance(\"SHA-1\")"
      - pattern: "Cipher.getInstance(\"DES\")"

  - id: grader-insecure-deser
    severity: WARNING
    languages: [java]
    message: "Insecure deserialization of untrusted data"
    cwe: "CWE-502"
    metadata: {grader_severity: HIGH}
    pattern: "new ObjectInputStream(...).readObject()"

  - id: grader-missing-tls
    severity: INFO
    languages: [java]
    message: "Plain HTTP connection used"
    cwe: "CWE-319"
    metadata: {grader_severity: MEDIUM}
    pattern: "new URL(\"http://...\").openConnection()"
```
Map semgrep `metadata.grader_severity` → DB severity. Add dependency-check CVE mapping as in §8.5.

---

## 10. AI HINTS (feedback service)

Prompt template (system message):
```
You are a tutor for a Java test-automation bootcamp. A student submitted code.
Given: (1) failing test names + error excerpts, (2) security findings (CWE + file:line), (3) a code excerpt.
Produce: (a) one-sentence diagnosis, (b) up to 3 concrete next steps phrased as guidance, NOT full solutions.
Never reveal complete fixed code. Keep under 200 words.
```
User message: JSON `{failingTests:[...], findings:[...], codeExcerpt: <first 150 lines>}`.
Cache: one hint per submission in `hints` table; repeated POSTs return the cached hint.
**Acceptance test (T22):** with seed failing submission, POST /hint returns ≤200-word guidance, no full corrected code block (assert response contains no complete method body copied from a reference solution).

---

## 11. AUTH DETAILS

- Register: BCrypt (strength 10). Role from request but only STUDENT self-registerable; INSTRUCTOR via ADMIN or first-run seed.
- Login: verify → issue access JWT (HS256, claims: sub=userId, role, exp=60 min) + refresh token (opaque, 7 d, stored hashed in DB — add table `refresh_tokens(id, user_id, token_hash, expires_at)` via V1 migration).
- Spring Security: stateless filter validating JWT; role-based method security (`@PreAuthorize`).
- **Acceptance test (T06):** unauthenticated → 401; STUDENT calling POST /cohorts → 403; valid login → access token works on GET /cohorts/mine.

---

## 12. SEED DATA (Flyway V2__seed.sql)

- 1 admin: `admin@grader.local / Admin123!`
- 1 instructor: `instructor@mindtek.local / Teach123!`
- 2 students: `student1@mindtek.local / Learn123!`, `student2@mindtek.local / Learn123!`
- 1 cohort "SDET-Batch16" (instructor above, dates 2026-10-01 → 2027-03-31), both students members
- 1 assignment "Selenium Login Test" with rubric_json from §5.1, max_attempts 3
- Seed MinIO with `sample-submission.zip` (in repo under `sandbox/sample-data/` — a tiny Maven+TestNG project: one passing test, one failing test, one hardcoded password) via `docker compose` init script or documented manual step in README.

---

## 13. TASK LIST (with acceptance tests)

> Execute strictly in order. Commit after each.

- **T01** Root repo: README skeleton, .gitignore (Java/Node/.env), LICENSE (MIT), docker-compose.yml per §3. *Test: compose up healthy.*
- **T02** Env config: .env.example per §4, Spring profile setup, fail-fast config validation. *Test: app boot fails clearly with missing JWT_SECRET; boots with full .env.*
- **T03** Backend skeleton: Spring Boot app per §2 structure, pom.xml with all deps, health endpoint GET /api/v1/health → 200 `{status:"UP"}`. *Test: `mvn spring-boot:run` + curl health.*
- **T04** Flyway V1 migration exactly as §5. *Test: app boot creates all 11 tables; `\dt` lists them.*
- **T05** Entities + repositories for all tables (JPA, Lombok). *Test: context loads; save/find round-trip test passes.*
- **T06** Auth: register/login/refresh per §11, JWT filter, role guards. *Test: §11 acceptance test.*
- **T07** Cohort + member APIs. *Test: instructor creates cohort, adds student, GET /cohorts/mine returns it.*
- **T08** Assignment APIs + rubric validation (weights must sum to 100 → else 400). *Test: POST invalid rubric → 400; valid → 201.*
- **T09** springdoc-openapi; verify all endpoints so far appear with schemas. *Test: swagger-ui renders; register/login/cohorts/assignments callable from UI.*
- **T10** Storage service: MinIO bucket init, presigned PUT/GET, upload zip endpoint wired to submissions table. *Test: POST submission returns 202; file retrievable from MinIO console.*
- **T11** Queue producer: on submission → RabbitMQ message {submissionId}. *Test: submit → message visible in RabbitMQ management UI.*
- **T12** Worker module: standalone Spring Boot app consuming GRADING_QUEUE, updating status RUNNING. *Test: start worker + backend; submit → status transitions QUEUED→RUNNING in DB.*
- **T13** sandbox/Dockerfile.student + run-grading.sh per §8.1–8.2; `docker build -t grader-student .` succeeds. *Test: run container manually on sample zip → /output contains build.log + junit XMLs.*
- **T14** SandboxExecutor with full isolation flags (gVisor, no network, limits, timeout). *Test: inside sandbox, `curl` and `ping` unavailable; `docker stats` shows memory cap; 10-min kill works (use 30 s override for test).*
- **T15** JunitParser + JacocoParser + persistence of grading_result + test_case_results. *Test: after sample run, DB rows match sample project (2 tests: 1 pass, 1 fail; coverage > 0).*
- **T16** FlakinessChecker (second run on failure; FLAKY status) + score computation per §5.1 + status GRADED. *Test: seeded flaky-test project yields tests_flaky=1.*
- **T17** End-to-end grading acceptance test (§8 acceptance test) as an integration test with Testcontainers. *Test: full pipeline green.*
- **T18** Semgrep integration: run rules file §9 inside worker on submission zip; persist security_findings (SEMGREP). *Test: seed zip produces ≥1 CRITICAL CWE-798 finding.*
- **T19** Dependency-check integration + CVE→severity mapping; persist DEPENDENCY_CHECK findings. *Test: pom.xml with old log4j 2.7 → CRITICAL finding with fixed_version.*
- **T20** SECURITY_QUEUE consumer wired; status stays GRADED; findings queryable via API. *Test: GET security-report returns summary counts.*
- **T21** AI service + POST /hint per §10 (works with both providers via config). *Test: with AI_PROVIDER=ollama and no server running → graceful 503; with mock → returns hint.*
- **T22** Hint caching + guardrail test (§10 acceptance).
- **T23** Frontend skeleton: Vite+React+TS+Tailwind, router, axios client with JWT interceptor, login page working against backend. *Test: login via UI stores token; refresh on 401 works.*
- **T24** Student pages 3–5 (§7) with status polling. *Test: full student flow with seeded data.*
- **T25** Student dashboard page 6 (Recharts, 3 trend charts). *Test: charts render with seeded submissions.*
- **T26** Instructor pages 7–8. *Test: create cohort + assignment through UI.*
- **T27** Instructor dashboard page 9 (cohort table + CWE bar chart). *Test: numbers match DB aggregation query.*
- **T28** Instructor review page 10 + override API persistence. *Test: override creates row; dashboard shows adjusted score.*
- **T29** Dockerfiles (backend, worker, frontend) + root compose extension to run all 6 services with one `docker compose up`. *Test: clean machine (clone → cp .env.example .env → compose up) works end-to-end.*
- **T30** Unit test coverage ≥ 70% on backend services; CI workflow (.github/workflows/ci.yml): build, test, docker build on push. *Test: CI green on main.*
- **T31** Playwright E2E: login → submit → result visible. *Test: headed run passes on staging compose.*
- **T32** Final README: architecture ASCII diagram, setup, env table, API link to Swagger, screenshots placeholder, DECISIONS.md review. *Test: a new contributor can complete setup using the README.*

---

## 14. TESTING REQUIREMENTS

- Backend: JUnit 5 + Mockito (units), Testcontainers (integration: PostgreSQL, RabbitMQ, MinIO in Docker for tests).
- Worker: integration test with real Docker sandbox (marked `@Tag("sandbox")`, skippable in CI without Docker).
- Frontend: Vitest for components (key pages only), no snapshot spam.
- Security: add `dependency-check` to backend CI (fail on CVSS ≥ 7 until baselined); OWASP ZAP baseline scan of staging (optional, T31).

## 15. DEFINITION OF DONE (whole project)

1. `docker compose up` from clean clone runs the full system.
2. §8.5 acceptance test passes on a fresh environment.
3. All T01–T32 acceptance tests pass; CI green.
4. README complete; Swagger documents 100% of endpoints.
5. Sample student project + docs folder included; seeded demo works with zero manual steps beyond `cp .env.example .env`.
