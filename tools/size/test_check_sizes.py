from contextlib import redirect_stderr
from io import StringIO
from pathlib import Path
import tempfile
import unittest

from check_sizes import (
    MAX_NEW_LINES,
    load_baseline,
    main,
    source_files,
    violations,
    write_baseline,
)


class SizeGateTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.baseline = self.root / "baseline.txt"

    def tearDown(self) -> None:
        self.temp.cleanup()

    def source(self, name: str, lines: int) -> None:
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("x\n" * lines, encoding="utf-8")

    def test_initial_baseline_uses_real_current_counts(self) -> None:
        self.source("core/src/Legacy.kt", 901)
        self.source("flutter/lib/new.dart", 12)
        self.assertEqual([], write_baseline(self.root, self.baseline, {}))
        saved = load_baseline(self.baseline)
        self.assertEqual(901, saved["core/src/Legacy.kt"])
        self.assertEqual(12, saved["flutter/lib/new.dart"])
        self.assertEqual([], violations(self.root, saved))

    def test_historical_growth_fails_and_does_not_raise_baseline(self) -> None:
        self.source("app/src/Test.kt", 10)
        write_baseline(self.root, self.baseline, {})
        before = self.baseline.read_bytes()
        self.source("app/src/Test.kt", 11)
        self.assertTrue(violations(self.root, load_baseline(self.baseline)))
        self.assertTrue(write_baseline(self.root, self.baseline, load_baseline(self.baseline)))
        self.assertEqual(before, self.baseline.read_bytes())

    def test_shrink_ratchets_baseline_down(self) -> None:
        self.source("core/src/Shrink.kt", 20)
        write_baseline(self.root, self.baseline, {})
        self.source("core/src/Shrink.kt", 15)
        self.assertEqual([], write_baseline(self.root, self.baseline, load_baseline(self.baseline)))
        self.assertEqual(15, load_baseline(self.baseline)["core/src/Shrink.kt"])

    def test_new_file_limit_is_800_and_counts_physical_lines(self) -> None:
        self.source("core/src/Existing.kt", 1)
        write_baseline(self.root, self.baseline, {})
        self.source("core/src/New.kt", MAX_NEW_LINES)
        self.assertEqual([], violations(self.root, load_baseline(self.baseline)))
        self.source("core/src/New.kt", MAX_NEW_LINES + 1)
        errors = violations(self.root, load_baseline(self.baseline))
        self.assertEqual(1, len(errors))
        self.assertIn("new file exceeds 800 lines", errors[0])

    def test_check_refuses_a_missing_baseline(self) -> None:
        self.source("core/src/Only.kt", 1)
        with redirect_stderr(StringIO()):
            code = main(["--root", str(self.root), "--baseline", str(self.baseline)])
        self.assertEqual(2, code)

    def test_deleted_historical_path_keeps_its_cap(self) -> None:
        self.source("core/src/Gone.kt", 30)
        write_baseline(self.root, self.baseline, {})
        (self.root / "core/src/Gone.kt").unlink()
        self.assertEqual([], write_baseline(self.root, self.baseline, load_baseline(self.baseline)))
        self.assertEqual(30, load_baseline(self.baseline)["core/src/Gone.kt"])
        self.source("core/src/Gone.kt", 31)
        self.assertTrue(violations(self.root, load_baseline(self.baseline)))


if __name__ == "__main__":
    unittest.main()
