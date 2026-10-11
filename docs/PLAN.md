# Planned sessions

Updated October 11, 2026 (America/Chicago). Eight sessions are a working plan, not a guarantee; sandbox and security integration may require additional time.

1. **Complete:** repository, approved UI prototype, React/TypeScript, frontend tests and public GitHub repository.
2. **Complete and merged:** infrastructure, backend configuration/health, migrations, 17 backend tests and successful GitHub backend CI; PR #1 merged.
3. **Complete and merged:** typed repositories, registration/login/refresh/logout, admin provisioning, role/cohort access; 45 total backend tests passed at that checkpoint; delivered through merged PR #3.
4. **Complete and merged:** teacher cohort creation/enrollment/read APIs, validated assignments and Swagger documentation.
5. **Complete and merged:** ZIP validation, MinIO uploads/downloads, durable RabbitMQ dispatch, attempt/deadline checks and status lifecycle; 62 backend tests pass.
6. **In progress:** isolated grading worker, reports, coverage and scoring implemented; Ubuntu/gVisor acceptance is deferred until a suitable host is ready.
7. Security scans and AI guidance: findings, severity mapping, hints and caching.
8. Integration/release preparation: dashboards, overrides, end-to-end tests, CI and reproducible setup.

## Current stop point

Five of eight sessions are complete. Sessions 3–5 were delivered through merged PR #3 and successful backend CI. Session 6 has local implementation and Docker-backed pipeline tests, but no verified real runsc execution yet. See SESSION_6.md for setup, acceptance requirements and limitations. Frontend pages still use sample data.

## Commit discipline

Use one coherent change per commit and concise descriptions. Prefer plain wording without hyphenated compounds in prose; retain required names and command syntax. Production tasks T01–T32 remain subject to their actual acceptance criteria; frontend sample flows do not count as real integrations.
