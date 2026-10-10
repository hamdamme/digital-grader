#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
exec "$root/scripts/backend.sh" -B -ntp spring-boot:run \
  '-Dspring-boot.run.arguments=--spring.main.web-application-type=none --gradely.worker.enabled=true' "$@"
