from pathlib import Path
import tempfile
import unittest

from check_pr_gate import check


class PrGateTopologyTest(unittest.TestCase):
    def workflow(self, jobs: str, trigger: bool = True) -> Path:
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        path = Path(temp.name) / "ci.yml"
        prefix = "on:\n  pull_request:\n" if trigger else "on:\n  push:\n"
        path.write_text(prefix + "jobs:\n" + jobs, encoding="utf-8")
        return path

    def test_all_jobs_are_required(self) -> None:
        path = self.workflow(
            "  first:\n    runs-on: ubuntu\n"
            "  second:\n    runs-on: ubuntu\n"
            "  pr-gate:\n    name: pr-gate\n    if: always()\n"
            "    needs: [first, second]\n    runs-on: ubuntu\n"
        )
        self.assertEqual([], check(path))

    def test_missing_dependency_and_trigger_fail(self) -> None:
        path = self.workflow(
            "  first:\n    runs-on: ubuntu\n"
            "  extra:\n    runs-on: ubuntu\n"
            "  pr-gate:\n    name: pr-gate\n    if: always()\n"
            "    needs:\n      - first\n    runs-on: ubuntu\n",
            trigger=False,
        )
        errors = check(path)
        self.assertTrue(any("pull_request" in error for error in errors))
        self.assertTrue(any("extra" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
