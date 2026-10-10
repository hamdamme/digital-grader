# Decisions

## 2026-10-03: prototype before implementation
The owner requested a visual prototype and a multi-day delivery pace. Today's implementation is a dependency-free browser prototype; the target application stack remains Java 17 / Spring Boot and React / TypeScript from the source specification. No production acceptance test is claimed by this milestone.

## Local preview
Keep the prototype in frontend/ with no credentials or services. Use in-memory sample data, clearly marked, and discard changes on reload. A chosen ZIP is validated by filename and size only; bytes are never read or uploaded.

## Repository
Default to a public GitHub repository named gradely. Use the existing Git identity, Hamdam, and its configured GitHub no-reply email. GitHub authentication and Desktop filesystem permission are external setup prerequisites.

## Resolve before worker implementation
- An offline Maven container needs dependencies preloaded or an explicitly controlled provisioning stage.
- Copying output after exit conflicts with --rm and tmpfs; define a tested output collection lifecycle.
- JaCoCo prepare-agent alone does not produce an XML report; invoke report explicitly.
- gVisor requires a compatible Linux execution host; do not silently substitute weaker isolation on macOS.
- Self-registration is STUDENT-only despite the broader API example.
- Security findings cannot satisfy T17 until the later security tasks are implemented; separate those assertions.

## 2026-10-03: Gradely branding and public repository
The owner chose Gradely as the project name and explicitly requested public GitHub visibility. The active checkout is Desktop/gradely. Desktop/grader is retained as the original checkpoint because this environment cannot rename its parent directory. Preserve the original build specification verbatim.

## 2026-10-03: light developer workspace
The owner rejected the green card-based design as too similar to their personal website and selected a light workspace. Replace it with charcoal navigation, blue actions, assignment rows and a desktop review panel. Keep existing prototype behavior and sample-data disclosures.

## 2026-10-03: React foundation checkpoint
The owner requested the next step after the light redesign. Port the approved UI to React/TypeScript now, while keeping backend implementation deferred. Use HashRouter to preserve local link behavior without server rewrite rules, split fixtures from components, and add tests for validation, timer cleanup and keyboard navigation.

The initial install of spec-era Vite 5, Router 6 and Vitest 3 reported six dependency findings. Upgrade to Vite 8.3.2, Router 7.18.4 and Vitest 5.0.3 with plugin-react 6.1.1; TypeScript 5.5 could not parse the plugin declarations, so use the compatible installed TypeScript 7.0.2. Keep React 18.3.1. The resulting npm audit reports zero findings. Exact dependencies and transitive resolutions are committed. Node 24 is used for local verification and CI.

Preview uses port 5176 because the earlier Python service still owns 5174 and could not be terminated by this environment. No production services or student code execution were added.

## 2026-10-03: simplified teacher workspace
The owner requested darker left navigation and a simpler professional appearance. Teacher pages use the earlier charcoal/blue palette, sans-serif headings, white content panels and direct labels. Removed the decorative teaching panel. Teacher cohort overview is /studio; submission review is /review/:id. Student pages remain available. All submissions, code and findings are sample data; review notes are local drafts only.

## 2026-10-04: Session 2 backend foundation
Work continues in Desktop/PROJECTS/gradely after the owner moved the checkout. Preserve the unrelated untracked gradely_mindtek folder; it is excluded from this session's commits. Use session-2/backend-foundation for reviewable changes.

Use Java 17 with Maven 3.9 and Spring Boot 3.5.16, a newer patch line than the spec's unsupported 3.3 series. Spring's dependency management selects Flyway/PostgreSQL/Testcontainers versions rather than duplicating pins. Retain com.gradely as the application package. JDBC suffices for this foundation; JPA entities and application APIs are Session 3+.

Use dedicated loopback ports (PostgreSQL 15432, AMQP 5673, RabbitMQ UI 15673, MinIO 19000/19001), because another project already uses 5432. All credentials come from ignored .env, generated randomly instead of shipping usable defaults. JWT_SECRET validation is reserved for the forthcoming auth layer. No demo users are seeded before authentication exists.

The migration preserves the specified DDL, adds the refresh_tokens table from the auth section, and includes lookup indexes. Only the health endpoint is accessible; other requests are denied until authorization is implemented. Health verifies the database; Compose separately verifies the broker and storage.

Official MinIO community image pulls failed; upstream now publishes source only and has archived its repository. Build the last release from a pinned commit and checked archive, retain AGPL licensing and run non-root. This is local compatibility infrastructure, not a production recommendation. PostgreSQL 16 and RabbitMQ 3.13 follow the requested stack; production dependency/support review remains required before a release. No third-party default credentials were added to tracked files.

## 2026-10-09: Session 3 authentication checkpoint
Retain typed JDBC repositories instead of introducing JPA alongside the existing JDBC/Flyway stack; only users and cohorts need application models at this milestone. Self-registration is student-only, matching the specification's security section. Admins provision instructors, with an opt-in first-admin bootstrap and no default credentials. Passwords use BCrypt cost 10 with a 12-character minimum and explicit 72-byte UTF-8 limit.

Use HS256 access JWTs and opaque hashed refresh tokens. Rotation is a single PostgreSQL transaction with DELETE RETURNING, tested for concurrency and rollback. Access requests recheck the stored user and role. Logout revokes the supplied refresh token, not outstanding access tokens or other devices; this limitation is documented. Exact-origin CORS and stateless bearer authentication are implemented; the frontend remains a prototype. Account recovery, abuse protection, email verification and production hardening remain release work.

## 2026-10-09: teacher workflows and submission intake
Implement the specification's create/enroll/read API contracts with owner/admin enforcement; student assignment reads require membership. Rubrics use typed validated records. Restrict the future build command to mvn -B test. Swagger UI documents the API.

Accept only validated ZIP multipart uploads in Session 5; defer Git ingestion and direct presigned PUT because they require separate fetching/staging validation. MinIO storage uses random keys and five-minute authorized downloads. A transactional outbox prevents database/broker dual-write loss, using confirms and durable persistent messages. Delivery remains at least once; worker claims must be idempotent. PostgreSQL student-row locking serializes attempt limits. Cleanup after database rollback is best effort; crash reconciliation remains release work.

Use MinIO Java SDK 9.0.3 with its explicit OkHttp JVM 5.3.2 artifact for Maven, and Commons Compress 1.28.0 for ZIP entry metadata/validation. The real-service tests use the existing pinned source-built MinIO image; CI builds it before testing. No backend extraction or execution, no simulated grading progress, and no frontend design changes are part of these sessions.

## 2026-10-10: grading worker and runtime acceptance boundary
Use a separate non-HTTP process from the existing Spring Boot artifact, an atomic database claim with token fencing, and a manual-ack RabbitMQ listener. Refuse startup without registered runsc and a locally available sandbox image. Resolve its immutable image ID at startup; never fall back to the host or normal Docker runtime for student execution.

Emit bounded reports as JSON through retained Docker logs before explicit removal, instead of copying container output directories to the host. Keep Maven offline with a prewarmed sample dependency cache. Store reports privately, score with the validated assignment rubric, and mark rerun passes FLAKY without awarding pass credit. Insert a security outbox record alongside the grade; security processing is Session 7.

Mac Docker tests validate services and worker orchestration with a substituted sandbox. They do not establish gVisor containment or real grading acceptance. Keep Session 6 in progress until the two explicit sandbox-profile tests and end-to-end grading are verified on Linux. Student-generated reports are not authoritative assessment evidence; instructor-owned tests remain release work.
