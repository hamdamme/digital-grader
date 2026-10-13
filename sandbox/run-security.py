#!/usr/bin/env python3
"""Static analysis runner for the isolated security image."""
import importlib.util
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import time

spec = importlib.util.spec_from_file_location("archive_reader", Path(__file__).with_name("run-grading.py"))
archive_reader = importlib.util.module_from_spec(spec)
spec.loader.exec_module(archive_reader)


def scan():
    result = {"exitCode": 2, "log": "", "junit": [], "jacoco": None, "quality": {}}
    deadline = time.monotonic() + int(os.environ["GRADE_TIMEOUT"])
    try:
        root = Path("/code/project")
        root.mkdir()
        archive_reader.extract(Path("/input/submission.zip"), root)
        for path in root.rglob("*"):
            if path.name == ".semgrepignore" and path.is_file():
                path.unlink()
            if path.suffix == ".java" and path.is_file() and path.stat().st_size > 1_000_000:
                raise ValueError("Java source exceeds static analysis limit")
        database = Path("/input/reference-data")
        # A snapshot date is supplied by the trusted database update process, not submissions.
        stamp = float((database / "updated-at").read_text().strip())
        if not 0 <= time.time() - stamp <= 7 * 86400 or not (database / "odc.mv.db").is_file():
            raise ValueError("Missing or stale vulnerability database")
        shutil.copytree(database, "/code/dependency-db")

        def run(args, output):
            with output.open("wb") as stream, Path("/output/scanner.log").open("ab") as log:
                process = subprocess.Popen(args, cwd=root, stdout=stream, stderr=log, start_new_session=True)
                try:
                    code = process.wait(timeout=max(1, deadline - time.monotonic()))
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait()
                    raise
            if code != 0:
                raise ValueError("Scanner did not complete")

        run(["/opt/scanner/bin/semgrep", "scan", "--config", "/rules/grader-rules.yml", "--json",
             "--metrics=off", "--disable-version-check", "--no-git-ignore", "--strict", "--jobs", "1",
             "--timeout", "20", "--max-memory", "1024", "--max-target-bytes", "1000000", "."], Path("/code/semgrep.json"))
        # No Maven invocation, package installation or network access in static analysis.
        run(["/opt/dependency-check/bin/dependency-check.sh", "--project", "submission", "--scan", str(root),
             "--format", "JSON", "--out", "/code/dependency-report", "--data", "/code/dependency-db",
             "--noupdate", "--disableCentral", "--disableOssIndex", "--disableNodeAudit", "--disableYarnAudit",
             "--disablePnpmAudit", "--disableVersionCheck", "--disableMavenInstall", "--disableRetireJs", "--disableHostedSuppressions", "--disableKnownExploited"], Path("/output/dependency.log"))
        result["quality"] = {
            "semgrep": archive_reader.read_regular(Path("/code/semgrep.json"), 4_000_000),
            "dependency-check": archive_reader.read_regular(Path("/code/dependency-report/dependency-check-report.json"), 4_000_000),
        }
        result["exitCode"] = 0
    except subprocess.TimeoutExpired:
        result["exitCode"] = 124
        result["log"] = "Security scan exceeded its deadline"
    except Exception as error:
        result["log"] = "Security scan incomplete: " + type(error).__name__
    print(json.dumps(result), flush=True)
    return result["exitCode"]


if __name__ == "__main__":
    sys.exit(scan())
