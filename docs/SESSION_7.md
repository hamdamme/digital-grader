# Session 7 — security findings and tutoring hints

October 12, 2026. Branch: `session-7/security-feedback`, based on the pushed Session 6 branch. Implementation is local; real sandbox and provider acceptance remain pending.

## Security processing

A separate worker claims pending `security_outbox` rows with database locking and a unique token. Claims retry up to three times, wait 30 seconds after a failure, and expire after 20 minutes. Completion stores findings and scan status in one transaction. A stale worker cannot finish a newer claim. Grade status is independent of scan status.

This worker consumes the durable database handoff directly. It does not add a second RabbitMQ queue. The existing submission queue remains unchanged; polling the existing security outbox avoids another delivery layer for this milestone.

The security image pins Semgrep 1.180.0 and OWASP Dependency Check 13.0.0. The Dependency Check archive is verified with its published SHA-256 digest. Eight Java teaching rules cover credentials, SQL construction, output escaping, commands, paths, cryptography, deserialization and plain HTTP. These rules identify review candidates; they are not proof of exploitability or comprehensive security coverage.

Static analysis uses the same mandatory runsc boundary as grading: no network, no privileges, an immutable root filesystem, resource limits and a deadline. A trusted vulnerability snapshot is mounted read only and copied into temporary storage. No Maven build or dependency installation is performed by the static scan. Uploaded Semgrep ignore files are removed; Java files larger than 1 MB make the scan fail rather than silently appear clean.

Semgrep metadata supplies severity and CWE. Dependency Check findings use CVSS thresholds: 9 or higher is CRITICAL, 7 or higher HIGH, 4 or higher MEDIUM, and lower scores LOW. Reports have size and finding limits; malformed or incomplete reports fail the scan. Missing CVSS data is not guessed. Fixed versions and current versions remain null when not mapped; consumers must not infer that a fix is unavailable.

## Runtime setup on Linux

Build the image from the repository root:

```sh
docker build -f sandbox/Dockerfile.security -t digital-grader-security:session7 sandbox
```

Prepare a trusted vulnerability snapshot separately from student scans:

```sh
./scripts/update-security-db.sh
```

The updater uses network access only to update reference data and mounts no student archive. An optional `NVD_API_KEY` supplied in the shell environment improves access to NVD. The key is written to a temporary properties file inside the update container and is not passed as a command argument. Initial download can take a long time; the updater stops after one hour. It writes a completion timestamp only after a successful update. Failed snapshot directories are not valid inputs.

Set the printed absolute `SECURITY_DATABASE_PATH` in the ignored `.env`. Use a dedicated snapshot directory; do not edit it while a worker is scanning. A snapshot older than seven days is rejected. The worker and Docker daemon must share the same filesystem.

```sh
./scripts/security-worker.sh
```

The worker checks runsc, the image and snapshot before consuming work. It is disabled in ordinary API startup. This setup remains deferred until the Linux host is ready; no fallback runtime is provided for submissions.

## Results and hints

`GET /api/v1/submissions/{id}/result` now returns `securityStatus`, stored `securityFindings` and cached `hints`. Security status is PENDING, RUNNING, COMPLETE or FAILED. An empty findings list with pending or failed status does not mean the submission is clean. Existing student and instructor access checks apply.

`POST /api/v1/submissions/{id}/hint` returns 201 with `{content,model}`. Grading and scanning must complete first. Concurrent generation or unfinished work returns 409. A disabled or unavailable provider returns 503 with a generic message. Failures are not cached and have a short retry delay. A unique database constraint and claim token allow one cached hint per submission.

The provider implements the Chat Completions contract shared by OpenAI compatible services and Ollama. Set these values in the ignored `.env` and restart the API:

```dotenv
AI_ENABLED=false
AI_PROVIDER=ollama
AI_BASE_URL=http://127.0.0.1:11434/v1
AI_MODEL=
AI_API_KEY=
```

Set `AI_PROVIDER` to `ollama` or `openai` to select its supported token limit parameter. Choose a model available at the configured provider before enabling requests. Remote endpoints require HTTPS; HTTP is allowed only for explicit loopback addresses. Redirects and automatic connection retries are disabled. Requests have a 30 second deadline and bounded responses. API keys and provider error bodies are not included in application responses.

Enabling hints transmits up to eight test names/error excerpts and ten finding locations to that provider. This information may contain student supplied text. Whole archives, source files, stack traces and matched credential values are not intentionally included. Source excerpts are deferred until a controlled extraction path exists; `codeExcerpt` is empty. Configure hosted providers only when this transfer is appropriate for the deployment.

The tutoring prompt asks for a short diagnosis and up to three next steps, not solutions. Responses over 200 words, code fences and braces are rejected. This is a limited output check, not a guarantee that a model cannot disclose a solution. Instructor review and broader evaluation remain release work.

## Verification

Backend tests cover report parsing, severity thresholds, access controls, duplicate work, failure limits, stale claims, hint caching, concurrent requests, unavailable providers and rejected code output. HTTP provider tests use a local fixture server; no live provider requests or student data transfers were made.

The scanner image builds and its three smoke checks pass against trusted synthetic fixtures, including all eight teaching rules. Those checks run ordinary static analysis without executing the fixture code. They do not validate gVisor or a real vulnerability database. CI adds the same scanner checks.

The sandbox profile now includes a security acceptance test in addition to the two grading tests:

```sh
./scripts/backend.sh -B -ntp -Psandbox test
```

All three runtime acceptance tests remain pending on Linux with runsc and a current snapshot. Live vulnerability lookup, a known vulnerable dependency fixture, a configured model, hint quality and the complete grading to security flow still require acceptance. Sessions 6 and 7 are not marked complete.

## References

- [Semgrep CLI](https://docs.semgrep.dev/cli-reference)
- [Dependency Check CLI](https://dependency-check.github.io/DependencyCheck/dependency-check-cli/arguments.html)
- [OpenAI Chat Completions](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create)
- [Ollama compatibility](https://docs.ollama.com/api/openai-compatibility)
