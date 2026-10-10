#!/usr/bin/env python3
"""Runs only inside the isolated worker image; never invoke on a host submission."""
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import signal
import stat
import subprocess
import sys
import time
import zipfile


def extract(archive, target):
    total = 0
    names = set()
    with zipfile.ZipFile(archive) as zip_file:
        entries = zip_file.infolist()
        if not 1 <= len(entries) <= 2000:
            raise ValueError("Invalid entry count")
        for entry in entries:
            name = entry.filename.rstrip("/")
            path = PurePosixPath(name)
            kind = (entry.external_attr >> 16) & 0o170000
            if (not name or path.is_absolute() or "\\" in name or ":" in name
                    or any(p in ("", ".", "..") for p in name.split("/"))
                    or any(ord(c) < 32 for c in name) or name.lower() in names
                    or kind not in (0, stat.S_IFREG, stat.S_IFDIR) or entry.flag_bits & 1):
                raise ValueError("Unsafe ZIP entry")
            names.add(name.lower())
            destination = target.joinpath(*path.parts)
            if entry.is_dir():
                destination.mkdir(parents=True, exist_ok=True)
                continue
            destination.parent.mkdir(parents=True, exist_ok=True)
            size = 0
            with zip_file.open(entry) as source, destination.open("xb") as output:
                while chunk := source.read(8192):
                    size += len(chunk)
                    total += len(chunk)
                    if size > 50*1024*1024 or total > 200*1024*1024 or size > max(1, entry.compress_size)*100:
                        raise ValueError("Expanded ZIP limit exceeded")
                    output.write(chunk)


def read_regular(path, limit=2_000_000):
    # Resolve parents without following project-created symlinks before opening the final file.
    root = Path("/code")
    relative = path.relative_to(root)
    fd = os.open(root, os.O_DIRECTORY | os.O_NOFOLLOW)
    try:
        for part in relative.parts[:-1]:
            next_fd = os.open(part, os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=fd)
            os.close(fd)
            fd = next_fd
        file_fd = os.open(relative.name, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK, dir_fd=fd)
        with os.fdopen(file_fd, "rb") as source:
            if not stat.S_ISREG(os.fstat(source.fileno()).st_mode):
                raise ValueError("Non-regular report")
            data = source.read(limit + 1)
            if len(data) > limit:
                raise ValueError("Report size exceeded")
            return data.decode("utf-8", errors="replace")
    finally:
        os.close(fd)


def grade():
    deadline = time.monotonic() + int(os.environ["GRADE_TIMEOUT"])
    output = {"exitCode": 2, "log": "", "junit": [], "jacoco": None, "quality": {}}
    log_path = Path("/output/build.log")
    try:
        root = Path("/code/project")
        root.mkdir()
        extract(Path("/input/submission.zip"), root)
        if not (root / "pom.xml").is_file():
            children = list(root.iterdir())
            if len(children) == 1 and children[0].is_dir() and (children[0] / "pom.xml").is_file():
                root = children[0]
            else:
                raise ValueError("A root Maven pom.xml is required")
        # Ignore uploaded build products; reports must come from this attempt.
        if (root / "target").exists():
            shutil.rmtree(root / "target")
        shutil.copytree("/opt/maven-cache", "/code/repository")
        base = ["mvn", "-o", "-B", "-Dmaven.repo.local=/code/repository", "-DspotbugsXmlOutput=true"]
        def run(goals, log):
            remaining = max(1, deadline-time.monotonic())
            process = subprocess.Popen(base+goals, cwd=root, stdout=log, stderr=log, start_new_session=True)
            try:
                return process.wait(timeout=remaining)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()
                raise
        with log_path.open("wb") as log:
            code = run(["org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent", "test"], log)
            run(["org.jacoco:jacoco-maven-plugin:0.8.12:report"], log)
            for tool, goal in (("checkstyle", "org.apache.maven.plugins:maven-checkstyle-plugin:3.6.0:checkstyle"),
                               ("spotbugs", "com.github.spotbugs:spotbugs-maven-plugin:4.9.6.0:spotbugs")):
                run([goal], log)
        report_dir = root / "target/surefire-reports"
        if report_dir.is_symlink():
            raise ValueError("Unsafe report directory")
        reports = sorted(report_dir.glob("TEST-*.xml"))
        if len(reports) > 1000:
            raise ValueError("Too many reports")
        output["junit"] = [read_regular(path) for path in reports]
        jacoco = root / "target/site/jacoco/jacoco.xml"
        if jacoco.exists():
            output["jacoco"] = read_regular(jacoco)
        for tool, filename in (("checkstyle", "checkstyle-result.xml"), ("spotbugs", "spotbugsXml.xml")):
            path = root / "target" / filename
            if path.exists():
                output["quality"][tool] = read_regular(path)
        output["exitCode"] = 0 if code == 0 else 1 if reports else 2
    except subprocess.TimeoutExpired:
        output["exitCode"] = 124
    except Exception as error:
        output["log"] = "Grading failed: " + type(error).__name__
    if log_path.exists():
        with log_path.open("rb") as log:
            output["log"] += log.read(180000).decode("utf-8", errors="replace")
    encoded = json.dumps(output, ensure_ascii=True)
    if len(encoded) > 10_000_000:
        output = {"exitCode": 2, "log": "Result bundle exceeded limit", "junit": [], "jacoco": None, "quality": {}}
        encoded = json.dumps(output)
    print(encoded, flush=True)
    return output["exitCode"]


if __name__ == "__main__":
    sys.exit(grade())
