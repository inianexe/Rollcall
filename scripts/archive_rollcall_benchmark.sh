#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
qa_root="${ROLLCALL_QA_DIR:-/home/inian/Projects/Rollcall-performance-qa}"
stamp="$(date +%Y%m%d-%H%M%S)"
out="$qa_root/phase6b1r-$stamp"
source_dir="$repo_root/android/macrobenchmark/build/outputs/connected_android_test_additional_output/benchmark/connected"

if [[ ! -d "$source_dir" ]]; then
  echo "Macrobenchmark trace output not found: $source_dir" >&2
  exit 1
fi
mkdir -p "$out"
cp -a "$source_dir/." "$out/"
cp -a "$repo_root/android/macrobenchmark/build/outputs/androidTest-results/connected/benchmark/." "$out/test-results/"
find "$out" -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 sha256sum > "$out/SHA256SUMS"
printf 'Archived benchmark outputs: %s\n' "$out"
