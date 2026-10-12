"""Smoke checks for trusted scanner fixtures; no submitted code is executed."""
import json
from pathlib import Path
import subprocess
import tempfile
import unittest


class RulesTest(unittest.TestCase):
    def scan(self, source):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "Fixture.java"
            path.write_text(source)
            result = subprocess.run(["/opt/scanner/bin/semgrep", "scan", "--config", "/rules/grader-rules.yml",
                                     "--json", "--metrics=off", "--disable-version-check", "--no-git-ignore",
                                     "--strict", str(path)], capture_output=True, text=True, timeout=60)
            self.assertEqual(result.returncode, 0, result.stderr)
            report = json.loads(result.stdout)
            self.assertEqual(report["errors"], [])
            return report["results"]

    def test_detects_all_teaching_rules(self):
        findings = self.scan('''import java.io.*; import java.net.*; import java.security.*; import javax.crypto.*;
class Fixture {
  void check(String user, java.sql.Statement statement, java.io.PrintWriter out) throws Exception {
    String password = "fixture-only-not-a-real-secret";
    statement.execute("SELECT name FROM users WHERE name=" + user);
    out.println(user);
    Runtime.getRuntime().exec("echo " + user);
    new File("/tmp/" + user);
    MessageDigest.getInstance("MD5");
    new ObjectInputStream(new ByteArrayInputStream(new byte[0])).readObject();
    new URL("http://example.invalid").openConnection();
  }
}''')
        ids = {f["check_id"].split(".")[-1] for f in findings}
        self.assertEqual(ids, {"grader-hardcoded-secret", "grader-sql-concat", "grader-xss", "grader-cmd-injection",
                               "grader-path-traversal", "grader-weak-crypto", "grader-insecure-deser", "grader-missing-tls"})
        for finding in findings:
            self.assertIn(finding["extra"]["metadata"]["grader_severity"], {"CRITICAL", "HIGH", "MEDIUM", "LOW"})

    def test_clean_fixture_has_no_findings(self):
        self.assertEqual(self.scan('class Fixture { int add(int a, int b) { return a + b; } }'), [])

    def test_dependency_check_options_exist(self):
        result = subprocess.run(["/opt/dependency-check/bin/dependency-check.sh", "--advancedHelp"],
                                capture_output=True, text=True, timeout=30)
        self.assertEqual(result.returncode, 0)
        for flag in ("--noupdate", "--disableRetireJs", "--disableMavenInstall", "--disableHostedSuppressions"):
            self.assertIn(flag, result.stdout)


if __name__ == "__main__":
    unittest.main()
