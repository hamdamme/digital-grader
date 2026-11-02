# Running workers on a remote Linux machine

Grading and security scanning need gVisor (`runsc`) and the sandbox images,
which a small cloud host usually cannot provide. A Linux machine on any
network can act as the worker instead, consuming the server's queue over an
SSH tunnel. No service port needs to open to the internet.

## Prerequisites on the worker machine

- Docker with the `runsc` runtime installed and verified by
  `sudo docker run --rm --runtime=runsc hello-world`
- The student and security images built (`sandbox/Dockerfile.student`,
  `sandbox/Dockerfile.security`)
- Java 17 and Maven 3.9 for the worker processes
- A current vulnerability snapshot for the security worker

## Opening the tunnel

The server binds its data services to loopback, so the tunnel forwards local
ports on the worker machine to the server's loopback interfaces:

```bash
nohup ssh -N \
  -o ServerAliveInterval=30 -o ServerAliveCountMax=3 -o ExitOnForwardFailure=yes \
  -i ~/.ssh/digital-grader.pem \
  -L 5673:127.0.0.1:5673 \
  -L 15432:127.0.0.1:15432 \
  -L 19000:127.0.0.1:19000 \
  ubuntu@SERVER_ADDRESS > /tmp/server-tunnel.log 2>&1 &
```

## Worker environment

Copy the server's `.env` and point the connection settings at the tunnel:

```text
RABBIT_HOST=127.0.0.1
RABBIT_PORT=5673
DB_URL=jdbc:postgresql://127.0.0.1:15432/digital-grader
DB_PORT=15432
MINIO_ENDPOINT=http://127.0.0.1:19000
MINIO_PORT=19000
SECURITY_IMAGE=digital-grader-security:local
SECURITY_DATABASE_PATH=/absolute/path/to/security-db.oITlFT
```

Keep the queue, database, storage, and signing credentials identical to the
server values.

## Starting the workers

```bash
cd digital-grader
nohup ./scripts/worker.sh          > /tmp/grading-worker.log  2>&1 &
nohup ./scripts/security-worker.sh > /tmp/security-worker.log 2>&1 &
```

The grading worker consumes submission messages and runs the student
sandbox under gVisor. The security worker consumes scan messages and runs the
dependency scan with the vulnerability snapshot. Both write results back
through the tunnel.

## Verifying a scan

A completed scan appears as `COMPLETE` in the `security_outbox` table:

```sql
SELECT submission_id, status FROM security_outbox ORDER BY submission_id DESC;
```

Hints for a submission unlock after its row reaches `COMPLETE` and the
submission itself is graded.
