# Architecture

## Components

| Component | Responsibility | Current status |
|---|---|---|
| React frontend | Teacher and student workspaces | Demonstration data; API integration pending |
| Spring Boot API | Authentication, cohorts, assignments, uploads and result access | Implemented |
| PostgreSQL | Application records, grading claims and outbox entries | Integrated |
| MinIO | Private submissions and grading artifacts | Integrated for local development |
| RabbitMQ | Submission delivery | Integrated |
| Standalone worker | Claims, sandbox execution, report parsing and scoring | Implemented; real runtime acceptance pending |
| gVisor sandbox | Isolated Java builds with bounded resources | Requires Linux verification |
| Security and hint services | Findings and AI guidance | Planned |

## Submission flow

1. An authenticated student uploads a ZIP for an accessible assignment.
2. The API checks membership, deadline, attempt limits and archive safety.
3. The archive is stored privately; submission and outbox records are persisted.
4. The dispatcher publishes the submission ID to RabbitMQ with confirmation.
5. A worker claims the submission with a unique token and downloads its input.
6. A gVisor container runs an offline build with no network and bounded resources.
7. The worker parses bounded reports, performs an optional rerun, and calculates rubric scores.
8. A transaction stores the result, completes the submission and records pending security work.
9. Authorized users can request results and a temporary build log download URL.

Steps involving actual student execution still require acceptance on Linux. Frontend demonstration flows do not call this pipeline.

## Failure handling

Queue delivery can repeat. Atomic claims prevent duplicate grading, and token checks reject stale completion attempts. Infrastructure errors retry up to three attempts; expired claims can be recovered. Deadlines produce TIMEOUT. Worker or host crashes can still leave objects or containers requiring reconciliation.

## Trust boundaries

The API validates access and archive structure. The worker host does not extract or execute student code. The sandbox has no network and receives only a read only input archive. Report parsing is bounded and XML external access is disabled. Reports produced by student projects are not trusted evidence without a test harness controlled by instructors.

See [technical decisions](../DECISIONS.md), [API contracts](SESSION_4_5.md), and [worker limits](SESSION_6.md).
