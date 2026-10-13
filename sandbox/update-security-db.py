"""Refresh trusted vulnerability data without mounting a submission."""
import os
from pathlib import Path
import re
import subprocess
import tempfile
import time

command = ["/opt/dependency-check/bin/dependency-check.sh", "--updateonly", "--data", "/database",
           "--disableOssIndex", "--disableRetireJs", "--disableHostedSuppressions", "--disableKnownExploited",
           "--disableVersionCheck"]
key = os.environ.get("NVD_API_KEY", "")
with tempfile.TemporaryDirectory() as directory:
    if key:
        if not re.fullmatch(r"[A-Za-z0-9-]{1,200}", key):
            raise SystemExit("Invalid NVD API key format")
        settings = Path(directory) / "settings.properties"
        settings.write_text("nvd.api.key=" + key + "\n")
        settings.chmod(0o600)
        command += ["--propertyfile", str(settings)]
    result = subprocess.run(command, timeout=3600)
    if result.returncode != 0 or not Path("/database/odc.mv.db").is_file():
        raise SystemExit("Database refresh failed; do not use this snapshot")
    Path("/database/updated-at").write_text(str(int(time.time())) + "\n")
