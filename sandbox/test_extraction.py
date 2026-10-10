import io
from pathlib import Path
import stat
import tempfile
import unittest
import zipfile
import importlib.util
spec = importlib.util.spec_from_file_location("digital_grader_runner", Path(__file__).with_name("run-grading.py"))
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)
extract = runner.extract


class ExtractionTest(unittest.TestCase):
    def fixture(self, name, content=b"fixture", mode=0):
        data = io.BytesIO()
        with zipfile.ZipFile(data, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            entry = zipfile.ZipInfo(name)
            entry.compress_type = zipfile.ZIP_DEFLATED
            if mode:
                entry.external_attr = mode << 16
            archive.writestr(entry, content)
        data.seek(0)
        return data

    def test_normal_files(self):
        with tempfile.TemporaryDirectory() as directory:
            extract(self.fixture("src/Test.java"), Path(directory))
            self.assertEqual((Path(directory) / "src/Test.java").read_bytes(), b"fixture")

    def test_rejects_traversal_and_symlinks(self):
        for name in ("../escape", "/absolute", "C:/absolute", "bad\\path", "a//b"):
            with tempfile.TemporaryDirectory() as directory, self.assertRaises(ValueError):
                extract(self.fixture(name), Path(directory))
        with tempfile.TemporaryDirectory() as directory, self.assertRaises(ValueError):
            extract(self.fixture("link", b"/etc/passwd", stat.S_IFLNK | 0o777), Path(directory))

    def test_bounds_expansion(self):
        with tempfile.TemporaryDirectory() as directory, self.assertRaises(ValueError):
            extract(self.fixture("bomb", bytes(1024 * 1024)), Path(directory))


if __name__ == "__main__":
    unittest.main()
