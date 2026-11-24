# Source size ratchet

The gate covers first-party Kotlin and Dart files under core/src, app/src,
flutter/lib, and flutter/test. It counts physical lines, including tests.
Generated/build output and this baseline file are not source inputs.

- A path already in baseline.txt may never grow.
- A new source path may not exceed 800 physical lines.
- Deleted historical paths stay in the baseline, so recreating one cannot evade
  its old cap.

Normal use:

    python3 -m unittest discover -s tools/size -v
    python3 tools/size/check_sizes.py

After intentionally shrinking files, ratchet the baseline downward:

    python3 tools/size/check_sizes.py --write

--init replaces the baseline from the current tree and is only for initial
adoption or an explicitly approved repository-wide re-baseline. It is not part
of CI and ordinary --write refuses to raise any historical cap.
