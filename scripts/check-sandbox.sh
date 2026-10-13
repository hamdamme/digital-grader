#!/usr/bin/env bash
set -euo pipefail
docker info --format '{{json .Runtimes}}' | python3 -c 'import json,sys; runtimes=json.load(sys.stdin); sys.exit(0 if "runsc" in runtimes else "Missing runsc: use a Linux Docker host with gVisor installed.")'
docker image inspect --format '{{.Id}}' "${SANDBOX_IMAGE:-digital-grader-student:local}"
