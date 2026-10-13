#!/usr/bin/env python3
"""Check browser flows against disposable services; never run a submission."""
import os
from pathlib import Path
import secrets
import socket
import subprocess
import sys
import time
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent.parent
RUN = ROOT / ".data" / ("browser-smoke-" + secrets.token_hex(4))
RUN.mkdir(parents=True)
containers, processes, logs = [], [], []

def command(*args):
    return subprocess.check_output(args, cwd=ROOT, text=True).strip()

def container(image, port, variables, *args):
    name = "dg-smoke-" + secrets.token_hex(5)
    command("docker", "run", "--detach", "--name", name, "--publish", f"127.0.0.1::{port}",
            *[item for key, value in variables.items() for item in ("--env", f"{key}={value}")], image, *args)
    containers.append(name)
    return int(command("docker", "port", name, str(port)).rsplit(":", 1)[1])

def port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]

def start(args, name, env):
    log = (RUN / (name + ".log")).open("w")
    logs.append(log)
    process = subprocess.Popen(args, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
    processes.append(process)
    return process

def wait(url, process, timeout=90):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError(f"Service exited. See {RUN}")
        try:
            with urllib.request.urlopen(url, timeout=2) as response:
                if response.status == 200:
                    return
        except (OSError, TimeoutError):
            pass
        time.sleep(1)
    raise RuntimeError(f"Service did not become ready. See {RUN}")

try:
    secret = secrets.token_urlsafe(24)
    postgres = container("postgres:16", 5432, {"POSTGRES_USER": "smoke", "POSTGRES_PASSWORD": secret, "POSTGRES_DB": "smoke"})
    rabbit = container("rabbitmq:3.13-management", 5672, {"RABBITMQ_DEFAULT_USER": "smoke", "RABBITMQ_DEFAULT_PASS": secret})
    minio = container("digital-grader-minio:2025-10-15", 9000, {"MINIO_ROOT_USER": "smoke-access", "MINIO_ROOT_PASSWORD": secret}, "server", "/data")
    backend_port, frontend_port = port(), port()
    env = dict(os.environ, DB_URL=f"jdbc:postgresql://127.0.0.1:{postgres}/smoke", DB_USERNAME="smoke", DB_PASSWORD=secret,
               RABBIT_HOST="127.0.0.1", RABBIT_PORT=str(rabbit), RABBIT_USERNAME="smoke", RABBIT_PASSWORD=secret,
               MINIO_ENDPOINT=f"http://127.0.0.1:{minio}", MINIO_ACCESS_KEY="smoke-access", MINIO_SECRET_KEY=secret,
               MINIO_BUCKET="smoke-submissions", JWT_SECRET=secrets.token_urlsafe(64), AI_ENABLED="false",
               BOOTSTRAP_ADMIN_EMAIL="admin@smoke.local", BOOTSTRAP_ADMIN_NAME="Smoke Admin", BOOTSTRAP_ADMIN_PASSWORD=secret,
               SERVER_PORT=str(backend_port), SERVER_ADDRESS="127.0.0.1", CORS_ALLOWED_ORIGINS=f"http://127.0.0.1:{frontend_port}",
               API_PROXY_TARGET=f"http://127.0.0.1:{backend_port}", VITE_API_BASE_URL="/api/v1", SMOKE_BASE_URL=f"http://127.0.0.1:{frontend_port}",
               SMOKE_PASSWORD=secret, SMOKE_OUTPUT=str(RUN), SMOKE_DB_CONTAINER=containers[0])
    java_home = env.get("JAVA_HOME", "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home")
    java = str(Path(java_home) / "bin/java") if Path(java_home).exists() else "java"
    # Wait for database readiness before Spring creates its pool.
    for _ in range(60):
        if subprocess.run(["docker", "exec", containers[0], "pg_isready", "-U", "smoke"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0:
            break
        time.sleep(1)
    backend = start([java, "-jar", str(ROOT / "backend/target/digital-grader-backend-0.1.0-SNAPSHOT.jar"),
                     "--digital-grader.dispatch.enabled=false", "--digital-grader.worker.enabled=false", "--digital-grader.security.enabled=false"], "backend", env)
    wait(f"http://127.0.0.1:{backend_port}/api/v1/health", backend)
    frontend = start(["node", str(ROOT / "frontend/node_modules/vite/bin/vite.js"), str(ROOT / "frontend"), "--host", "127.0.0.1", "--port", str(frontend_port), "--strictPort"], "frontend", env)
    wait(env["SMOKE_BASE_URL"], frontend)
    with zipfile.ZipFile(RUN / "project.zip", "w") as archive:
        archive.writestr("pom.xml", "<project><modelVersion>4.0.0</modelVersion><groupId>fixture</groupId><artifactId>example</artifactId><version>1</version></project>")
        archive.writestr("src/test/java/ExampleTest.java", "// Browser upload fixture. Never executed.\n")
    subprocess.run(["node", str(ROOT / "frontend/e2e/smoke.mjs")], cwd=ROOT, env=env, check=True)
    print(f"Browser checks passed. Screenshots and logs: {RUN}")
finally:
    for process in reversed(processes):
        process.terminate()
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
    for log in logs:
        log.close()
    for name in containers:
        subprocess.run(["docker", "rm", "--force", "--volumes", name], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
