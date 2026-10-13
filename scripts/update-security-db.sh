#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$root/.data"
snapshot="$(mktemp -d "$root/.data/security-db.XXXXXX")"
# Only trusted reference data is mounted; no project source or submissions.
docker run --rm --user "$(id -u):$(id -g)" --env HOME=/tmp --env NVD_API_KEY \
  --mount "type=bind,src=$snapshot,dst=/database" \
  --entrypoint python3 "${SECURITY_IMAGE:-digital-grader-security:local}" \
  -I /opt/digital-grader/update-security-db.py
chmod -R a+rX "$snapshot"
printf 'Set SECURITY_DATABASE_PATH=%s in your local .env\n' "$snapshot"
