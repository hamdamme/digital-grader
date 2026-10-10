# Session 3 — authentication and permissions

Completed locally October 9, 2026 (America/Chicago), on `session-3/authentication`.

## Delivered

Student registration, BCrypt password hashing (cost 10), login, signed access tokens, rotating refresh tokens, logout, current-user identity, admin-only instructor provisioning, and instructor cohort read access. Typed JDBC repositories reuse the existing Flyway schema. There is no UI login integration yet; the approved frontend still uses sample data.

## Run and verify

Start Docker Desktop, then use the setup from [Session 2](SESSION_2.md). From the repository root:

```sh
./scripts/backend.sh -B -ntp verify
./scripts/backend.sh spring-boot:run
```

Stop an older backend process in its own terminal before starting the new version on port 8080. An already-running Session 2 process does not automatically load these changes. Database tests run against disposable PostgreSQL containers, not your Compose database. See Session 2's isolated Docker configuration workaround if the credential helper is unavailable in an agent session.

Flyway applies V2 on startup. It adds a unique index over `lower(email)`. If pre-existing case-variant duplicates exist, migration fails for manual resolution; it never deletes user data.

## API

All paths below begin with `/api/v1`. JSON is required for POST bodies. Authentication uses `Authorization: Bearer <accessToken>`.

| Method | Path | Access / result |
|---|---|---|
| POST | `/auth/register` | Public; `{email,password,fullName,role:"STUDENT"}` → 201 public profile |
| POST | `/auth/login` | Public; `{email,password}` → accessToken, refreshToken, role |
| POST | `/auth/refresh` | Public; `{refreshToken}` → replacement token pair and current role |
| POST | `/auth/logout` | Public; `{refreshToken}` → 204; idempotently revokes that refresh token |
| GET | `/auth/me` | Authenticated; id, email, fullName, role |
| POST | `/admin/users` | ADMIN only; registration fields with STUDENT or INSTRUCTOR role → 201 |
| GET | `/cohorts/mine` | INSTRUCTOR/ADMIN; cohorts owned by the caller (an admin may have none) |
| GET | `/cohorts/{id}` | Owning INSTRUCTOR or ADMIN; other callers denied |

Public instructor/admin registration is forbidden. Student access to instructor cohort endpoints is forbidden, including POST `/cohorts`; cohort creation itself belongs to Session 4. A reusable membership policy is tested for future student assignment APIs, which are not implemented yet. Unknown and unauthorized routes remain closed.

Passwords must contain at least 12 characters and fit within BCrypt's 72-byte UTF-8 limit. Emails are stored lowercase. Duplicate registration returns 409. Invalid input returns 400 without echoing submitted passwords/tokens. Bad credentials/tokens return generic 401; insufficient permission returns 403. Cross-cohort and missing-cohort detail requests are both denied before data is returned.

## Token behavior

Access tokens are HS256 JWTs with Digital Grader issuer, user ID, role, access usage, issuance and expiration claims. The filter checks the database user on each authenticated request; deletion or a role change invalidates the old access token.

Refresh tokens are 32 random bytes encoded as base64url. Only SHA-256 hashes are stored. Refresh consumes the old row and inserts its replacement in one database transaction; concurrent reuse yields one successful request, and a failed replacement rolls back consumption. Expired tokens and replay are rejected. Expired database rows are not yet periodically purged.

Logout revokes only the supplied refresh token. Existing access tokens remain valid until expiration (default 60 minutes); other devices remain logged in. Global logout, token-family replay revocation and password reset are not implemented.

`JWT_ACCESS_TTL_MINUTES` accepts 1–60; `JWT_REFRESH_TTL_DAYS` accepts 1–30 (default 7). `JWT_SECRET` remains required with at least 64 characters; use the generated random value from the existing environment setup. Keep secrets and tokens out of Git and logs.

CORS allows only exact origins from comma-separated `CORS_ALLOWED_ORIGINS`; default `http://127.0.0.1:5176`. The preview at port 5175 would need an explicit origin entry when connecting it to the API. No cookies or server sessions are used. CSRF is disabled for this bearer-token API; revisit that decision if cookie authentication is introduced.

## First admin (optional, owner-configured)

No default account or password is seeded. To provision the first admin, add all three variables to your ignored `.env`, using valid shell quoting for values with spaces:

- `BOOTSTRAP_ADMIN_EMAIL`: your chosen account email.
- `BOOTSTRAP_ADMIN_NAME`: your display name.
- `BOOTSTRAP_ADMIN_PASSWORD`: a unique password meeting the rules above.

Start the backend once, then remove these bootstrap variables from `.env` and restart. Log in through `/auth/login`, then use `/admin/users` to provision instructor accounts. Never paste the password or issued tokens into chat or commit them. Re-running bootstrap for an existing admin does not reset credentials; it refuses to promote an existing student/instructor with the same email. Partial or invalid bootstrap settings fail startup.

## Verification and stop point

45 backend tests pass with no skips; Maven verify builds the executable JAR. Coverage includes registration validation, hashed credentials, JWT tampering/expiry/issuer/usage/algorithm, refresh replay/concurrency/rollback, logout, deleted users, changed roles, instructor ownership, student membership, admin provisioning/bootstrap, CORS, and the earlier database/health checks.

Session 2 was merged through PR #1 and its GitHub checks passed. Session 3 is locally verified; its push and remote CI are not yet verified. Frontend files were unchanged. The unrelated untracked `gradely_mindtek/` folder was left untouched.

Stop here. Session 4 adds teacher cohort creation, membership and assignment/rubric management. Before a public deployment, add account recovery, email verification, login abuse protection, transport/deployment hardening and the remaining release checks. This is a backend development checkpoint, not a production release.
