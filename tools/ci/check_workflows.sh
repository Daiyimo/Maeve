#!/usr/bin/env bash
# Validate Actions syntax locally or on CI with the same pinned validator.
set -euo pipefail

maeve_repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
maeve_lint_dir="$(mktemp -d)"
trap 'rm -rf -- "$maeve_lint_dir"' EXIT

if command -v actionlint >/dev/null 2>&1; then
  maeve_actionlint="$(command -v actionlint)"
elif command -v go >/dev/null 2>&1; then
  # GitHub Release assets can return 403 from shared runner IPs. The Go module
  # proxy is more reliable and still verifies the pinned module checksum.
  GOBIN="$maeve_lint_dir" go install github.com/rhysd/actionlint/cmd/actionlint@v1.7.12
  maeve_actionlint="$maeve_lint_dir/actionlint"
else
  curl --fail --silent --show-error --location --retry 3 --retry-all-errors \
    https://github.com/rhysd/actionlint/releases/download/v1.7.12/actionlint_1.7.12_linux_amd64.tar.gz \
    --output "$maeve_lint_dir/actionlint.tar.gz"
  printf '%s  %s\n' \
    8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8 \
    "$maeve_lint_dir/actionlint.tar.gz" | sha256sum --check
  tar --extract --gzip --file "$maeve_lint_dir/actionlint.tar.gz" \
    --directory "$maeve_lint_dir" actionlint
  maeve_actionlint="$maeve_lint_dir/actionlint"
fi

"$maeve_actionlint" "$maeve_repo"/.github/workflows/*.yml
python3 "$maeve_repo/tools/ci/check_pr_gate.py"
