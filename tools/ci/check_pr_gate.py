#!/usr/bin/env python3
"""Guard the stable PR aggregate without requiring a YAML dependency."""
from __future__ import annotations

from pathlib import Path
import re
import sys

DEFAULT_WORKFLOW = Path(__file__).resolve().parents[2] / ".github/workflows/ci.yml"


def check(workflow: Path) -> list[str]:
    text = workflow.read_text(encoding="utf-8")
    lines = text.splitlines()
    errors: list[str] = []
    if not re.search(r"^  pull_request:\s*$", text, re.MULTILINE):
        errors.append("pull_request trigger is missing")
    try:
        jobs_at = next(i for i, line in enumerate(lines) if line == "jobs:")
    except StopIteration:
        return errors + ["jobs block is missing"]
    jobs: dict[str, list[str]] = {}
    current: str | None = None
    for line in lines[jobs_at + 1:]:
        match = re.match(r"^  ([A-Za-z0-9_-]+):\s*$", line)
        if match:
            current = match.group(1)
            jobs[current] = []
        elif current is not None:
            jobs[current].append(line)
    if "pr-gate" not in jobs:
        return errors + ["stable pr-gate job is missing"]
    block = jobs["pr-gate"]
    if not any(line.strip() == "name: pr-gate" for line in block):
        errors.append("pr-gate must keep the stable display name 'pr-gate'")
    if not any("always()" in line for line in block):
        errors.append("pr-gate must run with always() so failures are aggregated")
    needs: set[str] = set()
    in_needs = False
    for line in block:
        stripped = line.strip()
        if stripped.startswith("needs:"):
            value = stripped.partition(":")[2].strip()
            if value.startswith("[") and value.endswith("]"):
                needs.update(item.strip() for item in value[1:-1].split(",") if item.strip())
            else:
                in_needs = True
        elif in_needs and stripped.startswith("- "):
            needs.add(stripped[2:].strip())
        elif in_needs and stripped:
            in_needs = False
    missing = set(jobs) - {"pr-gate"} - needs
    if missing:
        errors.append("pr-gate is missing dependencies: " + ", ".join(sorted(missing)))
    return errors


def main() -> int:
    errors = check(DEFAULT_WORKFLOW)
    if errors:
        print("PR gate topology failed:", file=sys.stderr)
        print("\n".join(errors), file=sys.stderr)
        return 1
    print("PR gate topology checked")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
