#!/usr/bin/env bash
# Validate Actions syntax locally or on CI with the same pinned validator.
set -euo pipefail

maeve_repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
maeve_lint_dir="$(mktemp -d)"
trap 'rm -rf -- "$maeve_lint_dir"' EXIT

curl --fail --silent --show-error --location \
  https://github.com/rhysd/actionlint/releases/download/v1.7.12/actionlint_1.7.12_linux_amd64.tar.gz \
  --output "$maeve_lint_dir/actionlint.tar.gz"
printf '%s  %s\n' \
  8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8 \
  "$maeve_lint_dir/actionlint.tar.gz" | sha256sum --check
tar --extract --gzip --file "$maeve_lint_dir/actionlint.tar.gz" \
  --directory "$maeve_lint_dir" actionlint

"$maeve_lint_dir/actionlint" "$maeve_repo"/.github/workflows/*.yml
