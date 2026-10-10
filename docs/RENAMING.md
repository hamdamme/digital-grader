# Digital Grader rename — October 10, 2026

The display name is Digital Grader. Repository, npm/Maven artifacts, images and configuration prefixes use digital-grader; Java packages use com.digitalgrader. Git history is retained.

GitHub: https://github.com/hamdamme/digital-grader. The repository ID is unchanged and its former URL redirects. Local origin targets the new URL. The intended folder is /Users/nazar/Desktop/PROJECTS/digital-grader; filesystem controls denied the agent's folder move, so the owner must perform that final move in Terminal.

## Existing local data

Existing .env credentials, database names and persisted service data are preserved. The ignored local .env pins COMPOSE_PROJECT_NAME to the pre-rename project and SUBMISSION_QUEUE to the existing queue. These compatibility identifiers are not public branding. Fresh installations use the new names from .env.example and docker-compose.yml. Do not delete existing volumes or regenerate credentials merely to rename them. Both publisher and listener honor SUBMISSION_QUEUE.

Restart API and worker together when adopting this build. Configuration properties now start with digital-grader; use the updated worker launcher. Access JWTs with the old issuer require a fresh login or refresh. Refresh token storage and password hashes are unchanged. Rebuild sandbox images with the new tags. The student image was rebuilt and the unchanged MinIO image retagged locally.

Unrelated user-owned reference files are untouched. Git history and ignored caches may contain the former name.

## Verification

76 backend tests passed after a clean build, 13 frontend tests passed, TypeScript/Vite build passed, and three ZIP extraction tests passed. The renamed sandbox image built. Real runsc acceptance is deferred until the owner's Ubuntu computer is ready; Session 6 remains incomplete.
