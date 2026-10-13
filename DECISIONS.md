# Technical decisions

This document records the current implementation choices. Phase status is in [the roadmap](ROADMAP.md); setup and verification live in [the README](README.md).

## Interface

Use React and TypeScript with HashRouter, shared layout components and separate demonstration fixtures. The workspace has charcoal navigation, blue actions and light content panels. Teacher review and student pages currently use sample data; local interactions reset on reload. This prototype does not represent real grading or stored instructor feedback.

Use the dependency versions committed in the package manifests and lockfile. The original specification is a planning baseline; its older library versions and deferred UI libraries do not override the implemented dependency set.

## Backend and persistence

Use Java 17, Maven 3.9 and Spring Boot 3.5.16. Typed JDBC repositories and Flyway provide persistence without introducing JPA alongside JDBC. The application package is `com.digitalgrader`.

PostgreSQL stores users, cohorts, assignments, submission state and grading results. Loopback service ports keep local infrastructure separate from other projects. Generate development credentials into an ignored `.env`; do not ship usable default credentials.

The local MinIO image is built from pinned official source with a verified archive checksum. It retains its upstream license. Production storage selection and dependency support review remain release work.

## Authentication and permissions

Public registration creates students only. Administrators provision instructors, and initial administrator creation requires explicit configuration. Passwords use BCrypt cost 10 with at least 12 characters and a maximum of 72 UTF-8 bytes.

Access tokens use HS256 and authenticated requests recheck the stored user and role. Refresh tokens are opaque random values stored as SHA-256 hashes; rotation consumes and replaces a token in one transaction. Logout revokes the supplied refresh token, not existing access tokens or other devices. CORS accepts explicit origins. Account recovery, abuse protection and email verification remain release work.

## Submission intake

Accept validated ZIP uploads; Git ingestion and direct storage uploads are deferred. Validate paths, sizes, expansion ratios and entry types before storage. MinIO uses random object keys and authorized download URLs that expire after five minutes.

A transactional outbox coordinates persistence and RabbitMQ delivery. Publisher confirms and persistent messages support retry, but delivery may repeat. Worker claims must therefore be idempotent. Database locking serializes student attempt limits. Storage cleanup after rollback is best effort; crash reconciliation remains release work.

## Grading isolation

Run the worker as a separate process without an HTTP server. Verify runsc and resolve the sandbox image to an immutable image ID before consuming messages. Execute no student code on the host and provide no fallback to the ordinary Docker runtime.

Maven runs offline with dependencies cached in the image. The runner emits bounded report JSON before exit; retained Docker logs provide output until explicit container removal. This avoids copying untrusted output directories to the host and supports temporary filesystems that disappear after shutdown. JaCoCo requires both preparation and an explicit report goal.

Atomic claims and tokens prevent stale workers from overwriting newer attempts. Failed tests receive one rerun; a later pass is marked FLAKY and earns no pass credit. Grades and a security outbox record are stored in the same transaction; a separate security worker consumes that outbox.

## Acceptance boundary

Docker service tests with a substituted sandbox validate orchestration, not gVisor containment. Real runtime tests and grading through the complete pipeline remain pending on Linux. Student projects can falsify their own reports; trusted tests controlled by instructors are required before using results as authoritative grades.

## Security scans and hints

Consume the existing security outbox directly with database claims instead of introducing another RabbitMQ delivery layer. Track scan state separately so missing or failed scans cannot be presented as clean. Use the existing gVisor execution boundary, an offline vulnerability snapshot and bounded report parsing. Refresh trusted reference data separately without any submission mounts.

Use a configurable Chat Completions provider for compatibility with hosted services and Ollama. Keep generation disabled by default. Send only bounded test evidence and finding locations; source excerpts are deferred. Persist one hint per submission with a unique constraint and expiring claims. Enforce output size and simple code rejection, while documenting that these checks do not guarantee tutoring quality or prevent every solution disclosure.

## Connected workspace

Keep sample pages under explicit demo routes. Use the API as the source of truth for uploads, scores and status. Store browser tokens only in memory for this development stage; page reloads require sign-in. Share refresh requests and cancel reads when their view changes. Use a local development proxy and an explicit production API route.

Append instructor score changes rather than replacing automated results. Lock the result and check the expected score before saving an override. Include a reason and expose review history to the submission owner. Browser workflow tests use disposable services, with stored report fixtures for review; they do not replace real sandbox acceptance.

Use native SVG for small submission trend charts and accessible meter bars for CWE counts. Keep text values available. Student trends use only authorized submission rows; instructor summaries are aggregated in PostgreSQL. Pending scans remain missing measurements. Finding fix rates need a stable identity comparison and are not inferred from a reduction in total counts.
