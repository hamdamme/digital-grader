# Digital Grader

Grading and security feedback for students learning Java test automation.

## Status

The backend implements authentication, cohort and assignment management, validated ZIP uploads, private storage and queue dispatch. A grading worker, report parsing, scoring and result APIs are implemented locally. Real sandbox acceptance on Linux remains pending.

The React frontend connects to the API for classroom setup, uploads, results and instructor reviews. A separate demo area contains labeled sample data. Security processing and cached AI hints are implemented, with runtime and provider acceptance pending. See [project status](docs/HANDOFF.md) for verification and remaining work, or [Project Overview](docs/PROJECT_OVERVIEW.md) for a presentation of the product.

## Local setup

Requirements: Docker with Compose v2, Java 17, Maven 3.9 and Python 3. The frontend requires Node.js 24 LTS, with a minimum supported version of 22.12.

From the repository root:

```sh
python3 scripts/init-dev-env.py   # first setup only; preserves an existing .env
docker compose up -d --build --wait --wait-timeout 180
./scripts/backend.sh spring-boot:run
```

The API health endpoint is http://127.0.0.1:8080/api/v1/health. API documentation is available at http://127.0.0.1:8080/swagger-ui/index.html.

The first MinIO build may take several minutes. It uses pinned source for local development; production storage selection remains open. See [backend setup](docs/SESSION_2.md) for service ports, configuration and troubleshooting, and [authentication](docs/SESSION_3.md) for initial administrator setup.

Start the frontend in a separate terminal:

```sh
cd frontend
npm ci
npm run dev
```

Open http://127.0.0.1:5176. To inspect a production build locally, use `npm run build` followed by `npm run preview`, then open http://127.0.0.1:5175.

Sign in to use the connected workspace, or open `/#/demo/studio` for the sample design. Student registration is available in the interface; instructor accounts require administrator provisioning. Sessions stay in memory, so reloading requires sign-in. Uploads remain queued unless a supported worker is running. See [worker setup](docs/SESSION_6.md) for Linux and gVisor requirements.

## Verification

```sh
./scripts/backend.sh -B -ntp verify
python3 sandbox/test_extraction.py
cd frontend
npm test
npm run build
```

Backend integration tests require Docker and use disposable services. The standard suite substitutes sandbox execution; actual runtime tests use the separate sandbox profile documented in the worker guide. GitHub workflows check relevant backend and frontend changes. A local pass does not establish remote CI success.

## Project structure

| Path | Contents |
|---|---|
| `backend/` | Spring Boot API, worker, migrations and tests |
| `frontend/` | React pages, shared components and demonstration fixtures |
| `sandbox/` | Grading image, bounded runner and sample Java project |
| `infra/minio/` | Pinned MinIO source build |
| `scripts/` | Environment setup and launch scripts |
| `.github/workflows/` | Backend, frontend and browser checks |
| `docs/` | Requirements, setup guides and milestone records |

## Documentation

- [Project Overview and Development Report](docs/PROJECT_OVERVIEW.md)
- [Connected workspace and browser verification](docs/SESSION_8.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Technical decisions](DECISIONS.md)
- [Project requirements](docs/BUILD_SPEC.md)
- [Current progress](docs/HANDOFF.md) and [planned sessions](docs/PLAN.md)
- [Teacher and submission APIs](docs/SESSION_4_5.md)
- [Security scans and hints](docs/SESSION_7.md)
- [Naming and existing installation compatibility](docs/RENAMING.md)

Requirements describe the target product. The implemented dependency versions are recorded in Maven and npm manifests and the npm lockfile. Planned features and original baseline versions do not override the current implementation or its documented limitations.

## Development

Use focused branches and commits that describe concrete changes. Prefer plain wording in descriptions, while preserving required technical identifiers. Run checks relevant to each change and record unresolved acceptance work. Keep credentials, dependencies, generated output and student submissions out of Git.

Public repository: [hamdamme/digital-grader](https://github.com/hamdamme/digital-grader).

## License

MIT — see [LICENSE](LICENSE). Dependencies retain their own licenses; see [third party notices](THIRD_PARTY.md).
